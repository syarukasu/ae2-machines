package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.compat.mek.MekVirtualSlot;
import mekanism.api.inventory.IInventorySlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native chance draws already loop once per operation; each draw must be inserted only once. */
@Pseudo
@Mixin(targets="mekanism.api.recipes.outputs.OutputHelper$4",remap=false)
public abstract class MekSawmillOutputMixin {
    @Unique private boolean machines$nativeDraws;
    @Unique private IInventorySlot machines$pendingSlot;
    @Unique private IInventorySlot machines$operationSlot;
    @Unique private boolean machines$parallel;
    @Unique private ItemStack machines$pending=ItemStack.EMPTY;
    @Unique private int machines$runs;
    @Inject(method="handleOutput(Lmekanism/api/recipes/SawmillRecipe$ChanceOutput;I)V",at=@At("HEAD"))
    private void machines$begin(@Coerce Object output,int count,CallbackInfo ci){
        com.syaru.ae2machines.compat.mek.MekIidVariableWork.observe(output);
        String type=output.getClass().getName();
        machines$nativeDraws=type.equals("mekanism.api.recipes.SawmillRecipe$ChanceOutput")
                || type.equals("mekanism.api.recipes.basic.BasicSawmillRecipe$BasicChanceOutput");
        machines$pendingSlot=null;machines$operationSlot=null;machines$pending=ItemStack.EMPTY;machines$runs=0;
    }
    @Redirect(method="handleOutput(Lmekanism/api/recipes/SawmillRecipe$ChanceOutput;I)V",
            at=@At(value="INVOKE",target="Lmekanism/api/recipes/outputs/OutputHelper;handleOutput(Lmekanism/api/inventory/IInventorySlot;Lnet/minecraft/world/item/ItemStack;I)V",ordinal=1))
    private void machines$oneDraw(IInventorySlot slot,ItemStack stack,int count){
        if(com.syaru.ae2machines.compat.mek.MekIidVariableWork.suppressSecondary())return;
        if(machines$operationSlot!=slot){machines$operationSlot=slot;machines$parallel=slot instanceof MekVirtualSlot v && v.machines$captured();}
        if(!machines$parallel){MekOutputAccess.machines$insert(slot,stack,count);return;}
        if(!machines$nativeDraws){MekOutputAccess.machines$insert(slot,stack,1);return;}
        if(stack.isEmpty())return;
        if(machines$pendingSlot!=slot || !ItemStack.matches(machines$pending,stack)
                || machines$runs==Integer.MAX_VALUE/stack.getCount())machines$flush();
        machines$pendingSlot=slot;machines$pending=stack;machines$runs++;
    }
    @Inject(method="handleOutput(Lmekanism/api/recipes/SawmillRecipe$ChanceOutput;I)V",at=@At("RETURN"))
    private void machines$finish(CallbackInfo ci){machines$flush();}
    @Unique private void machines$flush(){
        if(machines$runs==0)return;
        var slot=machines$pendingSlot;var stack=machines$pending;int runs=machines$runs;
        machines$pendingSlot=null;machines$pending=ItemStack.EMPTY;machines$runs=0;
        // These are actual native draw results, not expected output or a probability estimate.
        MekOutputAccess.machines$insert(slot,stack,runs);
    }
}
