package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.compat.mek.MekNativeNetworks;
import mekanism.common.lib.transmitter.DynamicNetwork;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Pseudo
@Mixin(targets = "mekanism.common.lib.transmitter.TransmitterNetworkRegistry", remap = false)
public abstract class MekNetworkClockMixin {
    @Redirect(method = "onTick", at = @At(value = "INVOKE", target = "Lmekanism/common/lib/transmitter/DynamicNetwork;onUpdate()V"))
    private static void machines$nativeClock(DynamicNetwork<?, ?, ?> network) { MekNativeNetworks.globalTick(network); }
}
