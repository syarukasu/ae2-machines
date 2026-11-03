package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.compat.mek.MekTransportParallel;
import mekanism.common.tier.TubeTier;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Pseudo
@Mixin(targets={"mekanism.common.content.network.transmitter.BoxedPressurizedTube","mekanism.common.content.network.transmitter.PressurizedTube"},remap=false)
public abstract class MekTubePullMixin {
    @Redirect(method="getAvailablePull",at=@At(value="INVOKE",target="Lmekanism/common/tier/TubeTier;getTubePullAmount()J"))
    private long machines$pull(TubeTier tier){return MekTransportParallel.scale(tier.getTubePullAmount(),MekTransportParallel.lanes(this));}
}
