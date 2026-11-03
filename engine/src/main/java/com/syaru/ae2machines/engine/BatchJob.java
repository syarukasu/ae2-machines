package com.syaru.ae2machines.engine;

import java.math.BigInteger;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** One aggregate state per batch, including for quantities larger than long. */
public final class BatchJob<K> {
    public enum State { RUNNING, OUTPUT_READY, ACKNOWLEDGED, CANCELLED, QUARANTINED }
    public record Checkpoint<K>(UUID id, String digest, Recipe<K> recipe, BigInteger total,
            BigInteger lanes, BigInteger speed, BigInteger completed, Map<BigInteger, BigInteger> phases,
            BigInteger energyUsed, State state) {
        public Checkpoint { phases = Map.copyOf(phases); }
    }
    private final UUID id;
    private final String digest;
    private final Recipe<K> recipe;
    private final BigInteger total, lanes, speed;
    private BigInteger completed = BigInteger.ZERO, energyUsed = BigInteger.ZERO;
    private final TreeMap<BigInteger, BigInteger> phases = new TreeMap<>();
    private State state = State.RUNNING;

    public BatchJob(UUID id, String digest, Recipe<K> recipe, BigInteger total, BigInteger lanes, BigInteger speed) {
        this.id = java.util.Objects.requireNonNull(id);
        if (digest == null || digest.isBlank()) throw new IllegalArgumentException("Digest required");
        this.digest = digest;
        this.recipe = java.util.Objects.requireNonNull(recipe);
        this.total = ExactAmounts.positive(total);
        this.lanes = ExactAmounts.positive(lanes).min(total);
        this.speed = ExactAmounts.positive(speed);
    }
    public static <K> BatchJob<K> restore(Checkpoint<K> cp) {
        var job = new BatchJob<>(cp.id(), cp.digest(), cp.recipe(), cp.total(), cp.lanes(), cp.speed());
        ExactAmounts.nonnegative(cp.completed());
        ExactAmounts.nonnegative(cp.energyUsed());
        if (cp.completed().compareTo(cp.total()) > 0 || cp.lanes().compareTo(cp.total()) > 0)
            throw new IllegalArgumentException("Invalid batch progress");
        BigInteger active = BigInteger.ZERO, partialWork = BigInteger.ZERO;
        for (var phase : cp.phases().entrySet()) {
            ExactAmounts.nonnegative(phase.getKey()); ExactAmounts.positive(phase.getValue());
            if (phase.getKey().compareTo(cp.recipe().duration()) >= 0)
                throw new IllegalArgumentException("Completed phase in active work");
            active = active.add(phase.getValue());
            partialWork = partialWork.add(phase.getKey().multiply(phase.getValue()));
        }
        if (active.compareTo(cp.lanes()) > 0 || active.add(cp.completed()).compareTo(cp.total()) > 0)
            throw new IllegalArgumentException("More active lanes than owned work");
        boolean finished = cp.completed().equals(cp.total());
        if (finished && !cp.phases().isEmpty()) throw new IllegalArgumentException("Finished job has active work");
        java.util.Objects.requireNonNull(cp.state());
        if ((cp.state() == State.OUTPUT_READY || cp.state() == State.ACKNOWLEDGED) != finished
                && cp.state() != State.QUARANTINED) throw new IllegalArgumentException("Invalid batch state");
        BigInteger actualWork = cp.completed().multiply(cp.recipe().duration()).add(partialWork);
        if (!actualWork.multiply(cp.recipe().energyPerWork()).equals(cp.energyUsed()))
            throw new IllegalArgumentException("Energy/progress mismatch");
        if (cp.state() == State.CANCELLED && actualWork.signum() != 0)
            throw new IllegalArgumentException("Irreversible cancellation");
        job.completed = cp.completed(); job.phases.putAll(cp.phases());
        job.energyUsed = cp.energyUsed(); job.state = cp.state();
        return job;
    }
    public Checkpoint<K> checkpoint() {
        return new Checkpoint<>(id, digest, recipe, total, lanes, speed, completed, phases, energyUsed, state);
    }
    void copyProgress(BatchJob<K> calculated) {
        if (!id.equals(calculated.id) || !digest.equals(calculated.digest) || !recipe.equals(calculated.recipe)
                || !total.equals(calculated.total) || !lanes.equals(calculated.lanes) || !speed.equals(calculated.speed))
            throw new IllegalArgumentException("Different work ownership");
        completed = calculated.completed; energyUsed = calculated.energyUsed; state = calculated.state;
        phases.clear(); phases.putAll(calculated.phases);
    }

    /** Advance phase groups, never individual lanes. Energy scarcity may split a group. */
    public void tick(MeteredEnergy energy) {
        if (state != State.RUNNING || !energy.supports(recipe)) return;
        BigInteger occupied = phases.values().stream().reduce(BigInteger.ZERO, BigInteger::add);
        BigInteger queued = total.subtract(completed).subtract(occupied);
        BigInteger starting = lanes.subtract(occupied).min(queued);
        addPhase(BigInteger.ZERO, starting);
        queued = queued.subtract(starting);
        var previous = new TreeMap<>(phases);
        phases.clear();
        for (var group : previous.descendingMap().entrySet()) {
            BigInteger active = group.getValue(), work = group.getKey(), time = speed;
            while (active.signum() > 0 && time.signum() > 0) {
                BigInteger step = recipe.duration().subtract(work).min(time);
                BigInteger available = recipe.energyPerWork().signum() == 0 ? active.multiply(time)
                        : energy.stored().divide(recipe.energyPerWork());
                if (available.compareTo(active.multiply(step)) < 0) {
                    // Advance as many lanes as can actually be powered, retaining all other progress.
                    BigInteger[] split = available.divideAndRemainder(step);
                    BigInteger advanced = split[0], partial = split[1];
                    pay(energy, available.multiply(recipe.energyPerWork()));
                    if (work.add(step).equals(recipe.duration())) completed = completed.add(advanced);
                    else addPhase(work.add(step), advanced);
                    if (partial.signum() > 0) addPhase(work.add(partial), BigInteger.ONE);
                    addPhase(work, active.subtract(advanced).subtract(partial.signum() > 0 ? BigInteger.ONE : BigInteger.ZERO));
                    active = BigInteger.ZERO;
                    break;
                }
                BigInteger waves = BigInteger.ONE;
                if (work.signum() == 0 && step.equals(recipe.duration())) {
                    waves = time.divide(step).min(BigInteger.ONE.add(queued.divide(active)))
                            .min(available.divide(active.multiply(step)));
                }
                BigInteger elapsed = step.multiply(waves);
                pay(energy, active.multiply(elapsed).multiply(recipe.energyPerWork()));
                time = time.subtract(elapsed);
                if (work.add(step).equals(recipe.duration())) {
                    completed = completed.add(active.multiply(waves));
                    queued = queued.subtract(active.multiply(waves.subtract(BigInteger.ONE)));
                    active = active.min(queued);
                    queued = queued.subtract(active);
                    work = BigInteger.ZERO;
                } else work = work.add(step);
            }
            addPhase(work, active);
        }
        if (completed.equals(total)) state = State.OUTPUT_READY;
    }
    private void addPhase(BigInteger work, BigInteger count) {
        if (count.signum() > 0) phases.merge(work, count, BigInteger::add);
    }
    private void pay(MeteredEnergy buffer, BigInteger n) { buffer.consume(n); energyUsed = energyUsed.add(n); }
    public boolean acknowledge() {
        if (state == State.ACKNOWLEDGED) return true;
        if (state != State.OUTPUT_READY) return false;
        state = State.ACKNOWLEDGED; return true;
    }
    public boolean cancel() {
        if (state == State.CANCELLED) return true;
        if (state != State.RUNNING || completed.signum() != 0 || phases.keySet().stream().anyMatch(p -> p.signum() != 0)) return false;
        state = State.CANCELLED; return true;
    }
    public void quarantine() { state = State.QUARANTINED; }
    public Map<K, BigInteger> receipt() {
        return state == State.OUTPUT_READY || state == State.ACKNOWLEDGED
                ? nativeReceipt == null ? ExactAmounts.multiply(recipe.outputs(), completed) : nativeReceipt.get() : Map.of();
    }
    private java.util.function.Supplier<Map<K, BigInteger>> nativeReceipt;
    public void useNativeReceipt(java.util.function.Supplier<Map<K, BigInteger>> receipt) {
        nativeReceipt = java.util.Objects.requireNonNull(receipt);
    }
    public BigInteger completed() { return completed; }
    public BigInteger energyUsed() { return energyUsed; }
    public State state() { return state; }
    public UUID id() { return id; }
    public String digest() { return digest; }
}
