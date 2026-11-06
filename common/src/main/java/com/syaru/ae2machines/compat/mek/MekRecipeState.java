package com.syaru.ae2machines.compat.mek;

import com.syaru.ae2machines.engine.SubtickCycles;
import com.syaru.ae2machines.mixin.*;
import com.syaru.ae2machines.platform.*;
import com.syaru.ae2machines.world.NativeWorldRuntime;
import java.math.BigInteger;
import java.util.*;
import java.util.function.*;
import mekanism.api.recipes.ItemStackToItemStackRecipe;
import mekanism.api.recipes.cache.CachedRecipe;
import mekanism.common.tile.prefab.TileEntityProgressMachine;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/** A native recipe and its current phase, not a multiplied observation of one past output. */
public record MekRecipeState(CachedRecipe<?> cached, int progress, int duration, int operations,
        BigInteger energyPerOperationTick, BigInteger energy, BigInteger executableCycles,
        List<Asset> assets, long recipeRevision) {
    public record Asset(BooleanSupplier unchanged, Runnable restore) {
        public static <T> Asset capture(Supplier<T> read, Consumer<T> write,
                UnaryOperator<T> copy, BiPredicate<T,T> equal) {
            var before=copy.apply(read.get());
            return new Asset(()->equal.test(before,read.get()),()->write.accept(copy.apply(before)));
        }
    }
    public enum Kind { COMPLETION_ONLY, PER_TICK_INPUT, RANDOM_OUTPUT, UNDECLARED }

    public static Kind kind(CachedRecipe<?> cached) {
        if(cached==null)return Kind.UNDECLARED;
        var type=cached.getClass();
        if(cached.getRecipe() instanceof mekanism.api.recipes.SawmillRecipe)return Kind.RANDOM_OUTPUT;
        if(type.getName().equals("mekanism.api.recipes.cache.ChemicalDissolutionCachedRecipe")
                || type.getName().equals("mekanism.api.recipes.cache.ItemStackConstantChemicalToItemStackCachedRecipe")
                || type.getName().equals("mekanism.api.recipes.cache.ItemStackConstantChemicalToObjectCachedRecipe"))
            return Kind.PER_TICK_INPUT;
        // These native algorithms have fixed IO at finishProcessing and no per-tick material use.
        if(type==mekanism.api.recipes.cache.OneInputCachedRecipe.class
                || type==mekanism.api.recipes.cache.TwoInputCachedRecipe.class
                || type.getName().equals("mekanism.api.recipes.cache.ChemicalCrystallizerCachedRecipe")
                || type==mekanism.api.recipes.cache.ChemicalChemicalToChemicalCachedRecipe.class) {
            var recipe=cached.getRecipe();
            if(recipe instanceof ItemStackToItemStackRecipe item)
                return MekanismAdapter.fixedOutput(item) && MekTimeClock.fixedInput(item.getInput())
                        ?Kind.COMPLETION_ONLY:Kind.UNDECLARED;
            return MekPassiveChemical.independent(recipe)?Kind.COMPLETION_ONLY:Kind.UNDECLARED;
        }
        return Kind.UNDECLARED;
    }

    public static MekRecipeState capture(TileEntityProgressMachine<?> machine,int offered) {
        if(offered<1 || !(machine.getLevel() instanceof ServerLevel world))return null;
        var cached=((MekTimeRecipeAccess)machine).machines$timeMonitor().getCachedRecipe(0);
        if(kind(cached)!=Kind.COMPLETION_ONLY || !MekThermalHooks.safe(cached.getClass()))return null;
        var access=(MekTimeCacheAccess)cached;
        int duration=access.machines$requiredTicks().getAsInt(),progress=machine.getOperatingTicks();
        if(duration<1 || progress<0 || progress>=duration || !machine.getActive()
                || !access.machines$canProcess().getAsBoolean()
                || ((MekNativeStep)cached).machines$progress()!=progress)return null;
        var state=NativeWorldRuntime.get(world).state();
        // The baseline supplier's lane-accounting callback is not a read-only quote.
        int group=progress==0?state.nativeLanes():state.processWork(machine.getBlockPos().asLong(),0);
        if(group<1)return null;
        var rate=MekRecipeTime.energyPerTick(cached);
        var energy=MekanismAdapter.energy(machine);
        if(rate.signum()<0)return null;
        var maxCycles=BigInteger.valueOf((long)progress+offered+duration-1).divide(BigInteger.valueOf(duration));
        int maximum=maxCycles.multiply(BigInteger.valueOf(group)).min(BigInteger.valueOf(Integer.MAX_VALUE)).intValueExact();
        var tracker=MekOperationsAccess.machines$quoteTracker(Set.of(),false,maximum);
        // Native input/output handlers determine matches and capacity, without executing their IO.
        access.machines$quoteOperations(tracker);
        var nativeLimit=(MekOperationsAccess)(Object)tracker;
        if(nativeLimit.machines$currentMax()<group || nativeLimit.machines$maxForEnergy()<group)return null;
        var cycles=BigInteger.valueOf(nativeLimit.machines$currentMax()/group);
        var assets=new ArrayList<Asset>();
        for(var slot:machine.getInventorySlots(null))
            assets.add(Asset.capture(slot::getStack,slot::setStack,ItemStack::copy,ItemStack::matches));
        assets.addAll(MekRecipeTime.tanks(machine));
        return new MekRecipeState(cached,progress,duration,group,rate,energy,cycles,List.copyOf(assets),
                com.syaru.ae2machines.runtime.FactoryRecipes.revision());
    }

    public BigInteger cost(){return energyPerOperationTick.multiply(BigInteger.valueOf(operations));}
    public SubtickCycles.Work work(int ticks) {
        var powered=cost().signum()==0?BigInteger.valueOf(ticks):energy.divide(cost());
        return SubtickCycles.plan(BigInteger.valueOf(progress),BigInteger.valueOf(duration),
                BigInteger.valueOf(ticks),executableCycles,powered);
    }
    public boolean current(TileEntityProgressMachine<?> machine) {
        var access=(MekTimeCacheAccess)cached;
        var world=(ServerLevel)machine.getLevel();
        var state=NativeWorldRuntime.get(world).state();
        return !machine.isRemoved() && world.getBlockEntity(machine.getBlockPos())==machine
                && recipeRevision==com.syaru.ae2machines.runtime.FactoryRecipes.revision()
                && ((MekTimeRecipeAccess)machine).machines$timeMonitor().getCachedRecipe(0)==cached
                && machine.getOperatingTicks()==progress && ((MekNativeStep)cached).machines$progress()==progress
                && access.machines$requiredTicks().getAsInt()==duration && access.machines$canProcess().getAsBoolean()
                && machine.getActive() && MekRecipeTime.energyPerTick(cached).equals(energyPerOperationTick)
                && MekanismAdapter.energy(machine).equals(energy)
                && (progress==0?state.nativeLanes():state.processWork(machine.getBlockPos().asLong(),0))==operations
                && !mekanism.common.CommonWorldTickHandler.flushTagAndRecipeCaches
                && assets.stream().allMatch(a->a.unchanged().getAsBoolean());
    }
    public void restoreAssets(){for(var asset:assets)asset.restore().run();}
}
