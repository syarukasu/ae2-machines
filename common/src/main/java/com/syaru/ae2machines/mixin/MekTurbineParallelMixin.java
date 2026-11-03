package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.compat.mek.MekStructureParallel;
import mekanism.common.lib.multiblock.MultiblockData;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Pseudo
@Mixin(targets="mekanism.generators.common.content.turbine.TurbineMultiblockData",remap=false)
public abstract class MekTurbineParallelMixin implements com.syaru.ae2machines.compat.mek.MekStructureFluids {
    @Shadow public mekanism.api.fluid.IExtendedFluidTank ventTank;
    @Override public void machines$enrollExtraFluids(){
        var data=(MultiblockData)(Object)this;
        var fluids=data.getFluidTanks(null);
        if(!fluids.contains(ventTank))fluids.add(ventTank);
    }
    @Shadow public abstract long getSteamCapacity();
    @ModifyArg(method={"tick","getMaxProduction","getMaxFlowRate"},at=@At(value="INVOKE",target="Ljava/lang/Math;min(DD)D",ordinal=0),index=0)
    private double machines$dispersers(double base){return base*MekStructureParallel.lanes((MultiblockData)(Object)this);}
    @ModifyArg(method={"tick","getMaxProduction","getMaxFlowRate"},at=@At(value="INVOKE",target="Ljava/lang/Math;min(DD)D",ordinal=0),index=1)
    private double machines$vents(double base){return base*MekStructureParallel.lanes((MultiblockData)(Object)this);}
    @Redirect(method="tick",at=@At(value="INVOKE",target="Lmekanism/generators/common/content/turbine/TurbineMultiblockData;getSteamCapacity()J"))
    private long machines$fillFraction(@Coerce Object ignored){long base=getSteamCapacity();int q=MekStructureParallel.lanes((MultiblockData)(Object)this);return base>Long.MAX_VALUE/q?Long.MAX_VALUE:base*q;}
    @ModifyArg(method="tick",at=@At(value="INVOKE",target="Ljava/lang/Math;min(II)I",ordinal=1),index=1)
    private int machines$condensers(int base){return (int)Math.min(Integer.MAX_VALUE,(long)base*MekStructureParallel.lanes((MultiblockData)(Object)this));}
}
