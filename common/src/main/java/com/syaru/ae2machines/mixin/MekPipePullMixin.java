package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.compat.mek.MekTransportParallel;
import mekanism.common.tier.PipeTier;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Pseudo
@Mixin(targets="mekanism.common.content.network.transmitter.MechanicalPipe",remap=false)
public abstract class MekPipePullMixin {
    @Redirect(method="getAvailablePull",at=@At(value="INVOKE",target="Lmekanism/common/tier/PipeTier;getPipePullAmount()I"))
    private int machines$pull(PipeTier tier){return (int)Math.min(Integer.MAX_VALUE,(long)tier.getPipePullAmount()*MekTransportParallel.lanes(this));}
}
