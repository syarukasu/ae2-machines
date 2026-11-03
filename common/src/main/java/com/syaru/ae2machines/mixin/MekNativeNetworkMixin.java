package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.compat.mek.MekNativeNetworks;
import mekanism.common.lib.transmitter.DynamicNetwork;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "mekanism.common.lib.transmitter.DynamicNetwork", remap = false)
public abstract class MekNativeNetworkMixin {
    @Inject(method = {"commit", "register"}, at = @At("RETURN"))
    private void machines$index(CallbackInfo ci) { MekNativeNetworks.committed((DynamicNetwork<?, ?, ?>)(Object)this); }
    @Inject(method = "deregister", at = @At("HEAD"))
    private void machines$remove(CallbackInfo ci) { MekNativeNetworks.removed((DynamicNetwork<?, ?, ?>)(Object)this); }
}
