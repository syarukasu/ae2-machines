package com.syaru.ae2machines.platform;

import mekanism.api.energy.IEnergyContainer;
import mekanism.api.math.FloatingLong;
import mekanism.api.recipes.cache.CachedRecipe;
import net.minecraft.nbt.CompoundTag;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.function.Supplier;

public final class MekNativeEnergy {
    private static final ThreadLocal<Boolean> EXACT_AVAILABILITY=new ThreadLocal<>();
    private MekNativeEnergy() {}
    public static boolean exactAvailability(){return Boolean.TRUE.equals(EXACT_AVAILABILITY.get());}
    public static int divideAvailable(FloatingLong stored,FloatingLong price){
        var denominator=MekanismAdapter.units(price.toString());
        return denominator.signum()==0?Integer.MAX_VALUE:MekanismAdapter.units(stored.toString()).divide(denominator).min(BigInteger.valueOf(Integer.MAX_VALUE)).intValueExact();
    }
    public static void process(CachedRecipe<?> recipe){
        var previous=EXACT_AVAILABILITY.get();EXACT_AVAILABILITY.set(true);
        try{recipe.process();}finally{if(previous==null)EXACT_AVAILABILITY.remove();else EXACT_AVAILABILITY.set(previous);}
    }
    public static boolean independentRecipe(mekanism.api.recipes.ItemStackToItemStackRecipe recipe) {
        return recipe.getClass().getName().startsWith("mekanism.common.recipe.impl.")
                && recipe.getInput().getClass().getName().startsWith("mekanism.common.recipe.ingredient.");
    }
    public static BigInteger configuredPerTick(mekanism.common.tile.prefab.TileEntityConfigurableMachine machine) {
        return MekanismAdapter.units(((mekanism.common.capabilities.energy.MachineEnergyContainer<?>)machine.getEnergyContainers(null).get(0)).getEnergyPerTick().toString());
    }
    public static int maximumOperations(BigInteger cost, int items) {
        return cost.signum() == 0 ? items : MekanismAdapter.units(FloatingLong.MAX_VALUE.toString())
                .divide(cost).min(BigInteger.valueOf(items)).intValueExact();
    }
    public static Supplier<BigInteger> bind(CachedRecipe<?> recipe, BigInteger perOperation, BigInteger paid) {
        var credit = new IEnergyContainer() {
            private FloatingLong remaining = value(paid);
            public FloatingLong getEnergy() { return remaining; }
            public FloatingLong getMaxEnergy() { return value(paid); }
            public void setEnergy(FloatingLong energy) { remaining = energy.copy(); }
            public void onContentsChanged() {}
            public void deserializeNBT(CompoundTag tag) { throw new UnsupportedOperationException("Transient payment view"); }
        };
        recipe.setEnergyRequirements(() -> value(perOperation), credit);
        return () -> MekanismAdapter.units(credit.getEnergy().toString());
    }
    private static FloatingLong value(BigInteger units) {
        return FloatingLong.parseFloatingLong(new BigDecimal(units).movePointLeft(4).toPlainString());
    }
}
