package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.compat.mek.*;
import java.math.BigInteger;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.common.lib.multiblock.MultiblockData;
import net.minecraft.core.registries.BuiltInRegistries;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="mekanism.generators.common.content.turbine.TurbineMultiblockData",remap=false)
public abstract class MekTurbineFluidMixin {
    @Shadow public IExtendedFluidTank ventTank;
    @Shadow public long clientFlow;
    @Inject(method="tick",at=@At(value="INVOKE",target="Lmekanism/api/fluid/IExtendedFluidTank;setStack(Lnet/minecraftforge/fluids/FluidStack;)V",shift=At.Shift.AFTER))
    private void machines$condensed(net.minecraft.world.level.Level world,CallbackInfoReturnable<Boolean> ci){
        if(MekStructureParallel.lanes((MultiblockData)(Object)this)<=1 || ventTank.isEmpty())return;
        var fluid=BuiltInRegistries.FLUID.getKey(ventTank.getFluid().getFluid()).toString();
        if(!fluid.equals("minecraft:water") && !fluid.equals("mekanism_extras:polonium_containing_solution"))return;
        var limit=BigInteger.valueOf(((MekVirtualTank)ventTank).machines$nativeCapacity()).multiply(BigInteger.valueOf(((MekTurbineWater)this).machines$activeTurbines()));
        var amount=BigInteger.valueOf(clientFlow).min(limit);
        if(fluid.equals("mekanism_extras:polonium_containing_solution"))amount=amount.divide(BigInteger.valueOf(1000));
        ((MekWideFluidTank)ventTank).machines$fluidAmount(amount);
    }
}
