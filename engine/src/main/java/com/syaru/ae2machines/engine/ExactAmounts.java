package com.syaru.ae2machines.engine;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class ExactAmounts {
    private ExactAmounts() {}
    public static BigInteger positive(BigInteger n) {
        if (Objects.requireNonNull(n).signum() <= 0) throw new IllegalArgumentException("Expected positive quantity");
        return n;
    }
    public static BigInteger nonnegative(BigInteger n) {
        if (Objects.requireNonNull(n).signum() < 0) throw new IllegalArgumentException("Negative quantity");
        return n;
    }
    public static <K> Map<K, BigInteger> freeze(Map<K, BigInteger> amounts) {
        var result = new LinkedHashMap<K, BigInteger>();
        amounts.forEach((key, n) -> result.put(Objects.requireNonNull(key), positive(n)));
        return Map.copyOf(result);
    }
    public static <K> Map<K, BigInteger> multiply(Map<K, BigInteger> amounts, BigInteger n) {
        nonnegative(n);
        if (n.signum() == 0) return Map.of();
        var result = new LinkedHashMap<K, BigInteger>();
        amounts.forEach((key, value) -> result.put(key, value.multiply(n)));
        return Map.copyOf(result);
    }
}
