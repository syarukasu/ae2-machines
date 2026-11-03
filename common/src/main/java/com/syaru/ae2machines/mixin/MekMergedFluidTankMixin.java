package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.compat.mek.MekVirtualTank;
import mekanism.api.fluid.IExtendedFluidTank;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;

/** The wrapper retains the native fluid/chemical exclusion checks. */
@Pseudo
@Mixin(targets="mekanism.common.capabilities.fluid.FluidTankWrapper",remap=false)
public abstract class MekMergedFluidTankMixin implements MekVirtualTank {
    @Shadow @Final private IExtendedFluidTank internal;
    @Override public void machines$owner(BlockEntity owner){((MekVirtualTank)internal).machines$owner(owner);}
    @Override public long machines$nativeCapacity(){return ((MekVirtualTank)internal).machines$nativeCapacity();}
}
