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
    private final NativeWorldRuntime runtime;
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
    private boolean processingOnly;
    private NativeThermalBatch(ServerLevel world,List<TickingBlockEntity> source,List<TickingBlockEntity> active,List<MekThermalShells.Shell> shells,int limit){
        this.world=world;runtime=NativeWorldRuntime.get(world);this.source=source;this.active=active;this.shells=shells;this.limit=limit;sourceSize=source.size();revision=runtime.topologyRevision();
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
                boolean valid=!closed && runtime.topologyRevision()==revision;
                long now=world.getGameTime();
                for(int i=0;i<groups.size();i++){
                    var group=groups.get(i);group.observe(now,valid);if(group.dormant)continue;
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
    static NativeThermalBatch open(ServerLevel world,NativeWorldSession session,int count){
        if(!NativeThermalOptions.ENABLED || count<2)return denied(world,"DISABLED_OR_SHORT");
        if(session.generic().hasTargets() || session.batches().requiresWorldTicks() || session.variable()!=null && session.variable().requiresWorldTicks())return denied(world,"OWNED_EXECUTOR");
        if(!com.syaru.ae2machines.platform.TemporalLevelHooks.safe())return denied(world,"UNDECLARED_LEVEL_HOOK");
        if(!com.syaru.ae2machines.platform.MekTimeClock.worldQuiet(world))return denied(world,"MEK_WORLD_EVENT");
        if(world.getAllEntities().iterator().hasNext())return denied(world,"ENTITY");
        count=NativeTickHorizon.world(world,count);
        if(count<2)return denied(world,"SCHEDULED_WORLD_EVENT");
        if(pendingCall(world))return denied(world,"AE2_CALLABLE");
        long now=world.getGameTime(),next=com.syaru.ae2machines.runtime.NativeGridState.nextEvent(world);
        if(next!=Long.MAX_VALUE)count=NativeTickHorizon.before(now,count,next);
        if(count<2)return denied(world,"AE2_EVENT");
        var access=(NativeThermalLevelAccess)world;if(!access.machines$thermalPending().isEmpty())return denied(world,"PENDING_TICKERS");
        if(world.isRaining() || world.isThundering() || !((com.syaru.ae2machines.mixin.NativeServerWorldAccess)world).ae2machines$blockEvents().isEmpty())return denied(world,"WEATHER_OR_BLOCK_EVENT");
        var region=NativeWorldRuntime.get(world).state();
        for(int x=region.minX();x<=region.maxX();x++)for(int z=region.minZ();z<=region.maxZ();z++){
            var chunk=world.getChunkSource().getChunkNow(x,z);if(chunk==null)return denied(world,"UNLOADED_CHUNK");
            // Fluid deadlines bound the interval above; the native level still dispatches them.
            // A standing water source alone does not invalidate a structural clock contract.
            for(var section:chunk.getSections())if(!section.hasOnlyAir() && section.getStates().maybeHas(s->s.isRandomlyTicking())){
                var random=new boolean[1];
                section.getStates().count((s,n)->{if(n>0 && s.isRandomlyTicking())random[0]=true;});
                if(random[0])return denied(world,"RANDOM_BLOCK");
            }
        }
        var runtime=NativeWorldRuntime.get(world);
        long revision=runtime.topologyRevision();
        var layout=runtime.thermalLayout(session);
        if(layout==null)return denied(world,"TOPOLOGY_CHANGED");
        var boundary=layout.boundary(count);
        if(boundary!=null)return denied(world,boundary);
        var original=access.machines$thermalTickers();var active=new ArrayList<TickingBlockEntity>();var shells=new ArrayList<MekThermalShells.Shell>();
        return build(world,session,count,layout,revision,original,false);
    }
    static NativeThermalBatch processing(ServerLevel world,NativeWorldSession session,int count,List<TickingBlockEntity> source){
        if(!NativeThermalOptions.ENABLED || count<2 || session.batches().active()
                || session.variable()!=null && session.variable().active())return null;
        var runtime=NativeWorldRuntime.get(world);long revision=runtime.topologyRevision();
        var layout=runtime.thermalLayout(session);if(layout==null)return null;
        var boundary=layout.boundary(count);if(boundary!=null)return denied(world,boundary);
        return build(world,session,count,layout,revision,source,true);
    }
    private static NativeThermalBatch build(ServerLevel world,NativeWorldSession session,int count,NativeThermalLayout layout,
            long revision,List<TickingBlockEntity> original,boolean processingOnly){
        var access=(NativeThermalLevelAccess)world;var runtime=NativeWorldRuntime.get(world);
        var active=new ArrayList<TickingBlockEntity>();var shells=new ArrayList<MekThermalShells.Shell>();
        var heat=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());
        for(var ticker:original){
            if(ticker.isRemoved())return denied(world,"REMOVED_TICKER");
            var pos=ticker.getPos();
            var be=layout.resident(pos);
            if(be==null || be.isRemoved()){
                if(!NativeRegionMove.contains(pos,layout.first,layout.last))return denied(world,"TICKER_OUTSIDE_CELL");
                // Pending/unindexed tickers still resolve through vanilla.
                be=world.getBlockEntity(pos);
                if(be!=null)return denied(world,"UNINDEXED_TICKER");
            }
            var shell=be==null || !access.machines$thermalTicking(pos)?null:MekThermalShells.plan(be,count);
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
        if(runtime.topologyRevision()!=revision || !access.machines$thermalPending().isEmpty())return denied(world,"TOPOLOGY_CHANGED");
        if(shells.isEmpty() && active.stream().noneMatch(NativeConditionalTicker.class::isInstance))return denied(world,"NO_QUIET_SHELLS");
        NativeWorldRuntime.get(world).thermalBoundary("FOLDED:"+shells.size()+"/"+original.size());
        NativeWorldCheckpoint.beforeOwnedWork(world.getServer(),session.id());
        var batch=new NativeThermalBatch(world,original,active,shells,count);batch.processingOnly=processingOnly;return batch;
    }
    List<TickingBlockEntity> tickers(List<TickingBlockEntity> original){
        if(closed)return original;
        if(completed>=limit || original!=source || original.size()!=sourceSize || runtime.topologyRevision()!=revision
                || !processingOnly && (pendingCall(world) || !com.syaru.ae2machines.platform.TemporalLevelHooks.safe())){close();return original;}
        return conditional.isEmpty()?active:schedule;
    }
    void tickEnd(){if(!closed){completed++;for(var group:groups)if(!group.dormant)group.refresh();}}
    boolean finished(){return closed || completed>=limit;}
    boolean witnessed(){
        return !finished() && source.size()==sourceSize && runtime.topologyRevision()==revision
                && !pendingCall(world) && ((NativeThermalLevelAccess)world).machines$thermalPending().isEmpty();
    }
    @Override public void close(){
        if(closed)return;closed=true;
        if(completed>0){for(var shell:shells)shell.advance(completed,startedAt+completed);}
        long skipped=(long)completed*shells.size();for(var item:conditional){item.flush(startedAt+completed);skipped+=item.skipped();}
        runtime.thermalSkipped(skipped);
    }
}
