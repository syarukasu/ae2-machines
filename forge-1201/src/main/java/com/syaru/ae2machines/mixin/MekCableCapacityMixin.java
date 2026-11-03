package com.syaru.ae2machines.mixin;
import com.syaru.ae2machines.compat.mek.MekTransportParallel;
import mekanism.api.math.FloatingLong;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo
@Mixin(targets="mekanism.common.content.network.transmitter.UniversalCable",remap=false)
public abstract class MekCableCapacityMixin {
    @Inject(method="getCapacityAsFloatingLong",at=@At("RETURN"),cancellable=true)
    private void machines$capacity(CallbackInfoReturnable<FloatingLong> cir){int q=MekTransportParallel.lanes(this);if(q>1)cir.setReturnValue(cir.getReturnValue().multiply(q));}
}
