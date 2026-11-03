package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.compat.mek.*;
import java.lang.ref.WeakReference;
import java.math.BigInteger;
import java.util.function.LongSupplier;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="mekanism.common.capabilities.energy.VariableCapacityEnergyContainer",remap=false)
public abstract class MekStructureEnergyMixin implements MekStructureEnergy {
    @Shadow @Final private LongSupplier maxEnergy;
    @Unique private WeakReference<BlockEntity> machines$owner;
    @Override public void machines$structureOwner(BlockEntity owner){machines$owner=new WeakReference<>(owner);}
    @Override public BigInteger machines$structureBaseEnergy(){return BigInteger.valueOf(maxEnergy.getAsLong()).multiply(BigInteger.valueOf(10000));}
    @Inject(method="getMaxEnergy",at=@At("RETURN"),cancellable=true)
    private void machines$capacity(CallbackInfoReturnable<Long> cir){
        if(machines$owner==null)return;int q=MekWorldParallel.lanes(machines$owner.get());long base=cir.getReturnValueJ();
        cir.setReturnValue(base>Long.MAX_VALUE/q?Long.MAX_VALUE:base*q);
    }
}
