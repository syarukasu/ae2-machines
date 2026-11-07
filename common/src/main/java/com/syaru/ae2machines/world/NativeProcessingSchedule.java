package com.syaru.ae2machines.world;

import com.syaru.ae2machines.api.NativeTimeMachine;
import com.syaru.ae2machines.api.NativeTimeParticipant;
import com.syaru.ae2machines.mixin.NativeThermalLevelAccess;
import com.syaru.ae2machines.runtime.*;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.*;

/** Reuses native ticker order, but never replays a level, AE grid or chunk-management tick. */
public final class NativeProcessingSchedule {
    private record Member(TickingBlockEntity ticker,BlockEntity owner){}
    private final ServerLevel world;
    private final NativeWorldRuntime runtime;
    private List<TickingBlockEntity> source;
    private List<Member> members=List.of();
    private List<TickingBlockEntity> tickers=List.of();
    private long revision=Long.MIN_VALUE,builds,reuses,nativeCalls,aggregated,spans;
    private int sourceSize;
    private String boundary="NOT_RUN";
    private NativeThermalBatch thermal;
    private List<TickingBlockEntity> thermalSource=List.of();
    public NativeProcessingSchedule(ServerLevel world,NativeWorldRuntime runtime){
        this.world=world;this.runtime=runtime;
    }
    public record Statistics(long builds,long reuses,long nativeCalls,long aggregatedTicks,long spans,String boundary){}
    public Statistics statistics(){return new Statistics(builds,reuses,nativeCalls,aggregated,spans,boundary);}
    public static boolean processing(BlockEntity block){
        return block!=null && !(block instanceof appeng.blockentity.AEBaseBlockEntity)
                && !(block instanceof appeng.api.parts.IPartHost);
    }
    private NativeWorldSession session(){
        var host=runtime.energyHost();
        return host!=null && host.factoryHandle() instanceof NativeWorldSession s?s:null;
    }
    private void refresh(){
        var access=(NativeThermalLevelAccess)world;
        var current=access.machines$thermalTickers();
        if(source==current && sourceSize==current.size() && revision==runtime.equipmentRevision()){
            reuses++;return;
        }
        var found=new ArrayList<Member>();
        for(var ticker:current){
            if(ticker.isRemoved())continue;
            var owner=world.getBlockEntity(ticker.getPos());
            if(processing(owner))found.add(new Member(ticker,owner));
        }
        source=current;sourceSize=current.size();revision=runtime.equipmentRevision();
        members=List.copyOf(found);tickers=members.stream().map(Member::ticker).toList();builds++;
    }
    private boolean live(Member member){
        return !member.ticker().isRemoved() && !member.owner().isRemoved()
                && ((NativeThermalLevelAccess)world).machines$thermalTicking(member.ticker().getPos());
    }
    private boolean reserved(Member member){
        return runtime.batches()!=null && runtime.batches().busy(member.owner().getBlockPos());
    }
    public void beginInterval(int count){
        refresh();
        var session=session();
        if(session!=null && NativeMachines.mek()){
            thermalSource=tickers;
            thermal=NativeThermalBatch.processing(world,session,count,thermalSource);
        }
    }
    public void endInterval(){
        if(thermal!=null)try{thermal.close();}finally{thermal=null;thermalSource=List.of();}
    }
    public void tickNative(){
        refresh();
        if(thermal!=null){
            for(var ticker:thermal.tickers(thermalSource))if(!ticker.isRemoved()
                    && (runtime.batches()==null || !runtime.batches().busy(ticker.getPos()))
                    && ((NativeThermalLevelAccess)world).machines$thermalTicking(ticker.getPos())){
                ticker.tick();nativeCalls++;
            }
            thermal.tickEnd();
            if(thermal.finished())endInterval();
            return;
        }
        for(var member:members)if(live(member) && !reserved(member)){
            member.ticker().tick();nativeCalls++;
        }
    }
    /** A shared interval ends at the first processing/transport event, not an AE scheduling event. */
    public int advance(int offered){
        if(offered<2)return 0;
        if(thermal!=null)return 0;
        refresh();
        var session=session();if(session==null)return 0;
        if(runtime.variable()!=null && runtime.variable().requiresWorldTicks()){
            boundary="VARIABLE_PROCESSING";return 0;
        }
        if(NativeMachines.mek() && com.syaru.ae2machines.compat.mek.MekNativeNetworks.hasNetworks(world)){
            boundary="NATIVE_TRANSPORT";return 0;
        }
        var steps=new ArrayList<NativeQuietStep>();
        var local=new ArrayList<BlockEntity>();
        int count=offered;
        for(var member:members){
            if(!live(member) || reserved(member))continue;
            var be=member.owner();
            if(be instanceof NativeTimeMachine && NativeParallelRuntime.owns(be)){local.add(be);continue;}
            if(!(be instanceof NativeTimeParticipant participant)){boundary="NATIVE_PROCESSING_EVENT";return 0;}
            var step=participant.nativeTimeStep(count);
            if(step==null || step.limit()<2){boundary="NATIVE_PROCESSING_EVENT";return 0;}
            if(step.limit()>count)throw new IllegalArgumentException("NATIVE_TIME_INTERVAL_EXCEEDS_OFFER");
            count=Math.min(count,step.limit());steps.add(new NativeQuietStep(step.limit(),step.advance(),step.rollback()));
        }
        if(!local.isEmpty()){
            NativeQuietStep step;
            try{step=session.generic().planTimes(local,count);}
            catch(RuntimeException failure){
                if(!isolatedFailure(session,failure))throw failure;
                return 0;
            }
            if(step==null || step.limit()<2){boundary="NATIVE_PROCESSING_EVENT";return 0;}
            count=Math.min(count,step.limit());steps.add(step);
        }
        long beforeRevision=runtime.equipmentRevision();
        Math.addExact(runtime.state().processingTicks(),count);
        NativeWorldCheckpoint.beforeOwnedWork(world.getServer(),session.id());
        int applied=0;
        try{
            for(var step:steps){
                applied++;
                step.advance().accept(count);
                if(beforeRevision!=runtime.equipmentRevision())throw new NativeQuietStep.StateChanged("PROCESSING_TOPOLOGY_CHANGED");
            }
        }catch(RuntimeException|Error failure){
            try{for(int i=applied-1;i>=0;i--)steps.get(i).rollback().run();}
            catch(Throwable undo){failure.addSuppressed(undo);throw new ExternalHandoffs.PersistenceFailure(failure);}
            if(failure instanceof NativeQuietStep.StateChanged){boundary="PROCESSING_STATE_CHANGED";return 0;}
            if(failure instanceof RuntimeException invalid && isolatedFailure(session,invalid))return 0;
            throw failure;
        }
        runtime.state().advanceProcessing(count);
        runtime.state().consumeTickDebt(count);
        aggregated+=count;spans++;boundary=steps.isEmpty()?"NO_UNOWNED_PROCESSING":"NATIVE_PROCESSING_INTERVAL";
        return count;
    }
    private boolean isolatedFailure(NativeWorldSession session,RuntimeException failure){
        if(!session.generic().quarantined())return false;
        boundary="NATIVE_TIME_CONTRACT_VIOLATION";
        org.slf4j.LoggerFactory.getLogger(NativeProcessingSchedule.class).error("Native processing state restored and cell quarantined",failure);
        return true;
    }
}
