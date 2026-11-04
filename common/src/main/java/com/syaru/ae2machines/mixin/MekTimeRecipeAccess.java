package com.syaru.ae2machines.mixin;

import mekanism.common.recipe.lookup.monitor.RecipeCacheLookupMonitor;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets="mekanism.common.tile.prefab.TileEntityRecipeMachine",remap=false)
public interface MekTimeRecipeAccess {
    @Accessor("recipeCacheLookupMonitor") RecipeCacheLookupMonitor<?> machines$timeMonitor();
}
