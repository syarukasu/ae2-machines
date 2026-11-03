package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.compat.mek.MekTransportParallel;
import mekanism.common.tier.TransporterTier;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Pseudo
@Mixin(targets="mekanism.common.content.network.transmitter.LogisticalTransporterBase",remap=false)
public abstract class MekItemPullMixin {
    @Redirect(method="onUpdateServer",at=@At(value="INVOKE",target="Lmekanism/common/tier/TransporterTier;getPullAmount()I"))
    private int machines$pull(TransporterTier tier){return (int)Math.min(Integer.MAX_VALUE,(long)tier.getPullAmount()*MekTransportParallel.lanes(this));}
}
