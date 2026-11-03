package com.syaru.ae2machines.platform;

import mekanism.api.energy.IEnergyContainer;
import mekanism.api.recipes.cache.CachedRecipe;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import java.math.BigInteger;
import java.util.function.Supplier;

public final class MekNativeEnergy {
    private MekNativeEnergy() {}
    public static void process(CachedRecipe<?> recipe){recipe.process();}
    public static boolean independentRecipe(mekanism.api.recipes.ItemStackToItemStackRecipe recipe) {
        return recipe.getClass().getName().startsWith("mekanism.api.recipes.basic.")
                && NativeIngredients.fixedItem(recipe.getInput().ingredient().ingredient());
    }
    public static BigInteger configuredPerTick(mekanism.common.tile.prefab.TileEntityConfigurableMachine machine) {
        return MekanismAdapter.units(((mekanism.common.capabilities.energy.MachineEnergyContainer<?>)machine.getEnergyContainers(null).get(0)).getEnergyPerTick());
    }
    public static int maximumOperations(BigInteger cost, int items) {
        return cost.signum() == 0 ? items : BigInteger.valueOf(Long.MAX_VALUE).divide(joules(cost))
                .min(BigInteger.valueOf(items)).intValueExact();
    }
    public static Supplier<BigInteger> bind(CachedRecipe<?> recipe, BigInteger perOperation, BigInteger paid) {
        var credit = new IEnergyContainer() {
            private long remaining = joules(paid).longValueExact();
            public long getEnergy() { return remaining; }
            public long getMaxEnergy() { return joules(paid).longValueExact(); }
            public void setEnergy(long energy) { if (energy < 0) throw new IllegalArgumentException("Negative energy"); remaining = energy; }
            public void onContentsChanged() {}
            public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) { throw new UnsupportedOperationException("Transient payment view"); }
        };
        recipe.setEnergyRequirements(() -> joules(perOperation).longValueExact(), credit);
        return () -> MekanismAdapter.units(credit.getEnergy());
    }
    private static BigInteger joules(BigInteger units) {
        var result = units.divideAndRemainder(BigInteger.valueOf(10000));
        if (result[1].signum() != 0) throw new IllegalArgumentException("Fractional native joule");
        return result[0];
    }
}
