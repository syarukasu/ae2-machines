package com.syaru.ae2machines.mixin;
import com.syaru.ae2machines.api.NativeParallelAccess;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=BlockCapabilityCache.class,remap=false)
public abstract class NativeParallelStorageMixin {
    @Shadow @Final private ServerLevel level;
    @Shadow @Final private BlockPos pos;
    @Shadow @Final private BlockCapability<?,?> capability;
    @Shadow @Final private Object context;
    @Inject(method="getCapability",at=@At("HEAD"),cancellable=true)
    private void machines$nativeStorage(CallbackInfoReturnable<Object> ci){
        if(capability!=(Object)appeng.api.AECapabilities.ME_STORAGE || !level.hasChunkAt(pos))return;
        var storage=NativeParallelAccess.storage(level.getBlockEntity(pos),context instanceof Direction d?d:null);if(storage!=null)ci.setReturnValue(storage);
    }
}
