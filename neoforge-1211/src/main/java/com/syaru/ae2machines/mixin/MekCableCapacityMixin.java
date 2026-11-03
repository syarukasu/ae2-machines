package com.syaru.ae2machines.mixin;
import com.syaru.ae2machines.compat.mek.MekTransportParallel;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo
@Mixin(targets="mekanism.common.content.network.transmitter.UniversalCable",remap=false)
public abstract class MekCableCapacityMixin {
    @Inject(method="getCapacity",at=@At("RETURN"),cancellable=true)
    private void machines$capacity(CallbackInfoReturnable<Long> cir){cir.setReturnValue(MekTransportParallel.scale(cir.getReturnValueJ(),MekTransportParallel.lanes(this)));}
}
