package com.syaru.ae2machines.world;

import com.syaru.ae2machines.compat.mek.MekThermalHooks;
import com.syaru.ae2machines.mixin.*;
import com.syaru.ae2machines.platform.*;
import com.syaru.ae2machines.runtime.NativeGridState;
import java.util.function.BooleanSupplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.InactiveProfiler;

/** Only the empty-event, playerless thermal case. Vanilla retains the tail if chunk work wakes anything. */
final class ThermalCellPulse implements ThermalFrameWindow.Pulse {
    private final NativeWorldRuntime runtime;
    private final ServerLevel world;
    private final NativeThermalBatch batch;
    private final ThermalCellAccess clock;
    private final NativeServerWorldAccess events;
    private final BooleanSupplier time;
    ThermalCellPulse(NativeWorldRuntime runtime,ServerLevel world,NativeThermalBatch batch,BooleanSupplier time){
        this.runtime=runtime;this.world=world;this.batch=batch;this.time=time;
        clock=(ThermalCellAccess)world;events=(NativeServerWorldAccess)world;
    }
    static boolean supported(ServerLevel world){
        return world.getClass()==ServerLevel.class && !world.isDebug() && world.getDragonFight()==null
                && world.getProfiler()==InactiveProfiler.INSTANCE && MekThermalHooks.safe(ServerLevel.class)
                && MekThermalHooks.safe(net.minecraft.world.ticks.LevelTicks.class);
    }
    private boolean clear(){
        return batch.witnessed() && world.players().isEmpty() && !world.getAllEntities().iterator().hasNext()
                && events.ae2machines$blockEvents().isEmpty() && !world.isRaining() && !world.isThundering()
                && NativeCellPlatform.runsNormally(world) && TemporalLevelHooks.safe() && MekTimeClock.worldQuiet(world);
    }
    public boolean clearSuffix(){
        if(clear())return true;
        runtime.releaseThermalSchedule();return false;
    }
    public void finishMachines(){
        clock.machines$runBlockEvents();
        clock.machines$handling(false);
        clock.machines$emptyTime(ThermalForcedChunks.present(world)?0:1);
        ((ThermalCellMachines)world).machines$runOriginalMachines();
        events.ae2machines$entities().tick();
    }
    public boolean clearNext(){
        long now=world.getGameTime(),next=NativeGridState.nextEvent(world);
        if(!clear() || now==Long.MAX_VALUE || next!=Long.MAX_VALUE && next<=now+1
                || NativeTickHorizon.due(world.getBlockTicks(),now+1) || NativeTickHorizon.due(world.getFluidTicks(),now+1)){
            runtime.releaseThermalSchedule();return false;
        }
        return true;
    }
    public void postAndDebit(){NativeCellPlatform.afterTick(world,time);runtime.state().consumeTickDebt();}
    public boolean running(){return runtime.running();}
    public void pre(){NativeCellPlatform.beforeTick(world,time);}
    public void nativeBody(){runtime.releaseThermalSchedule();runtime.nativeThermalBoundary(time);}
    public void ambientPrefix(){
        // The clock, chunk tickets, shuffle, precipitation RNG, loaded IO and raids remain native.
        // Neither an estimated heat delta nor a second copy of the entity loop belongs here.
        NativeWorldCheckpoint.beforeOwnedWork(world.getServer(),runtime.state().owner());
        world.resetEmptyTime();
        clock.machines$handling(true);
        world.getWorldBorder().tick();clock.machines$advanceWeather();world.updateSkyBrightness();
        clock.machines$advanceTime();
        // Border/weather listeners can schedule new work after PRE.
        long now=world.getGameTime();
        if(NativeTickHorizon.due(world.getBlockTicks(),now)){
            runtime.releaseThermalSchedule();world.getBlockTicks().tick(now,65536,clock::machines$tickBlock);
        }
        if(NativeTickHorizon.due(world.getFluidTicks(),now)){
            runtime.releaseThermalSchedule();world.getFluidTicks().tick(now,65536,clock::machines$tickFluid);
        }
        world.getRaids().tick();world.getChunkSource().tick(time,true);
    }
}
