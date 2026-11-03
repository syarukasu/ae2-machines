package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.compat.mek.MekVirtualSlot;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.recipes.cache.CachedRecipe.OperationTracker;
import mekanism.api.recipes.cache.CachedRecipe.OperationTracker.RecipeError;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Probe the real owned slot rather than a vanilla stack; native output insertion is unchanged. */
@Pseudo
@Mixin(targets="mekanism.api.recipes.outputs.OutputHelper",remap=false)
public abstract class MekParallelOutputMixin {
    @Inject(method="calculateOperationsCanSupport(Lmekanism/api/recipes/cache/CachedRecipe$OperationTracker;Lmekanism/api/recipes/cache/CachedRecipe$OperationTracker$RecipeError;Lmekanism/api/inventory/IInventorySlot;Lnet/minecraft/world/item/ItemStack;)V",
            at=@At("HEAD"),cancellable=true)
    private static void machines$space(OperationTracker tracker,RecipeError noSpace,IInventorySlot slot,ItemStack output,CallbackInfo ci) {
        if(!(slot instanceof MekVirtualSlot virtual) || !virtual.machines$parallel())return;
        if(!output.isEmpty()) {
            int accepted=Integer.MAX_VALUE-slot.insertItem(output.copyWithCount(Integer.MAX_VALUE),Action.SIMULATE,AutomationType.INTERNAL).getCount();
            int operations=accepted/output.getCount();tracker.updateOperations(operations);
            if(operations==0)tracker.addError(accepted==0 && slot.getLimit(slot.getStack())-slot.getCount()>0
                    ?RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT:noSpace);
        }
        ci.cancel();
    }
}
