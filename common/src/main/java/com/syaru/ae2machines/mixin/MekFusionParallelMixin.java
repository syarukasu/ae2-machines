package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.compat.mek.*;
import mekanism.common.lib.multiblock.MultiblockData;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Pseudo
@Mixin(targets="mekanism.generators.common.content.fusion.FusionReactorMultiblockData",remap=false)
public abstract class MekFusionParallelMixin implements MekFusionState {
    @Shadow public double plasmaTemperature;
    @Shadow private double lastPlasmaTemperature;
    @Shadow private double lastCaseTemperature;
    @Override public double machines$plasmaTemperature(){return plasmaTemperature;}
    @Override public boolean machines$plasmaFits(double ratio){return Double.isFinite(plasmaTemperature*ratio) && Double.isFinite(lastPlasmaTemperature*ratio) && Double.isFinite(lastCaseTemperature*ratio);}
    @Override public void machines$rescalePlasma(double ratio){plasmaTemperature*=ratio;lastPlasmaTemperature*=ratio;lastCaseTemperature*=ratio;}
    @Override public void machines$restorePlasma(double canonical,int lanes){plasmaTemperature=canonical/lanes;lastPlasmaTemperature=plasmaTemperature;}
    @Unique private int machines$q(){return MekStructureParallel.lanes((MultiblockData)(Object)this);}
    @ModifyConstant(method={"addTemperatureFromEnergyInput","burnFuel","transferHeat"},constant=@Constant(doubleValue=100))
    private double machines$plasmaCapacity(double base){return base*machines$q();}
    @ModifyConstant(method="transferHeat",constant=@Constant(doubleValue=0.2))
    private double machines$caseConductivity(double base){return base*machines$q();}
    @Redirect(method="transferHeat",at=@At(value="INVOKE",target="Lmekanism/common/config/value/CachedDoubleValue;get()D",ordinal=0))
    private double machines$waterTransfer(mekanism.common.config.value.CachedDoubleValue value){return value.get()*machines$q();}
    @Redirect(method="transferHeat",at=@At(value="INVOKE",target="Lmekanism/common/config/value/CachedDoubleValue;get()D",ordinal=1))
    private double machines$airTransfer(mekanism.common.config.value.CachedDoubleValue value){return value.get()*machines$q();}
    @ModifyArg(method="burnFuel",at=@At(value="INVOKE",target="Lmekanism/api/math/MathUtils;clampToLong(D)J"),index=0)
    private double machines$burnRate(double base){return Math.min(base*machines$q(),com.syaru.ae2machines.platform.MekPassiveChemical.fusionFuel((MultiblockData)(Object)this));}
    @ModifyArg(method="injectFuel",at=@At(value="INVOKE",target="Ljava/lang/Math;min(JJ)J",ordinal=1),index=1)
    private long machines$injectionRate(long base){int q=machines$q();return base>Long.MAX_VALUE/q?Long.MAX_VALUE:base*q;}
}
