package com.syaru.ae2machines.runtime;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import com.syaru.ae2machines.engine.*;
import java.math.BigInteger;
import java.util.*;

/** ACO-neutral runtime. No ME extractions, pattern-derived outputs, or external deliveries. */
public final class ProcessingRuntime {
    @FunctionalInterface public interface DurableCheckpoint { void save(ProcessingRuntime runtime); }
    private final Map<UUID, BatchJob<AEKey>> jobs = new LinkedHashMap<>();
    private final Map<UUID, String> forgotten = new LinkedHashMap<>();
    private final MeteredEnergy energy;
    private final DurableCheckpoint checkpoint;
    private CompiledProgram program;
    private BatchJob<AEKey> active;
    private DemandWork<AEKey> demand;
    private BigInteger speed = BigInteger.ONE, lanes = BigInteger.ONE;
    private BigInteger passiveNativeLanes = BigInteger.ONE;
    public void passiveNativeLanes(BigInteger count) { passiveNativeLanes=ExactAmounts.positive(count); }
    private long lastTick = Long.MIN_VALUE;
    private boolean quarantined;
    private java.util.function.Function<Recipe<AEKey>,NativeBatchProcessor> nativeFactory;
    private final Map<UUID,NativeWorkLedger> nativeJobs=new LinkedHashMap<>();
    private final Map<UUID,NativeWorkLedger.Snapshot> savedNativeJobs=new LinkedHashMap<>();
    public void nativeExecution(java.util.function.Function<Recipe<AEKey>,NativeBatchProcessor> factory) {
        nativeFactory=Objects.requireNonNull(factory);
        for(var job:jobs.values()) {
            var state=nativeJobs.containsKey(job.id())?nativeJobs.get(job.id()).snapshot():savedNativeJobs.get(job.id());
            if(state==null && (job.energyUsed().signum()!=0 || job.completed().signum()!=0))
                throw new IllegalArgumentException("LEGACY_ACTIVE_JOB_REQUIRES_FINISH_OR_RESTORE");
            var ledger=new NativeWorkLedger(factory.apply(job.checkpoint().recipe()),
                    ExactAmounts.multiply(job.checkpoint().recipe().inputs(),job.checkpoint().total()));
            if(state!=null)ledger.restore(state);
            ledger.validate(job.checkpoint());nativeJobs.put(job.id(),ledger);job.useNativeReceipt(ledger::outputs);
        }
        savedNativeJobs.clear();
    }
    public Map<UUID,NativeWorkLedger.Snapshot> nativeSnapshots() {
        var result=new LinkedHashMap<>(savedNativeJobs);
        nativeJobs.forEach((id,ledger)->result.put(id,ledger.snapshot()));return Map.copyOf(result);
    }
    public void restoreNative(Map<UUID,NativeWorkLedger.Snapshot> states) {
        if(!jobs.keySet().containsAll(states.keySet()))throw new IllegalArgumentException("Orphan native execution");
        savedNativeJobs.putAll(states);
        states.forEach((id,state)->{NativeWorkLedger.validate(state,jobs.get(id).checkpoint());jobs.get(id).useNativeReceipt(()->state.produced());});
    }
    private java.util.function.BiPredicate<UUID,BigInteger> completionReady=(id,completed)->true;
    public void completionGate(java.util.function.BiPredicate<UUID,BigInteger> gate){completionReady=Objects.requireNonNull(gate);}
    public boolean deliveryReady(UUID id){var job=jobs.get(id);return job!=null && completionReady.test(id,job.checkpoint().completed());}
    public ProcessingRuntime(MeteredEnergy energy, DurableCheckpoint checkpoint) {
        this.energy = Objects.requireNonNull(energy); this.checkpoint = Objects.requireNonNull(checkpoint);
    }
    public void bind(CompiledProgram program) {
        if (!jobs.isEmpty()) throw new IllegalStateException("Cannot replace an owned factory");
        this.program = Objects.requireNonNull(program);
    }
    public void resume(PatternProgram program) {
        if(this.program!=null) throw new IllegalStateException("Factory is already bound");
        refresh(program);
    }
    public void refresh(PatternProgram program) {
        if(jobs.values().stream().anyMatch(job->!program.contains(job.checkpoint().recipe()) && nativeFactory==null))
            throw new IllegalArgumentException("Native recipe changed for an owned job");
        this.program=Objects.requireNonNull(program);
    }
    public void configure(BigInteger speed, BigInteger lanes) {
        if (!canConfigure()) throw new IllegalStateException("Finish jobs before reconfiguring");
        this.speed = ExactAmounts.positive(speed); this.lanes = ExactAmounts.positive(lanes);
    }
    public Optional<Recipe<AEKey>> resolve(IPatternDetails pattern) {
        return quarantined || program == null || !program.boundaryActive() ? Optional.empty() : program.resolve(pattern);
    }
    public boolean accept(UUID id, String digest, IPatternDetails pattern, BigInteger count,
                          Map<AEKey, BigInteger> provided, Map<AEKey, BigInteger> promised) {
        BatchJob<AEKey> old = jobs.get(id);
        if (old != null) return old.digest().equals(digest) && old.state() != BatchJob.State.CANCELLED;
        if (quarantined || forgotten.containsKey(id) || count.signum() <= 0 || hasActiveJob()) return false;
        var candidate = resolve(pattern);
        if (candidate.isEmpty()) return false;
        return acceptRecipe(id,digest,candidate.get(),count,provided,promised,program.nativeLanes(pattern));
    }
    public boolean acceptPassive(UUID id,Recipe<AEKey> recipe,BigInteger count) {
        if(nativeFactory==null || quarantined || program==null || !program.boundaryActive() || count.signum()<=0
                || hasActiveJob() || tracks(id))return false;
        return acceptRecipe(id,"ae2-normal",recipe,count,ExactAmounts.multiply(recipe.inputs(),count),
                ExactAmounts.multiply(recipe.outputs(),count),passiveNativeLanes);
    }
    public BigInteger appendRoom(Recipe<AEKey> recipe) {
        if(nativeFactory==null || quarantined || program==null || !program.boundaryActive())return BigInteger.ZERO;
        // Hold only a tick's executable work, including successive cycles when S exceeds the recipe duration.
        var parallel=lanes.multiply(passiveNativeLanes);
        var window=parallel.multiply(speed.divide(recipe.duration()).max(BigInteger.ONE));
        if(active==null)return window;
        var state=active.checkpoint();
        if(!state.digest().equals("ae2-normal") || !state.recipe().equals(recipe) || state.state()!=BatchJob.State.RUNNING
                || state.energyUsed().signum()!=0 || state.completed().signum()!=0 || !state.phases().isEmpty()
                || demand!=null && demand.checkpoint()!=null && demand.checkpoint().supplied().signum()!=0)
            return BigInteger.ZERO;
        return window.subtract(state.total()).max(BigInteger.ZERO);
    }
    public boolean appendLocal(UUID id,Recipe<AEKey> recipe,BigInteger count,Map<AEKey,BigInteger> inputs) {
        if(active==null || !active.id().equals(id) || count.signum()<=0 || appendRoom(recipe).compareTo(count)<0
                || !ExactAmounts.multiply(recipe.inputs(),count).equals(inputs))return false;
        var old=active.checkpoint();var total=old.total().add(count);
        var ledger=nativeJobs.get(id);ledger.appendUnstarted(inputs);
        active=new BatchJob<>(id,old.digest(),recipe,total,lanes.multiply(passiveNativeLanes),old.speed());active.useNativeReceipt(ledger::outputs);
        jobs.put(id,active);nativeJobs.put(id,ledger);demand=null;persist();return true;
    }
    public boolean appendable() {
        return active!=null && appendRoom(active.checkpoint().recipe()).signum()>0;
    }
    private boolean acceptRecipe(UUID id,String digest,Recipe<AEKey> recipe,BigInteger count,
                                 Map<AEKey,BigInteger> provided,Map<AEKey,BigInteger> promised,BigInteger nativeLanes) {
        if (!energy.supports(recipe) || !ExactAmounts.multiply(recipe.inputs(), count).equals(provided)
                || !ExactAmounts.multiply(recipe.outputs(), count).equals(promised)) return false;
        var ledger=nativeFactory==null?null:new NativeWorkLedger(nativeFactory.apply(recipe),provided);
        active = new BatchJob<>(id, digest, recipe, count, lanes.multiply(nativeLanes), speed);
        if(ledger!=null){nativeJobs.put(id,ledger);active.useNativeReceipt(ledger::outputs);}
        jobs.put(id, active);
        persist();
        return true;
    }
    public void tick(long gameTick) {
        if (!canWork(gameTick)) return;
        var carried=demand==null ? BigInteger.ZERO:demand.remainder();
        if(energy.stored().add(carried).compareTo(active.checkpoint().recipe().energyPerWork())<0) return;
        var before = active.checkpoint(); var previous = powerCheckpoint();
        advancePower(gameTick);
        advanceNative(before);
        if (!before.equals(active.checkpoint()) || !Objects.equals(previous,powerCheckpoint())) persist();
    }
    private boolean canWork(long tick) {
        return !quarantined && savedNativeJobs.isEmpty() && program != null && program.boundaryActive() && active != null
                && active.state()==BatchJob.State.RUNNING && tick>=lastTick && (demand!=null || tick>lastTick);
    }
    private void advancePower(long tick) {
        if (demand==null) demand=new DemandWork<>(active);
        demand.advance(tick); lastTick=tick;
        // Captured native charge is a finite asset, not the capacity of the accelerated power path.
        var drawn=demand.required(tick).min(energy.stored());
        if(drawn.signum()>0) { demand.receive(drawn,tick,false); energy.consume(drawn); }
    }
    public BigInteger energyDemand(long tick) {
        if(!canWork(tick)) return BigInteger.ZERO;
        var work=demand==null ? new DemandWork<>(active):demand;
        return work.required(tick).subtract(energy.stored()).max(BigInteger.ZERO);
    }
    public BigInteger receiveEnergy(BigInteger offered,long tick,boolean simulate) {
        var accepted=ExactAmounts.nonnegative(offered).min(energyDemand(tick));
        if(simulate || accepted.signum()==0) return accepted;
        var before=active.checkpoint();
        advancePower(tick);
        var received=demand.receive(accepted,tick,false);
        if(!received.equals(accepted)) throw new IllegalStateException("Power demand changed during payment");
        advanceNative(before);
        persist(); return received;
    }
    /** EU packets are indivisible. Only the last packet's unused tail is retained, never an idle charge. */
    public BigInteger roundedEnergyDemand(long tick,BigInteger quantum) {
        ExactAmounts.positive(quantum);
        var needed=energyDemand(tick);
        return needed.signum()==0?BigInteger.ZERO:needed.add(quantum).subtract(BigInteger.ONE).divide(quantum).multiply(quantum);
    }
    public BigInteger receiveRoundedEnergy(BigInteger offered,long tick,BigInteger quantum,boolean simulate) {
        if(quantum.compareTo(energy.capacity())>0 || quantum.compareTo(energy.maxInput())>0)return BigInteger.ZERO;
        var accepted=ExactAmounts.nonnegative(offered).min(roundedEnergyDemand(tick,quantum));
        if(simulate || accepted.signum()==0)return accepted;
        var before=active.checkpoint();
        advancePower(tick);
        var used=demand.receive(accepted,tick,false);var tail=accepted.subtract(used);
        if(!energy.insert(tail,tick,false).equals(tail))throw new IllegalStateException("Native payment tail exceeds retained capacity");
        advanceNative(before);
        persist();return accepted;
    }
    private void advanceNative(BatchJob.Checkpoint<AEKey> before) {
        var ledger=nativeJobs.get(active.id());if(ledger==null)return;
        try {ledger.advance(before,active.checkpoint());}
        catch(RuntimeException failure){quarantined=true;active.quarantine();persist();throw failure;}
    }
    public long receivePackets(long voltage,long amperage,long tick,boolean simulate) {
        if(!energy.unit().equals("EU") || voltage<=0 || amperage<=0 || BigInteger.valueOf(voltage).compareTo(energy.voltage())>0)return 0;
        var needed=energyDemand(tick);if(needed.signum()==0)return 0;
        var v=BigInteger.valueOf(voltage);
        long accepted=needed.add(v).subtract(BigInteger.ONE).divide(v).min(BigInteger.valueOf(amperage)).longValueExact();
        if(simulate)return accepted;
        var payment=v.multiply(BigInteger.valueOf(accepted));
        return receiveRoundedEnergy(payment,tick,v,false).divide(v).longValueExact();
    }
    public DemandWork.Checkpoint<AEKey> powerCheckpoint() { return demand==null ? null:demand.checkpoint(); }
    public long lastWorkTick() { return lastTick; }
    /** A durable local journal can be newer than level.dat after a hard stop. Keep paid progress. */
    public void rebaseRecoveredTime(long tick) {
        if(tick>=lastTick)return;
        if(demand!=null) {
            var saved=demand.checkpoint();
            if(saved!=null)demand=DemandWork.restore(active,new DemandWork.Checkpoint<>(tick,saved.start(),saved.supplied()));
        }
        lastTick=tick;
    }
    public void restorePower(long tick,DemandWork.Checkpoint<AEKey> saved) {
        if(demand!=null) throw new IllegalStateException("Power already loaded");
        lastTick=tick;
        if(saved!=null) {
            if(active==null || saved.tick()!=tick) throw new IllegalArgumentException("Orphan power window");
            demand=DemandWork.restore(active,saved);
        }
    }
    public int powerPermille() {
        return demand==null ? 0 : demand.ceiling().signum()==0 ? 1000
                : demand.used().multiply(BigInteger.valueOf(1000)).divide(demand.ceiling()).intValueExact();
    }
    public int powerPermille(long tick) {
        return tick>=lastTick && tick-lastTick<=1 ? powerPermille():0;
    }
    public Optional<BatchJob<AEKey>> find(UUID id, String digest) {
        var job = jobs.get(id);
        return job != null && job.digest().equals(digest) ? Optional.of(job) : Optional.empty();
    }
    public boolean tracks(UUID id) { return jobs.containsKey(id) || forgotten.containsKey(id); }
    public boolean acknowledge(UUID id, String digest) {
        var found = find(id,digest);
        if (quarantined || found.isEmpty() || !deliveryReady(id)) return false;
        if (found.get().state() == BatchJob.State.ACKNOWLEDGED) return true;
        if (!found.get().acknowledge()) return false;
        if (active == found.get()) { active = null; demand=null; }
        persist(); return true;
    }
    public boolean cancel(UUID id, String digest) {
        var found = find(id,digest);
        if (quarantined || found.isEmpty()) return false;
        if (found.get().state() == BatchJob.State.CANCELLED) return true;
        if(active==found.get() && demand!=null && demand.remainder().signum()>0) return false;
        if (!found.get().cancel()) return false;
        if (active == found.get()) { active = null; demand=null; }
        persist(); return true;
    }
    public boolean forget(UUID id, String digest) {
        if (digest.equals(forgotten.get(id))) return !quarantined;
        var found = find(id,digest);
        if (quarantined || found.isEmpty() || found.get().state() != BatchJob.State.ACKNOWLEDGED) return false;
        jobs.remove(id);nativeJobs.remove(id);savedNativeJobs.remove(id); forgotten.put(id,digest); persist(); return true;
    }
    void finishLocalDelivery(UUID id) {
        var job=jobs.get(id);
        if(job==null || !deliveryReady(id) || !job.digest().equals("ae2-normal") || job.state()!=BatchJob.State.OUTPUT_READY)
            throw new IllegalStateException("Invalid local output delivery");
        job.acknowledge();jobs.remove(id);nativeJobs.remove(id);savedNativeJobs.remove(id);if(active==job) { active=null; demand=null; }
        // These IDs never leave the local provider, so there is no external retry contract to retain forever.
        persist();
    }
    public void restore(Collection<BatchJob.Checkpoint<AEKey>> snapshots) {
        if (!jobs.isEmpty()) throw new IllegalStateException("Already loaded");
        var restored = new LinkedHashMap<UUID,BatchJob<AEKey>>();
        for (var snap : snapshots) {
            if (restored.put(snap.id(),BatchJob.restore(snap)) != null) throw new IllegalArgumentException("Duplicate job");
        }
        var pending = restored.values().stream().filter(ProcessingRuntime::occupiesMachine).toList();
        if (pending.size() > 1)
            throw new IllegalArgumentException("Multiple jobs exceed this runtime's lane ownership");
        jobs.putAll(restored);
        active = pending.isEmpty() ? null : pending.get(0);
        quarantined = active != null && active.state() == BatchJob.State.QUARANTINED;
    }
    public void restoreForgotten(Map<UUID,String> tombstones) {
        if (!forgotten.isEmpty() || tombstones.keySet().stream().anyMatch(jobs::containsKey))
            throw new IllegalArgumentException("Conflicting released jobs");
        tombstones.forEach((id,digest) -> {
            if (id == null || digest == null || digest.isBlank()) throw new IllegalArgumentException("Invalid tombstone");
        });
        forgotten.putAll(tombstones);
    }
    private boolean hasActiveJob() {
        return active != null;
    }
    private static boolean occupiesMachine(BatchJob<?> job) {
        return job.state() == BatchJob.State.RUNNING || job.state() == BatchJob.State.OUTPUT_READY
                || job.state() == BatchJob.State.QUARANTINED;
    }
    public boolean canConfigure() { return !quarantined && !hasActiveJob(); }
    /** Compact read-only state for an open menu: idle, working, no power, output pending. */
    public int displayState(long tick) {
        if(active==null) return 0;
        if(active.state()==BatchJob.State.OUTPUT_READY) return 3;
        return powerPermille(tick)==0 ? 2 : 1;
    }
    public Map<UUID,String> forgotten() { return Map.copyOf(forgotten); }
    private void persist() {
        try { checkpoint.save(this); }
        catch (RuntimeException e) {
            quarantined = true;
            if (active != null) active.quarantine();
            throw e;
        }
    }
    public Collection<BatchJob.Checkpoint<AEKey>> snapshots() { return jobs.values().stream().map(BatchJob::checkpoint).toList(); }
    public MeteredEnergy energy() { return energy; }
    public BigInteger speed() { return speed; }
    public BigInteger lanes() { return lanes; }
    public boolean quarantined() { return quarantined; }
    public void restoreQuarantine(boolean stored) { quarantined |= stored; }
    public boolean hasJobs() { return !jobs.isEmpty(); }
}
