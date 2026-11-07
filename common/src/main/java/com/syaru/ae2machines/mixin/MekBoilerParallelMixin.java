package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.compat.mek.*;
import java.math.BigInteger;
import mekanism.api.Action;
import mekanism.common.capabilities.fluid.VariableCapacityFluidTank;
import mekanism.common.lib.multiblock.MultiblockData;
import mekanism.common.util.HeatUtils;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="mekanism.common.content.boiler.BoilerMultiblockData",remap=false)
public abstract class MekBoilerParallelMixin implements MekBoilerWater {
    @Unique private BigInteger machines$boiled=BigInteger.ZERO;
    @Override public BigInteger machines$boiledWater(){return machines$boiled;}
    @Unique private int machines$lanes(){return MekStructureParallel.lanes((MultiblockData)(Object)this);}
    @Inject(method="tick",at=@At("HEAD"))
    private void machines$begin(net.minecraft.world.level.Level world,CallbackInfoReturnable<Boolean> ci){machines$boiled=BigInteger.ZERO;}
    @ModifyArg(method="getHeatAvailable",at=@At(value="INVOKE",target="Ljava/lang/Math;min(DD)D"),index=1)
    private double machines$transferCeiling(double value){return value*MekStructureParallel.lanes((MultiblockData)(Object)this);}
    @Inject(method="getHeatAvailable",at=@At("RETURN"),cancellable=true)
    private void machines$unitHeat(CallbackInfoReturnable<Double> ci){ci.setReturnValue(ci.getReturnValueD()/machines$lanes());}
    @Redirect(method="tick",at=@At(value="INVOKE",target="Lmekanism/common/capabilities/fluid/VariableCapacityFluidTank;getFluidAmount()I"))
    private int machines$unitWater(VariableCapacityFluidTank tank){
        var q=BigInteger.valueOf(machines$lanes());
        return MekWideFluidTank.amount(tank).add(q).subtract(BigInteger.ONE).divide(q).min(MekFluidUnits.INT_MAX).intValueExact();
    }
    @Redirect(method="tick",at=@At(value="INVOKE",target="Lmekanism/common/capabilities/fluid/VariableCapacityFluidTank;shrinkStack(ILmekanism/api/Action;)I"))
    private int machines$boil(VariableCapacityFluidTank tank,int amount,Action action){
        if(machines$lanes()==1){int used=tank.shrinkStack(amount,action);machines$boiled=BigInteger.valueOf(used);return used;}
        var wide=(MekWideFluidTank)tank;
        machines$boiled=BigInteger.valueOf(amount).multiply(BigInteger.valueOf(machines$lanes()))
                .min(wide.machines$fluidAmount()).min(BigInteger.valueOf(com.syaru.ae2machines.platform.MekFluidAccess.steamNeeded((Object)this)));
        if(action.execute())wide.machines$fluidAmount(wide.machines$fluidAmount().subtract(machines$boiled));
        return amount;
    }
    @ModifyArg(method="tick",at=@At(value="INVOKE",target="Lmekanism/common/capabilities/heat/VariableHeatCapacitor;handleHeat(D)V"),index=0)
    private double machines$paidHeat(double nativeHeat){return machines$lanes()==1 || nativeHeat>=0?nativeHeat:-machines$boiled.doubleValue()*HeatUtils.getWaterThermalEnthalpy()/HeatUtils.getSteamEnergyEfficiency();}
}
