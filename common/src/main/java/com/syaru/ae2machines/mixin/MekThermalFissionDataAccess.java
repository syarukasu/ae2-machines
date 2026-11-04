package com.syaru.ae2machines.mixin;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;
@Pseudo
@Mixin(targets="mekanism.generators.common.content.fission.FissionReactorMultiblockData",remap=false)
public interface MekThermalFissionDataAccess {
    @Accessor("lastBurnRate") double machines$thermalBurnRate();
}
