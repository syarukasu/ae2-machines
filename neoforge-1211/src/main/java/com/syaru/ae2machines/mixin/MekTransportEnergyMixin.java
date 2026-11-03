package com.syaru.ae2machines.mixin;
import com.syaru.ae2machines.compat.mek.*;
import java.lang.ref.WeakReference;
import java.math.BigInteger;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo
@Mixin(targets="mekanism.common.capabilities.energy.BasicEnergyContainer",remap=false)
public abstract class MekTransportEnergyMixin implements MekTransportEnergy {
    @Shadow @Final private long maxEnergy;
    @Unique private WeakReference<BlockEntity> machines$transport;
    @Override public void machines$transportOwner(BlockEntity owner){machines$transport=new WeakReference<>(owner);}
    @Override public BigInteger machines$transportBaseEnergy(){return BigInteger.valueOf(maxEnergy).multiply(BigInteger.valueOf(10000));}
    @Inject(method="getMaxEnergy",at=@At("RETURN"),cancellable=true)
    private void machines$capacity(CallbackInfoReturnable<Long> cir){if(machines$transport!=null)cir.setReturnValue(MekTransportParallel.scale(cir.getReturnValueJ(),MekWorldParallel.lanes(machines$transport.get())));}
}
