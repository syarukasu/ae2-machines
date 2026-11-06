package com.syaru.ae2machines.mixin;
import net.minecraft.server.level.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.*;
@Mixin(ChunkMap.class)
public interface NativeCircuitChunkMapAccess {
    @Invoker("getChunks") Iterable<ChunkHolder> machines$circuitChunks();
    @Invoker("saveChunkIfNeeded") boolean machines$saveNativeChunk(ChunkHolder holder);
    @Accessor("visibleChunkMap") it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap<ChunkHolder> machines$visibleChunkIdentity();
}
