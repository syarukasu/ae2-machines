package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.compat.mek.MekTransportParallel;
import mekanism.common.lib.inventory.TransitRequest;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Pseudo
@Mixin(targets="mekanism.common.content.network.transmitter.LogisticalTransporterBase",remap=false)
public abstract class MekItemExtractMixin {
    @Redirect(method="onUpdateServer",at=@At(value="INVOKE",target="Lmekanism/common/lib/inventory/TransitRequest;anyItem(Lnet/neoforged/neoforge/items/IItemHandler;I)Lmekanism/common/lib/inventory/TransitRequest;"))
    private TransitRequest machines$offer(IItemHandler inventory,int amount){return MekTransportParallel.itemPull(this,()->TransitRequest.anyItem(inventory,amount));}
    @Redirect(method="onUpdateServer",at=@At(value="INVOKE",target="Lmekanism/common/lib/inventory/TransitRequest$TransitResponse;useAll()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack machines$take(TransitRequest.TransitResponse response){return MekTransportParallel.itemPull(this,response::useAll);}
}
