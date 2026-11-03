package com.syaru.ae2machines.mixin;

import java.util.function.*;
import mekanism.api.recipes.cache.CachedRecipe;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets="mekanism.api.recipes.cache.CachedRecipe",remap=false)
public interface MekCachedOperationsAccess {
    @Accessor("baselineMaxOperations") IntSupplier machines$baseline();
    @Accessor("postProcessOperations") Consumer<CachedRecipe.OperationTracker> machines$postProcess();
    @Accessor("operatingTicks") int machines$progress();
}
