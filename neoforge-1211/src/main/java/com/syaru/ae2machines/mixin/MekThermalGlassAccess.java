package com.syaru.ae2machines.mixin;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;
@Pseudo @Mixin(targets="mekanism.common.tile.prefab.TileEntityStructuralMultiblock",remap=false)
public interface MekThermalGlassAccess {
    @Accessor("hasFormedMultiblock") boolean machines$thermalFormed();
}
