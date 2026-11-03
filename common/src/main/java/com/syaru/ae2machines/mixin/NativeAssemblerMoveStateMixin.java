package com.syaru.ae2machines.mixin;

import appeng.blockentity.crafting.MolecularAssemblerBlockEntity;
import com.syaru.ae2machines.world.NativeMoveLifecycle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A detach-time display update must not recreate a block already removed from the source. */
@Mixin(value=MolecularAssemblerBlockEntity.class,remap=false)
public abstract class NativeAssemblerMoveStateMixin {
    @Inject(method="onMainNodeStateChanged",at=@At("HEAD"),cancellable=true)
    private void machines$deferMovingDisplay(CallbackInfo ci){if(NativeMoveLifecycle.relocating((MolecularAssemblerBlockEntity)(Object)this))ci.cancel();}
}
