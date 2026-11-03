package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.compat.mek.MekStructureParallel;
import mekanism.common.lib.multiblock.MultiblockData;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Pseudo
@Mixin(targets="mekanism.common.content.boiler.BoilerMultiblockData",remap=false)
public abstract class MekBoilerParallelMixin {
    @ModifyArg(method="getHeatAvailable",at=@At(value="INVOKE",target="Ljava/lang/Math;min(DD)D"),index=1)
    private double machines$transferCeiling(double value){return value*MekStructureParallel.lanes((MultiblockData)(Object)this);}
}
