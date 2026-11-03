package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.compat.mek.MekStructureParallel;
import mekanism.common.lib.multiblock.MultiblockData;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Pseudo
@Mixin(targets="mekanism.generators.common.content.fission.FissionReactorMultiblockData",remap=false)
public abstract class MekFissionParallelMixin {
    @ModifyArg(method="burnFuel",at=@At(value="INVOKE",target="Ljava/lang/Math;min(DD)D",ordinal=0),index=0)
    private double machines$configuredRate(double base){return base*MekStructureParallel.lanes((MultiblockData)(Object)this);}
    @ModifyArg(method="burnFuel",at=@At(value="INVOKE",target="Ljava/lang/Math;min(DD)D",ordinal=1),index=1)
    private double machines$assemblyRate(double base){return base*MekStructureParallel.lanes((MultiblockData)(Object)this);}
}
