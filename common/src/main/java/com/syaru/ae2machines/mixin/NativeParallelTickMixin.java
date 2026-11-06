package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.runtime.NativeParallelRuntime;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets="net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
public abstract class NativeParallelTickMixin {
    @Unique private byte machines$parallelKind;
    @Accessor("blockEntity") public abstract BlockEntity machines$blockEntity();
    @Inject(method="tick",at=@At("HEAD"),cancellable=true)
    private void machines$parallelStates(CallbackInfo ci){
        // The bound owner is final. Only its Java contract is cached, never world ownership or readiness.
        if(machines$parallelKind==0)machines$parallelKind=(byte)(machines$blockEntity() instanceof com.syaru.ae2machines.api.NativeParallelMachine?1:2);
        if(machines$parallelKind==1 && NativeParallelRuntime.tickOwned(machines$blockEntity()))ci.cancel();
    }
}
