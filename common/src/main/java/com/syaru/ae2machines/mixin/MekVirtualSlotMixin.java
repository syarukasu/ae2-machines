package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.compat.mek.MekVirtualSlot;
import com.syaru.ae2machines.compat.mek.MekWorldParallel;
import java.lang.ref.WeakReference;
import mekanism.common.tile.prefab.TileEntityConfigurableMachine;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="mekanism.common.inventory.slot.BasicInventorySlot",remap=false)
public abstract class MekVirtualSlotMixin implements MekVirtualSlot {
    @Shadow @Final private int limit;
    @Shadow protected boolean obeyStackLimit;
    @Unique private WeakReference<TileEntityConfigurableMachine> machines$owner;
    @Override public void machines$owner(TileEntityConfigurableMachine machine){machines$owner=new WeakReference<>(machine);}
    @Override public int machines$nativeLimit(ItemStack stack){return obeyStackLimit && !stack.isEmpty()?Math.min(limit,stack.getMaxStackSize()):limit;}
    @Override public boolean machines$parallel(){return machines$owner!=null && MekWorldParallel.lanes(machines$owner.get())>1;}
    @Override public boolean machines$captured(){
        var owner=machines$owner==null?null:machines$owner.get();
        return owner!=null && owner.getLevel() instanceof net.minecraft.server.level.ServerLevel level
                && com.syaru.ae2machines.world.NativeCellWorlds.owns(level);
    }
    @Inject(method="getLimit",at=@At("RETURN"),cancellable=true)
    private void machines$lanes(ItemStack stack,CallbackInfoReturnable<Integer> cir) {
        if(machines$owner!=null)cir.setReturnValue(MekWorldParallel.slotLimit(machines$owner.get(),cir.getReturnValueI()));
    }
    @Redirect(method="extractItem",at=@At(value="INVOKE",target="Lnet/minecraft/world/item/ItemStack;getMaxStackSize()I",remap=true))
    private int machines$transportWindow(ItemStack stack){
        return machines$owner==null?stack.getMaxStackSize():com.syaru.ae2machines.compat.mek.MekTransportParallel.itemStackLimit(machines$owner.get(),stack.getMaxStackSize());
    }
}
