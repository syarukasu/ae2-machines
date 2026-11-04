package com.syaru.ae2machines.mixin;
import com.syaru.ae2machines.world.NativeWorldRuntime;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntity.class)
public abstract class NativeTopologyBlockEntityMixin {
    @Inject(method={"setRemoved","clearRemoved"},at=@At("RETURN"))
    private void machines$topologyChanged(CallbackInfo ci){
        if(((BlockEntity)(Object)this).getLevel() instanceof ServerLevel level && NativeWorldRuntime.owns(level))NativeWorldRuntime.get(level).invalidateTemporal();
    }
}
