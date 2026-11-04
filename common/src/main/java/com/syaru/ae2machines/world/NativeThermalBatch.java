package com.syaru.ae2machines.world;

import com.syaru.ae2machines.compat.mek.*;
import com.syaru.ae2machines.mixin.NativeThermalLevelAccess;
import com.syaru.ae2machines.runtime.NativeWorldSession;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.*;

/** One native-order ticker schedule per quiet prefix, not one scan per virtual tick. */
final class NativeThermalBatch implements AutoCloseable {
    private static NativeThermalBatch denied(ServerLevel world,String why){NativeWorldRuntime.get(world).thermalBoundary(why);return null;}
    private final ServerLevel world;
    private final List<TickingBlockEntity> source,active;
    private final List<MekThermalShells.Shell> shells;
    private final List<NativeConditionalTicker> conditional;
    private final List<TickingBlockEntity> schedule;
    private final List<NativeConditionalGroup> groups;
    private final List<NativeConditionalGroup.Slot> nativeSlots;
    private final long startedAt;
    private final long revision;
    private final int sourceSize;
    private final int limit;
    private int completed;
    private boolean closed;
    private Iterable<net.minecraft.server.level.ChunkHolder> chunkSource;
    private List<net.minecraft.server.level.ChunkHolder> chunkIndex;
    private long chunkEpoch=Long.MIN_VALUE;
    private int chunkCandidates;
    private NativeThermalBatch(ServerLevel world,List<TickingBlockEntity> source,List<TickingBlockEntity> active,List<MekThermalShells.Shell> shells,int limit){
        this.world=world;this.source=source;this.active=active;this.shells=shells;this.limit=limit;sourceSize=source.size();revision=NativeWorldRuntime.get(world).topologyRevision();
        conditional=active.stream().filter(NativeConditionalTicker.class::isInstance).map(NativeConditionalTicker.class::cast).toList();
        startedAt=world.getGameTime();var byOwner=new IdentityHashMap<Object,NativeConditionalGroup>();nativeSlots=new ArrayList<>();
        for(int i=0;i<active.size();i++){
            var ticker=active.get(i);var slot=new NativeConditionalGroup.Slot(i,ticker);
            if(ticker instanceof NativeConditionalTicker member){var shell=member.shell();byOwner.computeIfAbsent(shell.group(),ignored->new NativeConditionalGroup(shell.driver())).members.add(slot);}
            else nativeSlots.add(slot);
        }
        groups=List.copyOf(byOwner.values());groups.forEach(NativeConditionalGroup::refresh);
        schedule=new AbstractList<>(){
            @Override public TickingBlockEntity get(int index){return active.get(index);}
            @Override public int size(){return active.size();}
            @Override public Iterator<TickingBlockEntity> iterator(){return filtered();}
        };
    }
    private Iterator<TickingBlockEntity> filtered(){
        return new Iterator<>(){
            int cursor,lastPosition=-1,nextGroup;final int[] groupCursors=new int[groups.size()];
            NativeConditionalGroup.Slot next,last;boolean prepared;
            @Override public boolean hasNext(){
                if(prepared)return true;
                while(cursor<nativeSlots.size() && nativeSlots.get(cursor).removed)cursor++;
                next=cursor<nativeSlots.size()?nativeSlots.get(cursor):null;nextGroup=-1;
                boolean valid=!closed && NativeWorldRuntime.get(world).topologyRevision()==revision;
                for(int i=0;i<groups.size();i++){
                    var group=groups.get(i);group.observe(world.getGameTime(),valid);if(group.dormant)continue;
                    int p=groupCursors[i];
                    if(p<group.members.size() && group.members.get(p).position<=lastPosition)p=group.after(lastPosition);
                    while(p<group.members.size() && group.members.get(p).removed)p++;
                    groupCursors[i]=p;
                    if(p<group.members.size() && (next==null || group.members.get(p).position<next.position)){next=group.members.get(p);nextGroup=i;}
                }
                return prepared=next!=null;
            }
            @Override public TickingBlockEntity next(){if(!hasNext())throw new NoSuchElementException();prepared=false;last=next;lastPosition=next.position;if(nextGroup<0)cursor++;else groupCursors[nextGroup]++;return next.ticker;}
            @Override public void remove(){if(last==null)throw new IllegalStateException();last.removed=true;if(last.ticker instanceof NativeConditionalTicker member)member.removed();last=null;}
        };
    }
    private static boolean pendingCall(ServerLevel world){
        var calls=(com.syaru.ae2machines.mixin.NativeTemporalCallsAccess)appeng.hooks.ticking.TickHandler.instance();
        var queue=calls.machines$timeCalls().get(world);
        return queue!=null && !queue.isEmpty() || calls.machines$timeCalculations().containsKey(world);
    }
    Iterable<net.minecraft.server.level.ChunkHolder> chunks(Iterable<net.minecraft.server.level.ChunkHolder> original){
        var runtime=NativeWorldRuntime.get(world);
        if(closed || runtime.topologyRevision()!=revision)return original;
        long epoch=runtime.circuitChunkEpoch();
        if(original!=chunkSource || epoch!=chunkEpoch){
            chunkSource=original;chunkEpoch=epoch;chunkIndex=null;chunkCandidates=0;
            var found=new ArrayList<net.minecraft.server.level.ChunkHolder>();boolean ready=true;
            for(var holder:original){
                chunkCandidates++;
                if(holder.getTickingChunk()!=null)found.add(holder);
                else if(net.minecraft.server.level.ChunkLevel.isBlockTicking(holder.getTicketLevel()))ready=false;
            }
            // Include the native ticking halo too. Pending ticking futures retain the native scan.
            if(ready)chunkIndex=List.copyOf(found);
        }
        if(chunkIndex==null)return original;
        runtime.circuitChunkChecks(chunkCandidates-chunkIndex.size());return chunkIndex;
    }
    static NativeThermalBatch open(ServerLevel world,NativeWorldSession session,int count){
        if(!NativeThermalOptions.ENABLED || count<2 || session.generic().hasTargets() || session.batches().requiresWorldTicks()
                || session.variable()!=null && session.variable().requiresWorldTicks()
                || !com.syaru.ae2machines.platform.TemporalLevelHooks.safe() || !com.syaru.ae2machines.platform.MekTimeClock.worldQuiet(world)
                || world.getAllEntities().iterator().hasNext() || world.getBlockTicks().count()!=0 || world.getFluidTicks().count()!=0)return denied(world,"EXECUTOR_HOOK_OR_WORLD_EVENT");
        if(pendingCall(world))return denied(world,"AE2_CALLABLE");
        long now=world.getGameTime(),next=com.syaru.ae2machines.runtime.NativeGridState.nextEvent(world);
        if(next!=Long.MAX_VALUE)count=next<=now?0:(int)Math.min(count,Math.max(0,next-now-1));
        if(count<2)return denied(world,"AE2_EVENT");
        var access=(NativeThermalLevelAccess)world;if(!access.machines$thermalPending().isEmpty())return denied(world,"PENDING_TICKERS");
        if(world.isRaining() || world.isThundering() || !((com.syaru.ae2machines.mixin.NativeServerWorldAccess)world).ae2machines$blockEvents().isEmpty())return denied(world,"WEATHER_OR_BLOCK_EVENT");
        var region=NativeWorldRuntime.get(world).state();
        for(int x=region.minX();x<=region.maxX();x++)for(int z=region.minZ();z<=region.maxZ();z++){
            var chunk=world.getChunkSource().getChunkNow(x,z);if(chunk==null)return denied(world,"UNLOADED_CHUNK");
            for(var section:chunk.getSections())if(!section.hasOnlyAir() && section.getStates().maybeHas(s->s.isRandomlyTicking() || !s.getFluidState().isEmpty()))return denied(world,"RANDOM_OR_FLUID_BLOCK");
        }
        var blocks=session.nativeBlocks();boolean thermal=false;
        var validated=Collections.newSetFromMap(new IdentityHashMap<mekanism.common.lib.multiblock.MultiblockData,Boolean>());
        for(var be:blocks){
            if(com.syaru.ae2machines.world.NativeTemporalScheduler.passiveContract(be))continue;
            if(!MekThermalShells.known(be))return denied(world,"UNDECLARED_BE:"+be.getClass().getName());
            if(!MekThermalHooks.safe(be.getClass()))return denied(world,"UNDECLARED_MIXIN:"+be.getClass().getName());
            if(be instanceof mekanism.common.lib.multiblock.IMultiblock<?> tile){
                var d=tile.getMultiblock();if(validated.add(d) && !MekThermalShells.bounded(d,count))return denied(world,"UNSTABLE_DATA:"+d.getClass().getName());thermal=true;
            }
        }
        if(!thermal)return denied(world,"NO_THERMAL_DATA");
        var original=access.machines$thermalTickers();var active=new ArrayList<TickingBlockEntity>();var shells=new ArrayList<MekThermalShells.Shell>();
        var heat=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());
        for(var ticker:original){
            if(ticker.isRemoved())return denied(world,"REMOVED_TICKER");
            if(!NativeRegionMove.contains(ticker.getPos(),session.previewFirst(),session.previewLast()))return denied(world,"TICKER_OUTSIDE_CELL");
            var be=world.getBlockEntity(ticker.getPos());
            var shell=be==null || !access.machines$thermalTicking(ticker.getPos())?null:MekThermalShells.plan(be,count);
            // Only the declared native masters produce pending heat in this contract.
            // Material pipes, activation-only elements and unconnected vents cannot.
            // Keep the first native heat barrier for each account after its master.
            if(shell!=null && (shell.heat().isEmpty() || heat.containsAll(shell.heat()))){
                if(shell.quiet()==null)shells.add(shell);else active.add(new NativeConditionalTicker(ticker,shell));
            }
            else{
                active.add(ticker);
                if(be instanceof mekanism.common.lib.multiblock.IMultiblock<?> tile && tile.isMaster())
                    heat.removeAll(tile.getMultiblock().getHeatCapacitors(null));
                if(shell!=null)heat.addAll(shell.heat());
            }
        }
        if(shells.isEmpty() && active.stream().noneMatch(NativeConditionalTicker.class::isInstance))return denied(world,"NO_QUIET_SHELLS");
        NativeWorldRuntime.get(world).thermalBoundary("FOLDED:"+shells.size()+"/"+original.size());
        NativeWorldCheckpoint.beforeOwnedWork(world.getServer(),session.id());
        return new NativeThermalBatch(world,original,active,shells,count);
    }
    List<TickingBlockEntity> tickers(List<TickingBlockEntity> original){
        if(closed)return original;
        if(completed>=limit || pendingCall(world) || original!=source || original.size()!=sourceSize || NativeWorldRuntime.get(world).topologyRevision()!=revision
                || !com.syaru.ae2machines.platform.TemporalLevelHooks.safe()){close();return original;}
        return conditional.isEmpty()?active:schedule;
    }
    void tickEnd(){if(!closed){completed++;for(var group:groups)if(!group.dormant)group.refresh();}}
    boolean finished(){return closed || completed>=limit;}
    @Override public void close(){
        if(closed)return;closed=true;
        if(completed>0){for(var shell:shells)shell.clock().advance().accept(completed);}
        long skipped=(long)completed*shells.size();for(var item:conditional){item.flush(startedAt+completed);skipped+=item.skipped();}
        NativeWorldRuntime.get(world).thermalSkipped(skipped);
    }
}
