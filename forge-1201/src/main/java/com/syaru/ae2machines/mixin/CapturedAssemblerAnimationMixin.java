package com.syaru.ae2machines.mixin;

import appeng.blockentity.crafting.MolecularAssemblerBlockEntity;
import appeng.core.sync.BasePacket;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.network.TargetPoint;
import com.syaru.ae2machines.runtime.CapturedAssembler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** There is no assembler at the captured source position to animate. Crafting events stay native. */
@Mixin(value=MolecularAssemblerBlockEntity.class,remap=false)
public abstract class CapturedAssemblerAnimationMixin {
    @Redirect(method="tickingRequest",at=@At(value="INVOKE",target="Lappeng/core/sync/network/NetworkHandler;sendToAllAround(Lappeng/core/sync/BasePacket;Lappeng/core/sync/network/TargetPoint;)V"))
    private void machines$animation(NetworkHandler network,BasePacket packet,TargetPoint point) {
        if(!((Object)this instanceof CapturedAssembler))network.sendToAllAround(packet,point);
    }
}
