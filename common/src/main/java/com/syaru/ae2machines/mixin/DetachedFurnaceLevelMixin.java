package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.runtime.DetachedFurnaceTick;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class DetachedFurnaceLevelMixin {
    @Inject(method="setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",at=@At("HEAD"),cancellable=true)
    private void machines$furnaceState(BlockPos pos,BlockState state,int flags,int recursion,CallbackInfoReturnable<Boolean> ci) {
        if(DetachedFurnaceTick.setState((Level)(Object)this,pos,state))ci.setReturnValue(true);
    }
    @Inject(method="blockEntityChanged",at=@At("HEAD"),cancellable=true)
    private void machines$furnaceSave(BlockPos pos,CallbackInfo ci) {
        if(DetachedFurnaceTick.owns((Level)(Object)this,pos))ci.cancel();
    }
    @Inject(method="updateNeighbourForOutputSignal",at=@At("HEAD"),cancellable=true)
    private void machines$furnaceNeighbors(BlockPos pos,Block block,CallbackInfo ci) {
        if(DetachedFurnaceTick.owns((Level)(Object)this,pos))ci.cancel();
    }
}
