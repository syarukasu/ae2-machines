package com.syaru.ae2machines.mixin;

import appeng.blockentity.crafting.MolecularAssemblerBlockEntity;
import com.syaru.ae2machines.runtime.CapturedAssembler;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=MolecularAssemblerBlockEntity.class,remap=false)
public abstract class CapturedAssemblerOutputMixin {
    @Inject(method="pushTo",at=@At("HEAD"),cancellable=true)
    private void machines$push(ItemStack output,Direction direction,CallbackInfoReturnable<ItemStack> ci) {
        if((Object)this instanceof CapturedAssembler captured)ci.setReturnValue(captured.pushCaptured(output,direction));
    }
}
