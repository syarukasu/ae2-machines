package com.syaru.ae2machines.mixin;
import net.minecraft.server.level.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(ChunkMap.class)
public interface NativeCircuitChunkMapAccess {
    @Invoker("getChunks") Iterable<ChunkHolder> machines$circuitChunks();
}
