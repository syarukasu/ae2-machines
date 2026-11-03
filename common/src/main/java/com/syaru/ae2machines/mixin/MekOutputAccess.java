package com.syaru.ae2machines.mixin;

import mekanism.api.inventory.IInventorySlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Invoker;

@Pseudo
@Mixin(targets="mekanism.api.recipes.outputs.OutputHelper",remap=false)
public interface MekOutputAccess {
    @Invoker("handleOutput") static void machines$insert(IInventorySlot slot,ItemStack stack,int operations){throw new AssertionError();}
}
