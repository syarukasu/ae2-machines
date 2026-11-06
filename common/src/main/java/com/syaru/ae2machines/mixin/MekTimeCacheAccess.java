package com.syaru.ae2machines.mixin;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.BooleanSupplier;
import mekanism.api.recipes.cache.CachedRecipe;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Pseudo
@Mixin(targets="mekanism.api.recipes.cache.CachedRecipe",remap=false)
public interface MekTimeCacheAccess {
    @Accessor("operatingTicksChanged") IntConsumer machines$timeProgressChanged();
    @Accessor("requiredTicks") IntSupplier machines$requiredTicks();
    @Accessor("canHolderFunction") BooleanSupplier machines$canProcess();
    @Invoker("calculateOperationsThisTick") void machines$quoteOperations(CachedRecipe.OperationTracker tracker);
}
