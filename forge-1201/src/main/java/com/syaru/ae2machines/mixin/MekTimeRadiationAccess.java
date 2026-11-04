package com.syaru.ae2machines.mixin;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;
@Pseudo
@Mixin(targets="mekanism.common.lib.radiation.RadiationManager",remap=false)
public interface MekTimeRadiationAccess {
    @Accessor("meltdowns") Map<ResourceLocation,List<?>> machines$timeMeltdowns();
}
