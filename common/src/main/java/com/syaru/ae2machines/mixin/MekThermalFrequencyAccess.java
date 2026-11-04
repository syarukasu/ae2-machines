package com.syaru.ae2machines.mixin;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;
@Pseudo @Mixin(targets="mekanism.common.lib.frequency.TileComponentFrequency",remap=false)
public interface MekThermalFrequencyAccess {
    @Accessor("needsSave") boolean machines$thermalSave();
    @Accessor("needsNotify") boolean machines$thermalNotify();
}
