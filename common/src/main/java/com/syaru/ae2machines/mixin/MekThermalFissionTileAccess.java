package com.syaru.ae2machines.mixin;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;
@Pseudo
@Mixin(targets="mekanism.generators.common.tile.fission.TileEntityFissionReactorCasing",remap=false)
public interface MekThermalFissionTileAccess {
    @Accessor("prevBurning") boolean machines$thermalPreviousBurning();
}
