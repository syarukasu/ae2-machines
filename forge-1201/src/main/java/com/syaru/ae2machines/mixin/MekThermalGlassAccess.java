package com.syaru.ae2machines.mixin;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;
/** Forge's periodic structural-glass state; NeoForge uses formation callbacks. */
@Pseudo @Mixin(targets="mekanism.common.tile.prefab.TileEntityStructuralMultiblock",remap=false)
public interface MekThermalGlassAccess {
    @Accessor("clientActiveMultiblock") String machines$thermalActive();
}
