package com.syaru.ae2machines.engine;

import java.math.BigInteger;

/** One tick of work, paid incrementally. A second input callback never grants a second tick. */
public final class DemandWork<K> {
    public record Checkpoint<K>(long tick, BatchJob.Checkpoint<K> start, BigInteger supplied) {}
    private final BatchJob<K> job;
    private BatchJob.Checkpoint<K> start;
    private long tick = Long.MIN_VALUE;
    private BigInteger ceiling = BigInteger.ZERO, supplied = BigInteger.ZERO;

    public DemandWork(BatchJob<K> job) { this.job = java.util.Objects.requireNonNull(job); }

    public static <K> DemandWork<K> restore(BatchJob<K> job, Checkpoint<K> saved) {
        var work = new DemandWork<>(job);
        work.start = saved.start(); work.tick = saved.tick();
        work.ceiling = maximumEnergy(saved.start());
        work.supplied = ExactAmounts.nonnegative(saved.supplied());
        if (work.supplied.compareTo(work.ceiling) > 0) throw new IllegalArgumentException("Overpaid work window");
        var calculated = work.calculate();
        var actual = job.checkpoint();
        if (actual.state() == BatchJob.State.QUARANTINED) calculated.quarantine();
        if (!actual.equals(calculated.checkpoint())) throw new IllegalArgumentException("Power window/progress mismatch");
        return work;
    }

    public BigInteger required(long now) {
        if (job.state() != BatchJob.State.RUNNING || now < tick) return BigInteger.ZERO;
        return now == tick ? ceiling.subtract(supplied)
                : maximumEnergy(job.checkpoint()).subtract(remainder());
    }

    public void advance(long now) {
        if (now <= tick || job.state() != BatchJob.State.RUNNING) return;
        BigInteger carry = remainder();
        start = job.checkpoint(); tick = now; ceiling = maximumEnergy(start); supplied = carry;
        job.copyProgress(calculate());
    }

    public BigInteger receive(BigInteger offered, long now, boolean simulate) {
        BigInteger accepted = ExactAmounts.nonnegative(offered).min(required(now));
        if (!simulate) {
            advance(now);
            if (accepted.signum() > 0) {
                var credit = remainder().add(accepted);
                supplied = supplied.add(accepted);
                // Sub-work-unit payments retain credit, but cannot change any native phase yet.
                if (credit.compareTo(start.recipe().energyPerWork()) >= 0)
                    job.copyProgress(calculate());
            }
        }
        return accepted;
    }

    private BatchJob<K> calculate() {
        var calculated = BatchJob.restore(start);
        calculated.tick(payment(start, supplied));
        return calculated;
    }

    private static BigInteger maximumEnergy(BatchJob.Checkpoint<?> start) {
        if (start.state() != BatchJob.State.RUNNING || start.recipe().energyPerWork().signum() == 0)
            return BigInteger.ZERO;
        var groups = new java.util.TreeMap<>(start.phases());
        var occupied = groups.values().stream().reduce(BigInteger.ZERO, BigInteger::add);
        var queued = start.total().subtract(start.completed()).subtract(occupied);
        var starting = start.lanes().subtract(occupied).min(queued);
        if (starting.signum() > 0) groups.merge(BigInteger.ZERO, starting, BigInteger::add);
        queued = queued.subtract(starting);
        var duration = start.recipe().duration();
        var work = BigInteger.ZERO;
        for (var group : groups.descendingMap().entrySet()) {
            var first = duration.subtract(group.getKey()).min(start.speed());
            work = work.add(first.multiply(group.getValue()));
            if (!first.equals(duration.subtract(group.getKey()))) continue;
            // Finished lanes reuse the remaining time; sub-tick cycles do not require trial jobs.
            var cycles = start.speed().subtract(first).divideAndRemainder(duration);
            var complete = queued.min(cycles[0].multiply(group.getValue()));
            work = work.add(complete.multiply(duration));
            queued = queued.subtract(complete);
            var partial = queued.min(group.getValue());
            work = work.add(partial.multiply(cycles[1]));
            queued = queued.subtract(partial);
        }
        return work.multiply(start.recipe().energyPerWork());
    }

    private static MeteredEnergy payment(BatchJob.Checkpoint<?> job, BigInteger amount) {
        return new MeteredEnergy(job.recipe().energyUnit(), amount, amount,
                job.recipe().minimumVoltage(), amount, false);
    }

    public BigInteger remainder() {
        return start == null ? BigInteger.ZERO : supplied.subtract(job.energyUsed().subtract(start.energyUsed()));
    }
    public BigInteger used() { return start == null ? BigInteger.ZERO : job.energyUsed().subtract(start.energyUsed()); }
    public BigInteger ceiling() { return ceiling; }
    public Checkpoint<K> checkpoint() { return start == null ? null : new Checkpoint<>(tick, start, supplied); }
}
