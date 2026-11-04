package com.syaru.ae2machines.mixin;
import java.util.UUID;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;
@Pseudo @Mixin(targets="mekanism.common.tile.prefab.TileEntityMultiblock",remap=false)
public interface MekThermalTileAccess {
    @Accessor("prevStructure") boolean machines$thermalFormed();
    @Accessor("cachedID") UUID machines$thermalId();
}
