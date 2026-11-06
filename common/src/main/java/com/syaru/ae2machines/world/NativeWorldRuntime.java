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

/** One native world tick, with separately scheduled processing work inside the owned cell. */
public final class NativeWorldRuntime {
    // Bounded native API window, including legacy S=512 saves; not a wall-time tick budget.
    private static final java.math.BigInteger EXTRA_TICK_WINDOW=java.math.BigInteger.valueOf(511);
    private static final boolean THERMAL_WINDOW=!Boolean.getBoolean("ae2machines.disableThermalWindow");
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
    private final NativeProcessingSchedule processing;
    public NativeProcessingSchedule.Statistics processingStatistics(){return processing.statistics();}
    private final java.util.Map<net.minecraft.core.BlockPos,NativeFurnaceTime.Frame> furnaceFrames=new java.util.HashMap<>();
    private long topologyRevision;
    private long equipmentRevision;
    private NativeThermalBatch thermalBatch;
    private NativeThermalLayout thermalLayout;
    private long layoutBuilds,layoutHits;
    public long thermalLayoutBuilds(){return layoutBuilds;}
    public long thermalLayoutHits(){return layoutHits;}
    NativeThermalLayout thermalLayout(com.syaru.ae2machines.runtime.NativeWorldSession session){
        if(thermalLayout!=null && thermalLayout.session==session){layoutHits++;return thermalLayout;}
        long revision=equipmentRevision;
        var layout=new NativeThermalLayout(session);
        // A callback during discovery must not install a stale layout.
        if(revision!=equipmentRevision)return null;
        layoutBuilds++;return thermalLayout=layout;
    }
    private long thermalSkipped;
    private long subtickCompletions,subtickTicks;
    public long subtickCompletions(){return subtickCompletions;}
    public long subtickTicks(){return subtickTicks;}
    public void subtickWork(long completions,int ticks){subtickCompletions+=completions;subtickTicks+=ticks;}
    private net.minecraft.server.level.ChunkMap pendingAutosave;
    private java.util.function.BooleanSupplier autosaveTime;
    private int pendingSaveLimit;
    private long combinedSaveScans,saveFlushes;
    public long combinedSaveScans(){return combinedSaveScans;}
    public long saveFlushes(){return saveFlushes;}
    public boolean combineThermalAutosave(net.minecraft.server.level.ChunkMap map,java.util.function.BooleanSupplier time){
        if(Boolean.getBoolean("ae2machines.disableThermalAutosave") || thermalWindow==null || thermalBatch==null
                || !thermalBatch.witnessed() || pendingAutosave!=null && pendingAutosave!=map)return false;
        // beforeOwnedWork revoked the recovery witness before this interval. Durable checkpoints
        // and explicit saves are untouched; an interrupted uncommitted interval remains fail-closed.
        pendingAutosave=map;autosaveTime=time;pendingSaveLimit=Math.addExact(pendingSaveLimit,20);
        combinedSaveScans++;return true;
    }
    private void flushThermalAutosave(){
        if(pendingAutosave==null)return;
        var access=(com.syaru.ae2machines.mixin.NativeCircuitChunkMapAccess)pendingAutosave;
        var time=autosaveTime;int limit=pendingSaveLimit;
        pendingAutosave=null;autosaveTime=null;pendingSaveLimit=0;saveFlushes++;
        // Preserve native order, cooldowns, POI flushing and the sum of native save budgets.
        var holders=access.machines$visibleChunkIdentity().values().iterator();int saved=0;
        while(saved<limit && time.getAsBoolean() && holders.hasNext())if(access.machines$saveNativeChunk(holders.next()))saved++;
    }
    private long circuitChunkEpoch,circuitChunkChecks;
    private final NativeChunkSchedule<net.minecraft.server.level.ChunkHolder> chunkSchedule=new NativeChunkSchedule<>(
            holder->holder.getClass()==net.minecraft.server.level.ChunkHolder.class && holder.getTickingChunk()!=null,
            holder->holder.getClass()!=net.minecraft.server.level.ChunkHolder.class || net.minecraft.server.level.ChunkLevel.isBlockTicking(holder.getTicketLevel()));
    private final com.syaru.ae2machines.engine.IntervalSnapshot<Object> chunkWork=new com.syaru.ae2machines.engine.IntervalSnapshot<>();
    private boolean pooledChunks;
    private Object pooledSource;
    private Iterable<net.minecraft.server.level.ChunkHolder> pooledSelection;
    private long fullWorldCalls;
    private ThermalFrameWindow thermalWindow;
    private long compactThermalSteps;
    private long emptyTicketSweeps;
    public long compactThermalSteps(){return compactThermalSteps;}
    public long emptyTicketSweeps(){return emptyTicketSweeps;}
    public boolean skipEmptyTicketSweep(ThermalTicketChecks manager){
        var w=thermalWindow;
        if(w==null || !manager.machines$skipEmptySweep(w,w.remaining()))return false;
        emptyTicketSweeps++;return true;
    }
    public boolean foldThermalBoundary(java.util.function.BooleanSupplier time){
        var window=thermalWindow;
        if(window==null || window.entered() || thermalBatch==null || !ThermalCellPulse.supported(level))return false;
        return window.fold(new ThermalCellPulse(this,level,thermalBatch,time));
    }
    void nativeThermalBoundary(java.util.function.BooleanSupplier time){level.tick(time);fullWorldCalls++;}
    void releaseThermalSchedule(){if(thermalBatch!=null){thermalBatch.close();thermalBatch=null;}}
    /** Additional virtual work only; the host's ordinary native world tick is not included. */
    public record WorldWorkStatistics(long fullWorldCalls,long chunkSchedules,long chunkReuses){}
    public WorldWorkStatistics worldWorkStatistics(){return new WorldWorkStatistics(fullWorldCalls,chunkWork.builds(),chunkWork.reuses());}
    public boolean intervalChunkPlanning(){return stepping && NativeCircuitOptions.ENABLED;}
    @SuppressWarnings("unchecked")
    public <T> java.util.ArrayList<T> intervalChunkList(int capacity,Object sourceIdentity,Iterable<net.minecraft.server.level.ChunkHolder> source){
        pooledChunks=false;pooledSource=null;pooledSelection=null;
        if(!intervalChunkPlanning())return new java.util.ArrayList<>(capacity);
        var selected=selectChunks(sourceIdentity,source);
        if(!chunkSchedule.stable(sourceIdentity,circuitChunkEpoch,topologyRevision,hostTick()))return new java.util.ArrayList<>(capacity);
        pooledChunks=true;pooledSource=sourceIdentity;pooledSelection=selected;
        return (java.util.ArrayList<T>)(java.util.ArrayList<?>)chunkWork.begin(sourceIdentity,chunkSchedule.generation(),chunkSchedule.tickingCount());
    }
    @SuppressWarnings("unchecked")
    public void intervalChunkOrder(java.util.List<?> nativeList){
        if(pooledChunks && intervalChunkPlanning())chunkWork.beforeNativeOrder((java.util.List<Object>)nativeList);
    }
    public void circuitChunkChanged(){circuitChunkEpoch++;}
    long circuitChunkEpoch(){return circuitChunkEpoch;}
    int hostTick(){return level.getServer().getTickCount();}
    public long circuitChunkChecks(){return circuitChunkChecks;}
    void circuitChunkChecks(long count){circuitChunkChecks+=count;}
    public Iterable<net.minecraft.server.level.ChunkHolder> circuitChunks(Iterable<net.minecraft.server.level.ChunkHolder> original){
        return circuitChunks(original,original);
    }
    public Iterable<net.minecraft.server.level.ChunkHolder> circuitChunks(Object sourceIdentity,Iterable<net.minecraft.server.level.ChunkHolder> original){
        if(pooledChunks && intervalChunkPlanning() && pooledSource==sourceIdentity)return chunkWork.replay()?java.util.List.of():pooledSelection;
        return !NativeCircuitOptions.ENABLED || !running() || !NativeCellWorlds.owns(level)?original:selectChunks(sourceIdentity,original);
    }
    private Iterable<net.minecraft.server.level.ChunkHolder> selectChunks(Object sourceIdentity,Iterable<net.minecraft.server.level.ChunkHolder> original){
        var selected=chunkSchedule.select(sourceIdentity,circuitChunkEpoch,topologyRevision,hostTick(),original);
        circuitChunkChecks(chunkSchedule.filteredCandidates());return selected;
    }
    private String thermalBoundary="NOT_RUN";
    public String thermalBoundary(){return thermalBoundary;}
    void thermalBoundary(String value){thermalBoundary=value;}
    public long thermalSkipped(){return thermalSkipped;}
    void thermalSkipped(long count){thermalSkipped+=count;}
    public java.util.List<net.minecraft.world.level.block.entity.TickingBlockEntity> thermalTickers(java.util.List<net.minecraft.world.level.block.entity.TickingBlockEntity> original){return thermalBatch==null?original:thermalBatch.tickers(original);}
    public void thermalTickEnd(){if(thermalBatch!=null)thermalBatch.tickEnd();}
    public long topologyRevision(){return topologyRevision;}
    public long equipmentRevision(){return equipmentRevision;}
    public void temporal(NativeTemporalScheduler value){checkThread();temporal=value;}
    public void invalidateTemporal(){topologyRevision++;if(temporal!=null)temporal.invalidate();}
    public void invalidateEquipment(){equipmentRevision++;thermalLayout=null;if(batches!=null)batches.invalidate();invalidateTemporal();}
    public void invalidateEquipment(net.minecraft.core.BlockPos pos){
        equipmentRevision++;thermalLayout=null;if(batches!=null)batches.invalidate(pos);invalidateTemporal();
    }
    public void blockChanged(net.minecraft.core.BlockPos pos){
        if(batches!=null)batches.invalidate(pos);invalidateTemporal();
    }
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
        this.processing = new NativeProcessingSchedule(level,this);
    }

    public static boolean owns(Level level) { return level.dimension().equals(DIMENSION) || NativeCellWorlds.owns(level); }
    public static NativeWorldRuntime get(ServerLevel level) {
        if (!owns(level)) throw new IllegalArgumentException("NOT_NATIVE_FACTORY_WORLD");
        return ((NativeWorldAccess) level).ae2machines$nativeWorld();
    }
    public NativeWorldState state() { return state; }
    public boolean running() { return active != null && executionReady(); }
    public void energyHost(com.syaru.ae2machines.block.BaseAccelerator host){checkThread();thermalLayout=null;energyHost=host;}
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
        if(stepping)return;
        var owned=variable;
        if(owned!=null)owned.beginTickBundle();
        try {
            state.processingClock(()->{
                processingTransport(owned);
                state.addTickDebt(speed.subtract(java.math.BigInteger.ONE));
                int work=state.tickDebt().min(EXTRA_TICK_WINDOW).intValueExact();
                if(work==0)return null;
                NativeWorldCheckpoint.beforeOwnedWork(level.getServer(),state.owner());
                stepping=true;
                processing.beginInterval(work);
                try{
                    for(int done=0;done<work && running();){
                        int span=processing.advance(work-done);
                        if(span>0){done+=span;continue;}
                        if(!running())break;
                        state.advanceProcessing(1);
                        processing.tickNative();
                        processingTransport(owned);
                        state.consumeTickDebt();done++;
                    }
                }finally{processing.endInterval();}
                return null;
            });
        } finally {
            stepping=false;
            if(owned!=null)owned.endTickBundle();
        }
    }
    private void processingTransport(com.syaru.ae2machines.runtime.MekNativeRequests owned){
        if(owned!=null)owned.tick();
        if(com.syaru.ae2machines.runtime.NativeMachines.mek())com.syaru.ae2machines.compat.mek.MekNativeNetworks.tick(level);
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
            thermalLayout=null;
            active = null;
            removeTickets();
            holdChunks();
            closed = true;
        }
    }
}
