package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.compat.mek.*;
import mekanism.common.lib.multiblock.MultiblockData;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="mekanism.generators.common.content.fusion.FusionReactorCache",remap=false)
public abstract class MekFusionCacheMixin {
    @Shadow private double plasmaTemperature;
    @Inject(method="sync(Lmekanism/generators/common/content/fusion/FusionReactorMultiblockData;)V",at=@At("RETURN"))
    private void machines$canonical(@Coerce MultiblockData data,CallbackInfo ci){plasmaTemperature*=MekStructureParallel.lanes(data);}
    @Inject(method="apply(Lmekanism/generators/common/content/fusion/FusionReactorMultiblockData;)V",at=@At("RETURN"))
    private void machines$restore(@Coerce MultiblockData data,CallbackInfo ci){if(plasmaTemperature>=0)((MekFusionState)data).machines$restorePlasma(plasmaTemperature,MekStructureParallel.lanes(data));}
}
