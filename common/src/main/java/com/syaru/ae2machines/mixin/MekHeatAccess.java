package com.syaru.ae2machines.mixin;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets="mekanism.common.capabilities.heat.BasicHeatCapacitor",remap=false)
public interface MekHeatAccess {
    @Accessor("heatToHandle") double ae2machines$pendingHeat();
    @Accessor("heatToHandle") void ae2machines$pendingHeat(double value);
}
