package com.syaru.ae2machines.mixin;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets="mekanism.common.tile.multiblock.TileEntitySPSCasing",remap=false)
public interface MekThermalSpsTileAccess {
    @Accessor("prevActive") boolean machines$previousSpsActive();
}
