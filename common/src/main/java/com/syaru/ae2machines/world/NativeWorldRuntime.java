package com.syaru.ae2machines.world;

import com.syaru.ae2machines.platform.NativeWorldData;
import com.syaru.ae2machines.platform.PlatformData;
import java.util.Comparator;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/** Native whole-level clock; each cell owns its own level and uncompleted tick debt. */
public final class NativeWorldRuntime {
    // T9 executes 512 native ticks including the server's ordinary level tick.
    private static final java.math.BigInteger EXTRA_TICK_WINDOW=java.math.BigInteger.valueOf(511);
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION,
            PlatformData.id("ae2machines:native_factory"));
    private static final TicketType<ChunkPos> TICKET = TicketType.create("ae2machines_native_world",
            Comparator.comparingLong(ChunkPos::toLong));
    private final ServerLevel level;
    private final NativeWorldState state;
    private Lease active;
    private java.math.BigInteger speed=java.math.BigInteger.ONE;
    private boolean stepping;
    private NativeTemporalScheduler temporal;
    private final java.util.Map<net.minecraft.core.BlockPos,NativeFurnaceTime.Frame> furnaceFrames=new java.util.HashMap<>();
    private long topologyRevision;
    private NativeThermalBatch thermalBatch;
    private long thermalSkipped;
    private long circuitChunkEpoch,circuitChunkChecks;
    private final NativeChunkSchedule chunkSchedule=new NativeChunkSchedule();
    public void circuitChunkChanged(){circuitChunkEpoch++;}
    long circuitChunkEpoch(){return circuitChunkEpoch;}
    int hostTick(){return level.getServer().getTickCount();}
    public long circuitChunkChecks(){return circuitChunkChecks;}
    void circuitChunkChecks(long count){circuitChunkChecks+=count;}
    public Iterable<net.minecraft.server.level.ChunkHolder> circuitChunks(Iterable<net.minecraft.server.level.ChunkHolder> original){
        return !NativeCircuitOptions.ENABLED || !running() || !NativeCellWorlds.owns(level)?original:chunkSchedule.select(this,original);
    }
    private String thermalBoundary="NOT_RUN";
    public String thermalBoundary(){return thermalBoundary;}
    void thermalBoundary(String value){thermalBoundary=value;}
    public long thermalSkipped(){return thermalSkipped;}
    void thermalSkipped(long count){thermalSkipped+=count;}
    public java.util.List<net.minecraft.world.level.block.entity.TickingBlockEntity> thermalTickers(java.util.List<net.minecraft.world.level.block.entity.TickingBlockEntity> original){return thermalBatch==null?original:thermalBatch.tickers(original);}
    public void thermalTickEnd(){if(thermalBatch!=null)thermalBatch.tickEnd();}
    public long topologyRevision(){return topologyRevision;}
    public void temporal(NativeTemporalScheduler value){checkThread();temporal=value;}
    public void invalidateTemporal(){topologyRevision++;if(temporal!=null)temporal.invalidate();}
    void observeFurnace(NativeFurnaceTime.Frame frame){furnaceFrames.put(frame.owner().getBlockPos(),frame);}
    NativeFurnaceTime.Frame furnaceFrame(net.minecraft.core.BlockPos pos){return furnaceFrames.get(pos);}
    public NativeTemporalScheduler.Statistics temporalStatistics(){return temporal==null?new NativeTemporalScheduler.Statistics(0,0,"NO_SESSION"):temporal.statistics();}
    private com.syaru.ae2machines.block.BaseAccelerator energyHost;
    private Runnable beforeExternalPower=()->{};
    private com.syaru.ae2machines.runtime.NativeWorldBatches batches;
    private com.syaru.ae2machines.runtime.MekNativeRequests variable;
    private com.syaru.ae2machines.runtime.NativeParallelRuntime generic;
    public com.syaru.ae2machines.runtime.NativeParallelRuntime generic(){return generic;}
    public void generic(com.syaru.ae2machines.runtime.NativeParallelRuntime value){checkThread();generic=value;}
    public com.syaru.ae2machines.runtime.MekNativeRequests variable(){return variable;}
    public void variable(com.syaru.ae2machines.runtime.MekNativeRequests value){checkThread();variable=value;}
    public com.syaru.ae2machines.runtime.NativeWorldBatches batches(){return batches;}
    public void batches(com.syaru.ae2machines.runtime.NativeWorldBatches value){checkThread();batches=value;}

    public NativeWorldRuntime(ServerLevel level) {
        this.level = level;
        this.state = NativeWorldData.get(level);
    }

    public static boolean owns(Level level) { return level.dimension().equals(DIMENSION) || NativeCellWorlds.owns(level); }
    public static NativeWorldRuntime get(ServerLevel level) {
        if (!owns(level)) throw new IllegalArgumentException("NOT_NATIVE_FACTORY_WORLD");
        return ((NativeWorldAccess) level).ae2machines$nativeWorld();
    }
    public NativeWorldState state() { return state; }
    public boolean running() { return active != null && executionReady(); }
    public void energyHost(com.syaru.ae2machines.block.BaseAccelerator host){checkThread();energyHost=host;}
    public com.syaru.ae2machines.block.BaseAccelerator energyHost(){return running()?energyHost:null;}
    public void beforeExternalPower(Runnable callback){checkThread();beforeExternalPower=java.util.Objects.requireNonNull(callback);}
    public void externalPower(){checkThread();beforeExternalPower.run();}
    public void retain(){checkThread();holdChunks();}
    public void release() {
        checkThread();if(active!=null)throw new IllegalStateException("NATIVE_WORLD_STILL_RUNNING");
        removeTickets();
        for(int x=state.minX();x<=state.maxX();x++)for(int z=state.minZ();z<=state.maxZ();z++) {
            var pos=new ChunkPos(x,z);level.getChunkSource().removeRegionTicket(TICKET,pos,2,pos,false);
        }
    }
    public void speed(java.math.BigInteger value){checkThread();if(value.signum()<=0)throw new IllegalArgumentException("Nonpositive world speed");speed=value;}
    public void afterTick(java.util.function.BooleanSupplier time) {
        if(!running() || !NativeCellWorlds.owns(level) || !com.syaru.ae2machines.platform.NativeCellPlatform.runsNormally(level))return;
        // Native registry transport follows the level's machines and AE2 level-end services.
        // Nested accelerated level ticks enter here too, but must not schedule more ticks.
        var owned=variable;
        boolean outer=!stepping;
        if(outer && owned!=null)owned.beginTickBundle();
        try {
            if(owned!=null)owned.tick();
            if(com.syaru.ae2machines.runtime.NativeMachines.mek())com.syaru.ae2machines.compat.mek.MekNativeNetworks.tick(level);
            if(!outer)return;
            state.addTickDebt(speed.subtract(java.math.BigInteger.ONE));
            int work=state.tickDebt().min(EXTRA_TICK_WINDOW).intValueExact();
            stepping=true;
            boolean retryThermal=true;
            for(int i=0;i<work && running();i++) {
                if(retryThermal && thermalBatch==null && work-i>=2 && energyHost!=null && energyHost.factoryHandle() instanceof com.syaru.ae2machines.runtime.NativeWorldSession session
                        && com.syaru.ae2machines.runtime.NativeMachines.mek()){
                    thermalBatch=NativeThermalBatch.open(level,session,work-i);
                    retryThermal=thermalBatch!=null || thermalBoundary.equals("AE2_EVENT") || thermalBoundary.equals("AE2_CALLABLE");
                }
                int skipped=temporal==null?0:temporal.advance(work-i);
                if(skipped>0){i+=skipped-1;continue;}
                if(!running())break;
                com.syaru.ae2machines.platform.NativeCellPlatform.beforeTick(level,time);
                level.tick(time);
                com.syaru.ae2machines.platform.NativeCellPlatform.afterTick(level,time);
                state.consumeTickDebt();
                if(thermalBatch!=null && thermalBatch.finished()){thermalBatch.close();thermalBatch=null;}
            }
        } finally {
            if(outer){try{if(thermalBatch!=null)thermalBatch.close();}finally{thermalBatch=null;stepping=false;if(owned!=null)owned.endTickBundle();}}
        }
    }
    private boolean executionReady(){
        if(energyHost==null)return !NativeCellWorlds.owns(level);
        if(energyHost.isRemoved() || !energyHost.settingsFit()
                || !com.syaru.ae2machines.block.ComputationStructure.runnable(energyHost))return false;
        return energyHost.factoryOnline();
    }

    // A live lease is deliberately never serialized: loading the save alone must not start a factory.
    public Lease start(UUID factory, ChunkPos first, ChunkPos last, long dayTime) {
        checkThread();
        if (active != null) throw new IllegalStateException("NATIVE_WORLD_ALREADY_RUNNING");
        state.initialRules(level.getServer().overworld().getGameRules());
        state.claim(factory, first.x, first.z, last.x, last.z, dayTime);
        var lease = new Lease();
        try {
            for (int x = state.minX(); x <= state.maxX(); x++) {
                for (int z = state.minZ(); z <= state.maxZ(); z++) {
                    var pos = new ChunkPos(x, z);
                    level.getChunkSource().removeRegionTicket(TICKET, pos, 2, pos, false);
                    level.getChunkSource().addRegionTicket(TICKET, pos, 2, pos, true);
                    ((com.syaru.ae2machines.mixin.NativeServerWorldAccess)level).ae2machines$entities()
                            .updateChunkStatus(pos,net.minecraft.world.level.entity.Visibility.TICKING);
                }
            }
            level.resetEmptyTime();
            active = lease;
            return lease;
        } catch (RuntimeException failure) {
            removeTickets();
            throw failure;
        }
    }

    private void checkThread() {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("NATIVE_WORLD_OFF_THREAD");
    }
    private void removeTickets() {
        for (int x = state.minX(); x <= state.maxX(); x++)
            for (int z = state.minZ(); z <= state.maxZ(); z++) {
                var pos = new ChunkPos(x, z);
                level.getChunkSource().removeRegionTicket(TICKET, pos, 2, pos, true);
            }
    }
    private void holdChunks() {
        // Suspension must not invalidate native multiblock caches before a coordinated save.
        // These transient tickets keep blocks resident, but do not force random/entity ticks.
        for (int x = state.minX(); x <= state.maxX(); x++)
            for (int z = state.minZ(); z <= state.maxZ(); z++) {
                var pos = new ChunkPos(x,z);
                level.getChunkSource().addRegionTicket(TICKET,pos,2,pos,false);
                ((com.syaru.ae2machines.mixin.NativeServerWorldAccess)level).ae2machines$entities()
                        .updateChunkStatus(pos,net.minecraft.world.level.entity.Visibility.TRACKED);
            }
    }
    public final class Lease implements AutoCloseable {
        private boolean closed;
        private Lease() {}
        @Override public void close() {
            checkThread();
            if (closed) return;
            if (active != this) throw new IllegalStateException("NATIVE_WORLD_LEASE_MISMATCH");
            if(variable!=null)variable.flush();
            active = null;
            removeTickets();
            holdChunks();
            closed = true;
        }
    }
}
