package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.world.NativeWorldRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Level.class)
public abstract class NativeTemporalInvalidationMixin {
    @Inject(method="setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",at=@At("RETURN"))
    private void machines$changed(BlockPos pos,BlockState state,int flags,int recursion,CallbackInfoReturnable<Boolean> ci){
        if(ci.getReturnValue() && (Object)this instanceof ServerLevel level && NativeWorldRuntime.owns(level))NativeWorldRuntime.get(level).blockChanged(pos);
    }
    @Inject(method="setBlockEntity",at=@At("RETURN"))
    private void machines$entityChanged(BlockEntity block,CallbackInfo ci){if((Object)this instanceof ServerLevel level && NativeWorldRuntime.owns(level))NativeWorldRuntime.get(level).invalidateEquipment(block.getBlockPos());}
    @Inject(method="removeBlockEntity",at=@At("RETURN"))
    private void machines$entityRemoved(BlockPos pos,CallbackInfo ci){if((Object)this instanceof ServerLevel level && NativeWorldRuntime.owns(level))NativeWorldRuntime.get(level).invalidateEquipment(pos);}
}
