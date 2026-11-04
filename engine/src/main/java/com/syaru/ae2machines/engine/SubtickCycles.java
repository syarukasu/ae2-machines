package com.syaru.ae2machines.engine;

import java.math.BigInteger;

/** Work allocation only. Native machine code remains responsible for committing actual IO. */
public final class SubtickCycles {
    private SubtickCycles() {}
    public record Work(BigInteger completed, BigInteger progress, BigInteger elapsed) {}

    public static Work plan(BigInteger progress, BigInteger duration, BigInteger time,
            BigInteger executableCycles, BigInteger poweredWork) {
        ExactAmounts.nonnegative(progress); ExactAmounts.positive(duration);
        ExactAmounts.nonnegative(time); ExactAmounts.nonnegative(executableCycles);
        ExactAmounts.nonnegative(poweredWork);
        if (progress.compareTo(duration) >= 0) throw new IllegalArgumentException("Completed native phase");
        if (executableCycles.signum() == 0) return new Work(BigInteger.ZERO, progress, BigInteger.ZERO);
        var elapsed = time.min(poweredWork).min(executableCycles.multiply(duration).subtract(progress));
        var split = progress.add(elapsed).divideAndRemainder(duration);
        return new Work(split[0], split[1], elapsed);
    }
}
