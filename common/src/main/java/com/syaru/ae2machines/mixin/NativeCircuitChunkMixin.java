package com.syaru.ae2machines.mixin;
import com.syaru.ae2machines.world.*;
import net.minecraft.server.level.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ServerChunkCache.class)
public abstract class NativeCircuitChunkMixin {
    @Redirect(method="tickChunks",at=@At(value="INVOKE",target="Lcom/google/common/collect/Lists;newArrayListWithCapacity(I)Ljava/util/ArrayList;",remap=false))
    private <T> java.util.ArrayList<T> machines$intervalList(int capacity){
        var cache=(ServerChunkCache)(Object)this;var level=(ServerLevel)cache.getLevel();
        if(!NativeCellWorlds.owns(level))return new java.util.ArrayList<>(capacity);
        var runtime=NativeWorldRuntime.get(level);
        if(!runtime.intervalChunkPlanning() || !machines$trusted(cache))return new java.util.ArrayList<>(capacity);
        var access=(NativeCircuitChunkMapAccess)cache.chunkMap;var source=access.machines$visibleChunkIdentity();
        return runtime.intervalChunkList(capacity,source,source.values());
    }
    @Unique private static boolean machines$trusted(ServerChunkCache cache){
        return cache.getClass()==ServerChunkCache.class && cache.chunkMap.getClass()==ChunkMap.class
                && com.syaru.ae2machines.compat.mek.MekThermalHooks.safe(ServerChunkCache.class)
                && com.syaru.ae2machines.compat.mek.MekThermalHooks.chunkSelectionSafe(ChunkMap.class)
                && com.syaru.ae2machines.compat.mek.MekThermalHooks.safe(ChunkHolder.class);
    }
    @Inject(method="runDistanceManagerUpdates",at=@At("RETURN"))
    private void machines$circuitChanged(CallbackInfoReturnable<Boolean> ci){
        var level=(ServerLevel)((ServerChunkCache)(Object)this).getLevel();
        if(ci.getReturnValueZ() && NativeWorldRuntime.owns(level))NativeWorldRuntime.get(level).circuitChunkChanged();
    }
    @Redirect(method="tickChunks",at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ChunkMap;getChunks()Ljava/lang/Iterable;"))
    private Iterable<ChunkHolder> machines$circuitChunks(ChunkMap map){
        var level=(ServerLevel)((ServerChunkCache)(Object)this).getLevel();
        var original=((NativeCircuitChunkMapAccess)map).machines$circuitChunks();
        if(!NativeWorldRuntime.owns(level) || !machines$trusted((ServerChunkCache)(Object)this))return original;
        return NativeWorldRuntime.get(level).circuitChunks(((NativeCircuitChunkMapAccess)map).machines$visibleChunkIdentity(),original);
    }
}
