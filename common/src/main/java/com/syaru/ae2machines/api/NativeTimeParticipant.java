package com.syaru.ae2machines.api;

import java.util.Objects;
import java.util.function.IntConsumer;

/** Native time contract, discovered by capability rather than a machine-name allowlist. */
public interface NativeTimeParticipant {
    /**
     * Read-only, bounded original-machine update. No visible intermediate event may be
     * hidden. Return null when an exact interval cannot be supplied. The rollback must
     * restore all changed state; this is not permission to synthesize recipe outputs.
     */
    Step nativeTimeStep(int maximumTicks);

    record Step(int limit, IntConsumer advance, Runnable rollback) {
        public Step(int limit, IntConsumer advance) { this(limit,advance,()->{}); }
        public Step {
            if(limit<0)throw new IllegalArgumentException("Negative native time interval");
            Objects.requireNonNull(advance);Objects.requireNonNull(rollback);
        }
    }
}
