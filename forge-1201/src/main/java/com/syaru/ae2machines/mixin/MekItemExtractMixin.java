package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.compat.mek.MekTransportParallel;
import mekanism.common.lib.inventory.TransitRequest;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Pseudo
@Mixin(targets="mekanism.common.content.network.transmitter.LogisticalTransporterBase",remap=false)
public abstract class MekItemExtractMixin {
    @Redirect(method="onUpdateServer",at=@At(value="INVOKE",target="Lmekanism/common/lib/inventory/TransitRequest;anyItem(Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/core/Direction;I)Lmekanism/common/lib/inventory/TransitRequest;"))
    private TransitRequest machines$offer(BlockEntity tile,Direction side,int amount){return MekTransportParallel.itemPull(this,()->TransitRequest.anyItem(tile,side,amount));}
    @Redirect(method="onUpdateServer",at=@At(value="INVOKE",target="Lmekanism/common/lib/inventory/TransitRequest$TransitResponse;useAll()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack machines$take(TransitRequest.TransitResponse response){return MekTransportParallel.itemPull(this,response::useAll);}
}
