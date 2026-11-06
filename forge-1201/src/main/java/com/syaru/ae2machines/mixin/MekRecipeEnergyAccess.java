package com.syaru.ae2machines.mixin;

import mekanism.api.math.FloatingLongSupplier;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets="mekanism.api.recipes.cache.CachedRecipe",remap=false)
public interface MekRecipeEnergyAccess {
    @Accessor("perTickEnergy") FloatingLongSupplier machines$recipeEnergy();
}
