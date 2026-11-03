package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.runtime.NativeParallelContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(Level.class)
public abstract class NativeParallelLevelMixin {
    @Inject(method="getBlockEntity",at=@At("HEAD"),cancellable=true)
    private void machines$localEntity(BlockPos pos,CallbackInfoReturnable<BlockEntity> ci){var c=NativeParallelContext.current((Level)(Object)this);if(c!=null)ci.setReturnValue(c.block(pos));}
    @Inject(method="getBlockState",at=@At("HEAD"),cancellable=true)
    private void machines$localState(BlockPos pos,CallbackInfoReturnable<BlockState> ci){var c=NativeParallelContext.current((Level)(Object)this);if(c!=null){var state=c.state(pos);if(state!=null)ci.setReturnValue(state);}}
    @Inject(method="setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",at=@At("HEAD"),cancellable=true)
    private void machines$localWrite(BlockPos pos,BlockState state,int flags,int recursion,CallbackInfoReturnable<Boolean> ci){var c=NativeParallelContext.current((Level)(Object)this);if(c!=null)ci.setReturnValue(c.set(pos,state));}
    @Inject(method="blockEntityChanged",at=@At("HEAD"),cancellable=true)
    private void machines$localSave(BlockPos pos,CallbackInfo ci){var c=NativeParallelContext.current((Level)(Object)this);if(c!=null){c.own(pos);ci.cancel();}}
    @Inject(method="updateNeighbourForOutputSignal",at=@At("HEAD"),cancellable=true)
    private void machines$localSignal(BlockPos pos,Block block,CallbackInfo ci){var c=NativeParallelContext.current((Level)(Object)this);if(c!=null){c.own(pos);ci.cancel();}}
}
