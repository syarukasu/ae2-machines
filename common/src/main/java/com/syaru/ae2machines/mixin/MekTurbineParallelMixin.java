package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.compat.mek.MekStructureParallel;
import mekanism.common.lib.multiblock.MultiblockData;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.level.Level;

@Pseudo
@Mixin(targets="mekanism.generators.common.content.turbine.TurbineMultiblockData",remap=false)
public abstract class MekTurbineParallelMixin implements com.syaru.ae2machines.compat.mek.MekStructureFluids,com.syaru.ae2machines.compat.mek.MekTurbineWater {
    @Shadow public mekanism.api.fluid.IExtendedFluidTank ventTank;
    @Override public void machines$enrollExtraFluids(){
        var data=(MultiblockData)(Object)this;
        var fluids=data.getFluidTanks(null);
        if(!fluids.contains(ventTank))fluids.add(ventTank);
    }
    @Shadow public abstract long getSteamCapacity();
    @Unique private int machines$activeLanes=1;
    @Override public int machines$activeTurbines(){return machines$activeLanes;}
    @Inject(method="tick",at=@At("HEAD"))
    private void machines$admitSteam(Level world,CallbackInfoReturnable<Boolean> ci){
        var data=(MultiblockData)(Object)this;
        MekStructureParallel.prepare(data,world);
        long steam=com.syaru.ae2machines.platform.MekPassiveChemical.turbineSteam(data),capacity=getSteamCapacity();
        long occupied=steam==0 || capacity<=0?1:1+(steam-1)/capacity;
        machines$activeLanes=(int)Math.min(MekStructureParallel.lanes(data),occupied);
    }
    @ModifyArg(method="tick",at=@At(value="INVOKE",target="Ljava/lang/Math;min(DD)D",ordinal=0),index=0)
    private double machines$activeDispersers(double base){return base*machines$activeLanes;}
    @ModifyArg(method="tick",at=@At(value="INVOKE",target="Ljava/lang/Math;min(DD)D",ordinal=0),index=1)
    private double machines$activeVents(double base){return base*machines$activeLanes;}
    @ModifyArg(method={"getMaxProduction","getMaxFlowRate"},at=@At(value="INVOKE",target="Ljava/lang/Math;min(DD)D",ordinal=0),index=0)
    private double machines$dispersers(double base){return base*MekStructureParallel.lanes((MultiblockData)(Object)this);}
    @ModifyArg(method={"getMaxProduction","getMaxFlowRate"},at=@At(value="INVOKE",target="Ljava/lang/Math;min(DD)D",ordinal=0),index=1)
    private double machines$vents(double base){return base*MekStructureParallel.lanes((MultiblockData)(Object)this);}
    @Redirect(method="tick",at=@At(value="INVOKE",target="Lmekanism/generators/common/content/turbine/TurbineMultiblockData;getSteamCapacity()J"))
    private long machines$fillFraction(@Coerce Object ignored){long base=getSteamCapacity();int q=machines$activeLanes;return base>Long.MAX_VALUE/q?Long.MAX_VALUE:base*q;}
    @ModifyArg(method="tick",at=@At(value="INVOKE",target="Ljava/lang/Math;min(II)I",ordinal=1),index=1)
    private int machines$condensers(int base){return (int)Math.min(Integer.MAX_VALUE,(long)base*machines$activeLanes);}
}
