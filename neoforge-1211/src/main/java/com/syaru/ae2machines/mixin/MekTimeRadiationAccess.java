package com.syaru.ae2machines.mixin;
import java.util.List;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;
@Pseudo
@Mixin(targets="mekanism.common.lib.radiation.MeltdownLevelData",remap=false)
public interface MekTimeRadiationAccess {
    @Accessor("meltdowns") List<?> machines$timeMeltdowns();
}
