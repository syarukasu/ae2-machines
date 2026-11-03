package com.syaru.ae2machines.runtime;

import appeng.api.stacks.AEKey;
import com.syaru.ae2machines.engine.ExactAmounts;
import com.syaru.ae2machines.engine.Recipe;
import java.math.BigInteger;

/** Native energy uses 1/10000 joule units; no FE or EU conversion is guessed. */
public record MachineProfile(Recipe<AEKey> recipe, BigInteger capacity, BigInteger inputRate,
                             BigInteger stored, BigInteger unitsPerFE, BigInteger nativeQuantum,
                             BigInteger parallelism,long voltage,long amperage,String energyUnit) {
    public static final String MEK_UNIT = "MEK_J_1E4";
    public static final String GT_UNIT = "EU";
    public MachineProfile(Recipe<AEKey> recipe,BigInteger capacity,BigInteger inputRate,
                          BigInteger stored,BigInteger unitsPerFE,BigInteger nativeQuantum,
                          BigInteger parallelism,long voltage,long amperage) {
        this(recipe,capacity,inputRate,stored,unitsPerFE,nativeQuantum,parallelism,voltage,amperage,recipe.energyUnit());
    }
    public MachineProfile(Recipe<AEKey> recipe,BigInteger capacity,BigInteger inputRate,
                          BigInteger stored,BigInteger unitsPerFE,BigInteger nativeQuantum) {
        this(recipe,capacity,inputRate,stored,unitsPerFE,nativeQuantum,BigInteger.ONE,0,0);
    }
    public MachineProfile {
        if(!java.util.Set.of(MEK_UNIT,GT_UNIT,"FE").contains(energyUnit) || recipe!=null && !recipe.energyUnit().equals(energyUnit))
            throw new IllegalArgumentException("Inconsistent machine power unit");
        ExactAmounts.nonnegative(capacity); ExactAmounts.nonnegative(inputRate);
        ExactAmounts.nonnegative(stored); ExactAmounts.positive(unitsPerFE);
        ExactAmounts.positive(nativeQuantum);
        ExactAmounts.positive(parallelism);
        if(voltage<0 || amperage<0)throw new IllegalArgumentException("Negative native packet limit");
        if (stored.compareTo(capacity)>0) throw new IllegalArgumentException("Overfilled native machine");
    }
}
