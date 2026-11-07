package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.compat.mek.*;
import java.lang.ref.WeakReference;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="mekanism.common.capabilities.fluid.BasicFluidTank",remap=false)
public abstract class MekVirtualFluidTankMixin implements MekVirtualTank {
    @Shadow @Final private int capacity;
    @Unique private WeakReference<BlockEntity> machines$owner;
    @Override public void machines$owner(BlockEntity owner){machines$owner=new WeakReference<>(owner);((MekWideFluidTank)this).machines$fluidCapacity(()->java.math.BigInteger.valueOf(machines$nativeCapacity()).multiply(java.math.BigInteger.valueOf(MekWorldParallel.lanes(machines$owner.get()))));}
    @Override public long machines$nativeCapacity(){return capacity;}
    @Inject(method="getCapacity",at=@At("RETURN"),cancellable=true)
    private void machines$capacity(CallbackInfoReturnable<Integer> cir) {
        if(((MekWideFluidTank)this).machines$wideFluid())cir.setReturnValue(MekFluidUnits.view(((MekWideFluidTank)this).machines$fluidCapacity()));
    }
}
