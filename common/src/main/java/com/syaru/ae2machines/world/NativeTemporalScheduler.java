package com.syaru.ae2machines.world;

import com.syaru.ae2machines.runtime.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;

/** Advances only intervals whose entire owned world has no observable event. Unknowns stay native. */
public final class NativeTemporalScheduler {
    private static final Set<String> PARTS=Set.of("appeng.parts.p2p.MEP2PTunnelPart","com.syaru.ae2machines.platform.VirtualP2PPart",
        "appeng.parts.networking.QuartzFiberPart","appeng.parts.automation.ImportBusPart","appeng.parts.automation.ExportBusPart",
        "appeng.parts.storagebus.StorageBusPart","appeng.parts.misc.InterfacePart","appeng.parts.crafting.PatternProviderPart");
    private static final Set<String> PASSIVE=Set.of(
        "appeng.blockentity.networking.CableBusBlockEntity","appeng.blockentity.crafting.PatternProviderBlockEntity",
        "appeng.blockentity.networking.EnergyCellBlockEntity","appeng.blockentity.networking.CreativeEnergyCellBlockEntity",
        "appeng.blockentity.networking.ControllerBlockEntity","appeng.blockentity.networking.EnergyAcceptorBlockEntity",
        "appeng.blockentity.storage.DriveBlockEntity","com.syaru.ae2machines.platform.EnergyPortEntity");
    private final NativeWorldSession session;
    private final ServerLevel world;
    private boolean dirty=true,staticSafe;
    private List<BlockEntity> blocks=List.of();
    private String staticReason="UNDECLARED_BLOCK";
    private long skipped,spans;
    private String reason="NOT_RUN";
    public NativeTemporalScheduler(NativeWorldSession session){this.session=session;world=session.world();}
    public void invalidate(){dirty=true;}
    public record Statistics(long skippedTicks,long spans,String lastBoundary){}
    public Statistics statistics(){return new Statistics(skipped,spans,reason);}
    public static boolean passiveContract(BlockEntity be){
        if(!PASSIVE.contains(be.getClass().getName()))return false;
        if(be instanceof appeng.blockentity.networking.CableBusBlockEntity cable)for(var side:net.minecraft.core.Direction.values()){
            var part=cable.getPart(side);if(part!=null && !PARTS.contains(part.getClass().getName()))return false;
        }
        return true;
    }
    private int blocked(String value){reason=value;return 0;}
    public int advance(int offered){
        if(offered<2)return 0;
        if(NativeCircuitOptions.ENABLED && !dirty && !staticSafe)return blocked(staticReason);
        if(!session.isOnline())return blocked("OFFLINE");
        if(session.batches().requiresWorldTicks() || session.variable()!=null && session.variable().requiresWorldTicks())return blocked("OWNED_EXECUTOR");
        if(dirty){
            staticSafe=true;staticReason="UNDECLARED_BLOCK";var found=new ArrayList<BlockEntity>();var state=NativeWorldRuntime.get(world).state();
            for(int x=state.minX();x<=state.maxX();x++)for(int z=state.minZ();z<=state.maxZ();z++){
                var chunk=world.getChunkSource().getChunkNow(x,z);
                if(chunk==null || chunk.getBlockEntitiesPos().size()!=chunk.getBlockEntities().size()){staticSafe=false;staticReason="PENDING_CHUNK";continue;}
                // Palette inspection covers the entire ticking chunk, not only the captured box.
                for(var section:chunk.getSections())if(!section.hasOnlyAir() && section.getStates().maybeHas(s->!quietBlock(s)))
                    // Unused palette entries survive region moves; only live blocks can tick.
                    section.getStates().count((s,n)->{if(n>0 && !quietBlock(s)){staticSafe=false;staticReason="UNDECLARED_BLOCK:"+net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock());}});
                found.addAll(chunk.getBlockEntities().values());
            }
            blocks=List.copyOf(found);
            if(staticSafe)for(var be:blocks)if(!(be instanceof com.syaru.ae2machines.api.NativeTimeParticipant) && !PASSIVE.contains(be.getClass().getName())){
                staticSafe=false;staticReason="NO_NATIVE_TIME_CONTRACT";break;
            }
            dirty=false;
        }
        if(!staticSafe)return blocked(staticReason);
        if(!com.syaru.ae2machines.platform.TemporalLevelHooks.safe())return blocked("UNDECLARED_LEVEL_HOOK");
        if(world.getAllEntities().iterator().hasNext())return blocked("ENTITY");
        var weather=(com.syaru.ae2machines.mixin.NativeTemporalWeatherAccess)world;
        if(world.getLevelData().isRaining() || world.getLevelData().isThundering() || weather.machines$timeRain()!=0
                || weather.machines$timePreviousRain()!=0 || weather.machines$timeThunder()!=0 || weather.machines$timePreviousThunder()!=0)return blocked("WEATHER");
        if(NativeMachines.mek() && !com.syaru.ae2machines.platform.MekTimeClock.worldQuiet(world))return blocked("MEK_WORLD_EVENT");
        if(world.getBlockTicks().count()!=0 || world.getFluidTicks().count()!=0
                || !((com.syaru.ae2machines.mixin.NativeServerWorldAccess)world).ae2machines$blockEvents().isEmpty())return blocked("WORLD_EVENT");
        var calls=(com.syaru.ae2machines.mixin.NativeTemporalCallsAccess)appeng.hooks.ticking.TickHandler.instance();
        var queue=calls.machines$timeCalls().get(world);
        if(queue!=null && !queue.isEmpty() || calls.machines$timeCalculations().containsKey(world))return blocked("AE2_CALLABLE");
        long now=world.getGameTime();long next=NativeGridState.nextEvent(world);
        int count=next==Long.MAX_VALUE?offered:next<=now?0:(int)Math.min(offered,Math.max(0,next-now-1));
        if(count<2)return blocked("AE2_EVENT");
        var steps=new ArrayList<NativeQuietStep>();
        for(var be:blocks){
            if(be.isRemoved() || world.getBlockEntity(be.getBlockPos())!=be){dirty=true;return blocked("REMOVED_BLOCK_ENTITY");}
            if(PASSIVE.contains(be.getClass().getName())){
                if(be instanceof appeng.blockentity.AEBaseBlockEntity ae && ae.getQueuedForReady()!=ae.getReadyInvoked())return blocked("AE2_INITIALIZATION");
                if(be instanceof appeng.blockentity.networking.CableBusBlockEntity cable){
                    if(!NativeGridState.indexed(world,cable.getGridNode(null)))return blocked("AE2_UNINDEXED_NODE");
                    for(var side:net.minecraft.core.Direction.values()){
                        var part=cable.getPart(side);
                        if(part!=null && !PARTS.contains(part.getClass().getName()))return blocked("UNDECLARED_AE_PART");
                        if(!NativeGridState.indexed(world,cable.getGridNode(side)))return blocked("AE2_UNINDEXED_NODE");
                    }
                }else if(be instanceof appeng.me.helpers.IGridConnectedBlockEntity network && !NativeGridState.indexed(world,network.getMainNode().getNode()))return blocked("AE2_UNINDEXED_NODE");
                continue;
            }
            NativeQuietStep step;
            try{
                step=be instanceof com.syaru.ae2machines.api.NativeTimeMachine?session.generic().planTime(be,count):null;
                if(step==null && !(be instanceof com.syaru.ae2machines.api.NativeTimeMachine) && be instanceof com.syaru.ae2machines.api.NativeTimeParticipant participant){
                    var planned=participant.nativeTimeStep(count);
                    if(planned!=null){
                        if(planned.limit()>count)throw new IllegalArgumentException("NATIVE_TIME_INTERVAL_EXCEEDS_OFFER");
                        step=new NativeQuietStep(planned.limit(),planned.advance(),planned.rollback());
                    }
                }
            }
            catch(RuntimeException invalid){
                if(!session.generic().quarantined())throw invalid;
                org.slf4j.LoggerFactory.getLogger(NativeTemporalScheduler.class).error("Native time plan quarantined at {}",be.getBlockPos(),invalid);
                return blocked("NATIVE_TIME_CONTRACT_VIOLATION");
            }
            if(step==null)return blocked("NATIVE_TIME_BOUNDARY");
            count=Math.min(count,step.limit());steps.add(step);
            if(count<2)return blocked("MACHINE_EVENT");
        }
        if(steps.isEmpty())return blocked("NO_PROCESSOR");
        var state=NativeWorldRuntime.get(world).state();var before=state.save(new CompoundTag());
        // Reject time overflow and revoke a previous recovery witness before touching assets.
        Math.addExact(state.ticks(),count);if(world.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DAYLIGHT))Math.addExact(state.dayTime(),count);
        NativeWorldCheckpoint.beforeOwnedWork(world.getServer(),session.id());
        try{
            for(var step:steps)step.advance().accept(count);
            state.advance(count,world.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DAYLIGHT));
            state.consumeTickDebt(count);
            skipped+=count;spans++;reason="NATIVE_EVENT_BOUNDARY";return count;
        }catch(RuntimeException|Error failure){
            try{for(int i=steps.size()-1;i>=0;i--)steps.get(i).rollback().run();state.load(before);}
            catch(Throwable undo){failure.addSuppressed(undo);throw new ExternalHandoffs.PersistenceFailure(failure);}
            if(failure instanceof RuntimeException && !(failure instanceof ExternalHandoffs.PersistenceFailure) && session.generic().quarantined()){
                org.slf4j.LoggerFactory.getLogger(NativeTemporalScheduler.class).error("Native time update rolled back and quarantined",failure);
                return blocked("NATIVE_TIME_CONTRACT_VIOLATION");
            }
            throw failure;
        }
    }
    private static boolean quietBlock(net.minecraft.world.level.block.state.BlockState state){
        return !state.isRandomlyTicking() && state.getFluidState().isEmpty();
    }
}
