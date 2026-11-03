package com.syaru.ae2machines.mixin;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo @Mixin(targets="com.gregtechceu.gtceu.api.machine.trait.RecipeLogic",remap=false)
public abstract class GtOwnedBatchMixin {
    @Inject(method="serverTick",at=@At("HEAD"),cancellable=true)
    private void machines$owned(CallbackInfo ci) {
        var logic=(com.gregtechceu.gtceu.api.machine.trait.RecipeLogic)(Object)this;
        var block=(net.minecraft.world.level.block.entity.BlockEntity)((com.gregtechceu.gtceu.api.machine.MetaMachine)logic.machine).getHolder();
        if(com.syaru.ae2machines.runtime.NativeWorldBatches.owns(block))ci.cancel();
    }
}
