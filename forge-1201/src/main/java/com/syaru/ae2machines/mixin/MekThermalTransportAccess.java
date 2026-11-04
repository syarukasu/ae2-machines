package com.syaru.ae2machines.mixin;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;
@Pseudo
@Mixin(targets="mekanism.common.tile.transmitter.TileEntityTransmitter",remap=false)
public interface MekThermalTransportAccess {
    @Accessor("forceUpdate") boolean machines$thermalForceUpdate();
}
