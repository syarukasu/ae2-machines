package com.syaru.ae2machines.mixin;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets="mekanism.common.lib.transmitter.DynamicBufferedNetwork",remap=false)
public interface MekSaveSharesAccess {
    @Accessor("lastSaveShareWriteTime") void machines$saveShareTime(long value);
}
