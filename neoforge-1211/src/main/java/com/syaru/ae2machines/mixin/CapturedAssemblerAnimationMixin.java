package com.syaru.ae2machines.mixin;

import appeng.blockentity.crafting.MolecularAssemblerBlockEntity;
import com.syaru.ae2machines.runtime.CapturedAssembler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** There is no assembler at the captured source position to animate. Crafting events stay native. */
@Mixin(value=MolecularAssemblerBlockEntity.class,remap=false)
public abstract class CapturedAssemblerAnimationMixin {
    @Redirect(method="tickingRequest",at=@At(value="INVOKE",target="Lnet/neoforged/neoforge/network/PacketDistributor;sendToPlayersNear(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;DDDDLnet/minecraft/network/protocol/common/custom/CustomPacketPayload;[Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V"))
    private void machines$animation(ServerLevel level,ServerPlayer except,double x,double y,double z,double range,CustomPacketPayload packet,CustomPacketPayload[] extra) {
        if(!((Object)this instanceof CapturedAssembler))PacketDistributor.sendToPlayersNear(level,except,x,y,z,range,packet,extra);
    }
}
