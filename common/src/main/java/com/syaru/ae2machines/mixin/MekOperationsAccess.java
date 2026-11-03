package com.syaru.ae2machines.mixin;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets="mekanism.api.recipes.cache.CachedRecipe$OperationTracker",remap=false)
public interface MekOperationsAccess {
    @Accessor("currentMax") int machines$currentMax();
    @Accessor("maxForEnergy") int machines$maxForEnergy();
}
