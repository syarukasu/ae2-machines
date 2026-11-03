package com.syaru.ae2machines.runtime;

import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import com.syaru.ae2machines.engine.Recipe;
import net.minecraft.core.Direction;
import java.math.BigInteger;
import java.util.Map;

/** Native slot operations for locally supplied work; no ME extraction or receipt ownership. */
public interface BufferedMachine {
    default void tickNative(){}
    default void tickNative(BigInteger speed){if(!speed.equals(BigInteger.ONE))throw new IllegalArgumentException("NATIVE_CLOCK_UNSUPPORTED");tickNative();}
    MEStorage storage(Direction side);
    BigInteger availableExecutions(Recipe<AEKey> recipe);
    void takeInputs(Recipe<AEKey> recipe,BigInteger executions);
    boolean putOutputs(Map<AEKey,BigInteger> outputs);
    boolean ejecting();
    default java.util.Optional<Recipe<AEKey>> discoverRecipe(){return java.util.Optional.empty();}
    interface WorkAccess {
        long insert(Recipe<AEKey> recipe,AEKey key,long amount,boolean simulate);
        long extract(AEKey key,long amount,boolean simulate);
        Map<AEKey,BigInteger> outputs();
    }
    default boolean bindWork(WorkAccess access){return false;}
    default boolean ejecting(AEKey key,Direction side){return ejecting();}
    /** Null uses the destination capability; -1 is an explicitly uncolored Mekanism packet. */
    default Integer transportColor(){return null;}
}
