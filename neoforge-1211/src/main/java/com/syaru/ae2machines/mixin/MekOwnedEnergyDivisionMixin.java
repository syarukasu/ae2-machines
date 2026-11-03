package com.syaru.ae2machines.mixin;
import org.spongepowered.asm.mixin.*;
/** Platform counterpart: NeoForge Mek already computes availability with integer joules. */
@Pseudo @Mixin(targets="mekanism.api.recipes.cache.CachedRecipe",remap=false)
public abstract class MekOwnedEnergyDivisionMixin {}
