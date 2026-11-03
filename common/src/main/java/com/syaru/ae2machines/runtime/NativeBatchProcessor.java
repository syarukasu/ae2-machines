package com.syaru.ae2machines.runtime;

import appeng.api.stacks.AEKey;
import java.math.BigInteger;
import java.util.Map;

/** Executes native consumption/production for one homogeneous phase group. */
public interface NativeBatchProcessor {
    record Result(Map<AEKey, BigInteger> consumed, Map<AEKey, BigInteger> produced,
                  BigInteger energyUsed, int progress) {}
    int maximumOperations(int from, int to);
    /**
     * Opt-in only for identical, independent native states: advance resets its handlers and
     * has no random, world, shared-inventory, or per-call side effects. A representative's
     * actual transition can then stand for multiple copies of that state. Unknown executors
     * must retain the default; recipe coefficients alone do not establish this property.
     */
    default boolean independentCopies() { return false; }
    default boolean consumesAtStart() { return false; }
    Result advance(int from, int to, int operations, Map<AEKey, BigInteger> ownedInputs);
}
