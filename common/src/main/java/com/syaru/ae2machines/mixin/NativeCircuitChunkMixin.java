package com.syaru.ae2machines.mixin;
import com.syaru.ae2machines.world.*;
import net.minecraft.server.level.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ServerChunkCache.class)
public abstract class NativeCircuitChunkMixin {
    @Shadow @Final private ServerLevel level;
    @Inject(method="runDistanceManagerUpdates",at=@At("RETURN"))
    private void machines$circuitChanged(CallbackInfoReturnable<Boolean> ci){
        if(ci.getReturnValueZ() && NativeWorldRuntime.owns(level))NativeWorldRuntime.get(level).circuitChunkChanged();
    }
    @Redirect(method="tickChunks",at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ChunkMap;getChunks()Ljava/lang/Iterable;"))
    private Iterable<ChunkHolder> machines$circuitChunks(ChunkMap map){
        var original=((NativeCircuitChunkMapAccess)map).machines$circuitChunks();
        return NativeWorldRuntime.owns(level)?NativeWorldRuntime.get(level).circuitChunks(original):original;
    }
}
