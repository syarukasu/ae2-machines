package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.compat.mek.*;
import com.syaru.ae2machines.platform.MekFluidAccess;
import java.math.BigInteger;
import java.util.Collection;
import mekanism.api.*;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.common.lib.multiblock.MultiblockData;
import mekanism.common.util.FluidUtils;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Pseudo
@Mixin(targets="mekanism.generators.common.content.turbine.TurbineMultiblockData",remap=false)
public abstract class MekTurbineFluidMixin {
    @Shadow public IExtendedFluidTank ventTank;
    @Shadow public long clientFlow;
    @Redirect(method="tick",at=@At(value="INVOKE",target="Lmekanism/api/fluid/IExtendedFluidTank;insert(Lnet/neoforged/neoforge/fluids/FluidStack;Lmekanism/api/Action;Lmekanism/api/AutomationType;)Lnet/neoforged/neoforge/fluids/FluidStack;"))
    private FluidStack machines$condensed(IExtendedFluidTank tank,FluidStack water,Action action,AutomationType automation){
        if(MekStructureParallel.lanes((MultiblockData)(Object)this)<=1)return tank.insert(water,action,automation);
        var wide=(MekWideFluidTank)tank;
        var limit=BigInteger.valueOf(((MekVirtualTank)tank).machines$nativeCapacity()).multiply(BigInteger.valueOf(((MekTurbineWater)this).machines$activeTurbines()));
        var made=BigInteger.valueOf(clientFlow).min(limit).min(wide.machines$fluidCapacity().subtract(wide.machines$fluidAmount()).max(BigInteger.ZERO));
        if(made.signum()>0 && tank.insert(water.copyWithAmount(1),Action.SIMULATE,automation).isEmpty() && action.execute())
            MekFluidAccess.set(tank,water,wide.machines$fluidAmount().add(made));
        return FluidStack.EMPTY;
    }
    @Redirect(method="tick",at=@At(value="INVOKE",target="Lmekanism/common/util/FluidUtils;emit(Ljava/util/Collection;Lnet/neoforged/neoforge/fluids/FluidStack;)I"))
    private int machines$emit(Collection<BlockCapabilityCache<IFluidHandler,Direction>> targets,FluidStack water){
        var amount=MekWideFluidTank.amount(ventTank);var unit=MekFluidUnits.covering(amount);
        if(unit.equals(BigInteger.ONE) || !MekFluidAccess.pooledTargets(targets))return FluidUtils.emit(targets,water);
        int accepted=MekFluidUnits.call(unit,()->FluidUtils.emit(targets,water.copyWithAmount(MekFluidUnits.view(amount))));
        ((MekWideFluidTank)ventTank).machines$fluidAmount(amount.subtract(BigInteger.valueOf(accepted).multiply(unit)));
        return 0;
    }
}
