package com.syaru.ae2machines.engine;

import java.math.BigInteger;
import java.util.Map;

/** A native adapter's validated recipe, never a pattern's promised output. */
public record Recipe<K>(String id, Map<K, BigInteger> inputs, Map<K, BigInteger> outputs,
                        BigInteger duration, BigInteger energyPerWork, String energyUnit,
                        BigInteger minimumVoltage) {
    public Recipe {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Missing recipe id");
        inputs = ExactAmounts.freeze(inputs);
        outputs = ExactAmounts.freeze(outputs);
        if (inputs.isEmpty() || outputs.isEmpty()) throw new IllegalArgumentException("Empty recipe");
        ExactAmounts.positive(duration);
        ExactAmounts.nonnegative(energyPerWork);
        ExactAmounts.nonnegative(minimumVoltage);
        if (!java.util.Set.of("FE","EU","MEK_J_1E4").contains(energyUnit)) throw new IllegalArgumentException("Unknown energy unit");
    }
}
