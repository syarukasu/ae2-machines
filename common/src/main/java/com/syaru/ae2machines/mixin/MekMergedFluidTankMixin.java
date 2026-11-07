package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.compat.mek.MekVirtualTank;
import mekanism.api.fluid.IExtendedFluidTank;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;

/** The wrapper retains the native fluid/chemical exclusion checks. */
@Pseudo
@Mixin(targets="mekanism.common.capabilities.fluid.FluidTankWrapper",remap=false)
public abstract class MekMergedFluidTankMixin implements MekVirtualTank,com.syaru.ae2machines.compat.mek.MekWideFluidTank {
    @Shadow @Final private IExtendedFluidTank internal;
    @Override public void machines$owner(BlockEntity owner){((MekVirtualTank)internal).machines$owner(owner);}
    @Override public long machines$nativeCapacity(){return ((MekVirtualTank)internal).machines$nativeCapacity();}
    @Override public java.math.BigInteger machines$fluidAmount(){return ((com.syaru.ae2machines.compat.mek.MekWideFluidTank)internal).machines$fluidAmount();}
    @Override public java.math.BigInteger machines$fluidCapacity(){return ((com.syaru.ae2machines.compat.mek.MekWideFluidTank)internal).machines$fluidCapacity();}
    @Override public void machines$fluidAmount(java.math.BigInteger amount){((com.syaru.ae2machines.compat.mek.MekWideFluidTank)internal).machines$fluidAmount(amount);}
    @Override public void machines$fluidCapacity(java.util.function.Supplier<java.math.BigInteger> capacity){((com.syaru.ae2machines.compat.mek.MekWideFluidTank)internal).machines$fluidCapacity(capacity);}
    @Override public boolean machines$wideFluid(){return ((com.syaru.ae2machines.compat.mek.MekWideFluidTank)internal).machines$wideFluid();}
}
