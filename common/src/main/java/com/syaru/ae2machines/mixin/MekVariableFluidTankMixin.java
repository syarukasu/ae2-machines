package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.compat.mek.*;
import java.lang.ref.WeakReference;
import java.util.function.IntSupplier;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="mekanism.common.capabilities.fluid.VariableCapacityFluidTank",remap=false)
public abstract class MekVariableFluidTankMixin implements MekVirtualTank {
    @Shadow @Final private IntSupplier capacity;
    @Unique private WeakReference<BlockEntity> machines$owner;
    @Override public void machines$owner(BlockEntity owner){machines$owner=new WeakReference<>(owner);((MekWideFluidTank)this).machines$fluidCapacity(()->java.math.BigInteger.valueOf(machines$nativeCapacity()).multiply(java.math.BigInteger.valueOf(MekWorldParallel.lanes(machines$owner.get()))));}
    @Override public long machines$nativeCapacity(){return capacity.getAsInt();}
    @Inject(method="getCapacity",at=@At("RETURN"),cancellable=true)
    private void machines$capacity(CallbackInfoReturnable<Integer> cir) {
        if(((MekWideFluidTank)this).machines$wideFluid())cir.setReturnValue(MekFluidUnits.view(((MekWideFluidTank)this).machines$fluidCapacity()));
    }
    @Inject(method="setStackSize",at=@At("HEAD"),cancellable=true)
    private void machines$size(int amount,mekanism.api.Action action,CallbackInfoReturnable<Integer> ci){
        var wide=(MekWideFluidTank)this;if(!wide.machines$wideFluid() || wide.machines$fluidAmount().signum()==0)return;
        int accepted=Math.max(0,Math.min(amount,MekFluidUnits.view(wide.machines$fluidCapacity())));
        if(action.execute())wide.machines$fluidAmount(MekFluidUnits.exact(accepted));ci.setReturnValue(accepted);
    }
}
