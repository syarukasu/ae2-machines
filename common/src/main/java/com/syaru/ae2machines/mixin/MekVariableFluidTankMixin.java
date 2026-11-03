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
    @Override public void machines$owner(BlockEntity owner){machines$owner=new WeakReference<>(owner);}
    @Override public long machines$nativeCapacity(){return capacity.getAsInt();}
    @Inject(method="getCapacity",at=@At("RETURN"),cancellable=true)
    private void machines$capacity(CallbackInfoReturnable<Integer> cir) {
        if(machines$owner!=null)cir.setReturnValue(MekWorldParallel.slotLimit(machines$owner.get(),cir.getReturnValueI()));
    }
}
