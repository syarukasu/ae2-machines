package com.syaru.ae2machines.runtime;

import appeng.api.stacks.AEKey;
import com.syaru.ae2machines.engine.*;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;

/** Only native handler results enter this ledger. Planning coefficients cannot generate a receipt. */
public final class NativeWorkLedger {
    public record Snapshot(Map<AEKey, BigInteger> remaining, Map<AEKey, BigInteger> produced,
                           BigInteger energyUsed, long calls, boolean consumesAtStart) {
        public Snapshot(Map<AEKey,BigInteger> remaining,Map<AEKey,BigInteger> produced,BigInteger energyUsed,long calls) {
            this(remaining,produced,energyUsed,calls,false);
        }
        public Snapshot {
            remaining = ExactAmounts.freeze(remaining); produced = ExactAmounts.freeze(produced);
            ExactAmounts.nonnegative(energyUsed);
            if(calls<0)throw new IllegalArgumentException("Negative native call count");
        }
    }
    private final NativeBatchProcessor processor;
    private final Map<AEKey, BigInteger> remaining = new LinkedHashMap<>(), produced = new LinkedHashMap<>();
    private BigInteger energyUsed = BigInteger.ZERO;
    private long calls;
    public NativeWorkLedger(NativeBatchProcessor processor, Map<AEKey, BigInteger> inputs) {
        this.processor = java.util.Objects.requireNonNull(processor); remaining.putAll(ExactAmounts.freeze(inputs));
    }
    public void restore(Snapshot state) {
        if(state.consumesAtStart()!=processor.consumesAtStart())throw new IllegalStateException("Native consumption contract changed");
        remaining.clear(); remaining.putAll(ExactAmounts.freeze(state.remaining()));
        produced.clear(); produced.putAll(ExactAmounts.freeze(state.produced()));
        energyUsed = ExactAmounts.nonnegative(state.energyUsed()); calls = state.calls();
        if (calls < 0) throw new IllegalArgumentException("Negative native call count");
    }
    public void appendUnstarted(Map<AEKey,BigInteger> inputs) {
        if(calls!=0 || energyUsed.signum()!=0 || !produced.isEmpty())throw new IllegalStateException("Native work already started");
        ExactAmounts.freeze(inputs).forEach((key,n)->remaining.merge(key,n,BigInteger::add));
    }
    public void advance(BatchJob.Checkpoint<AEKey> before, BatchJob.Checkpoint<AEKey> after) {
        if (!energyUsed.equals(before.energyUsed())) throw new IllegalStateException("Native energy ledger mismatch");
        for (var step : PhaseAdvances.between(before, after)) {
            int from = step.from().intValueExact(), to = step.to().intValueExact();
            int maximum = processor.maximumOperations(from, to);
            if (maximum <= 0) throw new IllegalStateException("Native execution cannot accept this phase");
            var left = step.count();
            while (left.signum() > 0) {
                int n = left.min(BigInteger.valueOf(maximum)).intValueExact();
                var copies = processor.independentCopies() ? left.divide(BigInteger.valueOf(n)) : BigInteger.ONE;
                var result = processor.advance(from, to, n, Map.copyOf(remaining));
                int expectedProgress = to == after.recipe().duration().intValueExact() ? 0 : to;
                var expectedEnergy = after.recipe().energyPerWork().multiply(BigInteger.valueOf((long) to - from)).multiply(BigInteger.valueOf(n));
                if (result.progress() != expectedProgress || !expectedEnergy.equals(result.energyUsed()))
                    throw new IllegalStateException("Native processing did not advance paid work: phase="+from+".."+to+", count="+n
                            +", progress="+result.progress()+"/"+expectedProgress+", energy="+result.energyUsed()+"/"+expectedEnergy);
                ExactAmounts.multiply(ExactAmounts.freeze(result.consumed()), copies).forEach((key, amount) -> {
                    var available = remaining.getOrDefault(key, BigInteger.ZERO);
                    if (amount.compareTo(available) > 0) throw new IllegalStateException("Native consumption exceeds owned input");
                    var rest = available.subtract(amount);
                    if (rest.signum() == 0) remaining.remove(key); else remaining.put(key, rest);
                });
                ExactAmounts.multiply(ExactAmounts.freeze(result.produced()), copies).forEach((key, amount) -> produced.merge(key, amount, BigInteger::add));
                energyUsed = energyUsed.add(result.energyUsed().multiply(copies)); calls = Math.incrementExact(calls);
                left = left.subtract(BigInteger.valueOf(n).multiply(copies));
            }
        }
        validate(after);
    }
    public void validate(BatchJob.Checkpoint<AEKey> state) {
        validate(snapshot(),state);
    }
    public static void validate(Snapshot nativeState,BatchJob.Checkpoint<AEKey> state) {
        var consumed=state.completed();
        if(nativeState.consumesAtStart())
            consumed=consumed.add(state.phases().entrySet().stream().filter(e->e.getKey().signum()>0)
                    .map(Map.Entry::getValue).reduce(BigInteger.ZERO,BigInteger::add));
        if (!nativeState.energyUsed().equals(state.energyUsed())
                || !nativeState.remaining().equals(ExactAmounts.multiply(state.recipe().inputs(), state.total().subtract(consumed)))
                || !nativeState.produced().equals(ExactAmounts.multiply(state.recipe().outputs(), state.completed())))
            throw new IllegalStateException("Native material/energy conservation failed");
    }
    public Map<AEKey, BigInteger> outputs() { return Map.copyOf(produced); }
    public Snapshot snapshot() { return new Snapshot(remaining, produced, energyUsed, calls,processor.consumesAtStart()); }
}
