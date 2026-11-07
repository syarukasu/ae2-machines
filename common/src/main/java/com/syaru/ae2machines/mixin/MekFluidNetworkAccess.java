package com.syaru.ae2machines.mixin;

import mekanism.common.lib.transmitter.acceptor.NetworkAcceptorCache;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets="mekanism.common.lib.transmitter.DynamicNetwork",remap=false)
public interface MekFluidNetworkAccess {
    @Accessor("acceptorCache") NetworkAcceptorCache<?> machines$fluidAcceptors();
}
