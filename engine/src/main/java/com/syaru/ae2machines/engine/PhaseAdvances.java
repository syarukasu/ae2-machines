package com.syaru.ae2machines.engine;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

/** Monotone changes between paid phase groups, without expanding individual lanes. */
public final class PhaseAdvances {
    public record Step(BigInteger from, BigInteger to, BigInteger count) {}
    private PhaseAdvances() {}

    public static List<Step> between(BatchJob.Checkpoint<?> before, BatchJob.Checkpoint<?> after) {
        if (!before.id().equals(after.id()) || !before.recipe().equals(after.recipe())
                || !before.total().equals(after.total()) || after.completed().compareTo(before.completed()) < 0)
            throw new IllegalArgumentException("Different or regressing native work");
        var old = phases(before, before.completed());
        var next = phases(after, before.completed());
        var result = new ArrayList<Step>();
        var work = BigInteger.ZERO;
        while (!old.isEmpty() && !next.isEmpty()) {
            var a = old.firstEntry(); var b = next.firstEntry();
            if (b.getKey().compareTo(a.getKey()) < 0) throw new IllegalArgumentException("Native progress regressed");
            var count = a.getValue().min(b.getValue());
            if (!a.getKey().equals(b.getKey())) {
                result.add(new Step(a.getKey(), b.getKey(), count));
                work = work.add(b.getKey().subtract(a.getKey()).multiply(count));
            }
            subtract(old, a.getKey(), count); subtract(next, b.getKey(), count);
        }
        if (!old.isEmpty() || !next.isEmpty() || !work.multiply(before.recipe().energyPerWork())
                .equals(after.energyUsed().subtract(before.energyUsed())))
            throw new IllegalArgumentException("Native work does not match paid progress");
        return List.copyOf(result);
    }
    private static TreeMap<BigInteger, BigInteger> phases(BatchJob.Checkpoint<?> state, BigInteger completedBefore) {
        var result = new TreeMap<BigInteger, BigInteger>(Comparator.reverseOrder());
        result.putAll(state.phases());
        var completed = state.completed().subtract(completedBefore);
        if (completed.signum() > 0) result.put(state.recipe().duration(), completed);
        var occupied = state.phases().values().stream().reduce(BigInteger.ZERO, BigInteger::add);
        var waiting = state.total().subtract(state.completed()).subtract(occupied);
        if (waiting.signum() > 0) result.merge(BigInteger.ZERO, waiting, BigInteger::add);
        return result;
    }
    private static void subtract(TreeMap<BigInteger, BigInteger> map, BigInteger key, BigInteger count) {
        var left = map.get(key).subtract(count);
        if (left.signum() == 0) map.remove(key); else map.put(key, left);
    }
}
