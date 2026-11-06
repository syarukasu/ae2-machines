package com.syaru.ae2machines.mixin;

import java.util.function.LongSupplier;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets="mekanism.api.recipes.cache.CachedRecipe",remap=false)
public interface MekRecipeEnergyAccess {
    @Accessor("perTickEnergy") LongSupplier machines$recipeEnergy();
}
