package com.syaru.ae2machines.api;

/** Optional native time advancement for the existing audited Q state contract. No type registry. */
public interface NativeTimeMachine extends NativeParallelMachine, NativeTimeParticipant {
    /**
     * Read-only plan equivalent to up to maximumTicks original ticks of this local state.
     * The implementation must use its original processing/IO and preserve intermediate
     * events, energy, random effects and saved state. Return null if that cannot be proven.
     * No world time reads, neighbor access, scheduled work or shared mutations are allowed.
     * A bounded int interval never narrows the BigInteger Q or outstanding world-time debt.
     */
    Step nativeTimeStep(int maximumTicks);
}
