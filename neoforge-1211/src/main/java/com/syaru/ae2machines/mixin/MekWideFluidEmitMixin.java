package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.compat.mek.*;
import com.syaru.ae2machines.platform.MekFluidAccess;
import java.math.BigInteger;
import java.util.Collection;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.common.util.FluidUtils;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="mekanism.common.util.FluidUtils",remap=false)
public abstract class MekWideFluidEmitMixin {
    @Inject(method="emit(Ljava/util/Collection;Lmekanism/api/fluid/IExtendedFluidTank;I)V",at=@At("HEAD"),cancellable=true)
    private static void machines$emit(Collection<BlockCapabilityCache<IFluidHandler,Direction>> targets,IExtendedFluidTank tank,int maximum,CallbackInfo ci){
        if(!(tank instanceof MekWideFluidTank wide) || !wide.machines$wideFluid() || !MekFluidUnits.unit().equals(BigInteger.ONE))return;
        long base=((MekVirtualTank)tank).machines$nativeCapacity();if(base<=0)return;
        var q=wide.machines$fluidCapacity().divide(BigInteger.valueOf(base));
        var offered=wide.machines$fluidAmount().min(BigInteger.valueOf(maximum).multiply(q));
        var unit=MekFluidUnits.covering(offered);if(unit.equals(BigInteger.ONE) || !MekFluidAccess.pooledTargets(targets))return;
        MekFluidUnits.call(unit,()->{FluidUtils.emit(targets,tank,MekFluidUnits.view(offered));return null;});ci.cancel();
    }
}
