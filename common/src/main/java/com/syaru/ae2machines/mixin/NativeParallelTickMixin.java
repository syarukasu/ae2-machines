package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.runtime.NativeParallelRuntime;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets="net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
public abstract class NativeParallelTickMixin {
    @Accessor("blockEntity") public abstract BlockEntity machines$blockEntity();
    @Inject(method="tick",at=@At("HEAD"),cancellable=true)
    private void machines$parallelStates(CallbackInfo ci){if(NativeParallelRuntime.tickOwned(machines$blockEntity()))ci.cancel();}
}
