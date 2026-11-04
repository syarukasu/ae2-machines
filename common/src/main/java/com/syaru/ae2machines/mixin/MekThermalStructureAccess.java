package com.syaru.ae2machines.mixin;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;
@Pseudo @Mixin(targets="mekanism.common.lib.multiblock.Structure",remap=false)
public interface MekThermalStructureAccess {
    @Accessor("updateTimestamp") long machines$thermalUpdate();
    @Accessor("didUpdate") boolean machines$thermalUpdated();
}
