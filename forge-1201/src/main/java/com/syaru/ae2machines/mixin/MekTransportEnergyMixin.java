package com.syaru.ae2machines.mixin;
import com.syaru.ae2machines.compat.mek.*;
import com.syaru.ae2machines.platform.MekanismAdapter;
import java.lang.ref.WeakReference;
import java.math.BigInteger;
import mekanism.api.math.FloatingLong;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo
@Mixin(targets="mekanism.common.capabilities.energy.BasicEnergyContainer",remap=false)
public abstract class MekTransportEnergyMixin implements MekTransportEnergy {
    @Shadow @Final private FloatingLong maxEnergy;
    @Unique private WeakReference<BlockEntity> machines$transport;
    @Override public void machines$transportOwner(BlockEntity owner){machines$transport=new WeakReference<>(owner);}
    @Override public BigInteger machines$transportBaseEnergy(){return MekanismAdapter.units(maxEnergy.toString());}
    @Inject(method="getMaxEnergy",at=@At("RETURN"),cancellable=true)
    private void machines$capacity(CallbackInfoReturnable<FloatingLong> cir){if(machines$transport!=null){int q=MekWorldParallel.lanes(machines$transport.get());if(q>1)cir.setReturnValue(cir.getReturnValue().multiply(q));}}
}
