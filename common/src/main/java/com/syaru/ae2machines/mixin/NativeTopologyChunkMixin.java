package com.syaru.ae2machines.mixin;
import com.syaru.ae2machines.world.NativeWorldRuntime;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Direct chunk edits and load/unload must invalidate the same index as Level edits. */
@Mixin(LevelChunk.class)
public abstract class NativeTopologyChunkMixin {
    @Inject(method={"setBlockEntity","removeBlockEntity","clearAllBlockEntities","setLoaded"},at=@At("RETURN"))
    private void machines$topologyChanged(CallbackInfo ci){
        if(((LevelChunk)(Object)this).getLevel() instanceof ServerLevel level && NativeWorldRuntime.owns(level))NativeWorldRuntime.get(level).invalidateTemporal();
    }
}
