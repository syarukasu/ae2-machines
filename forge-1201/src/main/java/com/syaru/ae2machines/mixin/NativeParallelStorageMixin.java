package com.syaru.ae2machines.mixin;
import appeng.util.BlockApiCache;
import com.syaru.ae2machines.api.NativeParallelAccess;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.capabilities.Capability;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=BlockApiCache.class,remap=false)
public abstract class NativeParallelStorageMixin {
    @Shadow @Final private ServerLevel level;
    @Shadow @Final private BlockPos fromPos;
    @Shadow @Final private Capability<?> capability;
    @Inject(method="find",at=@At("HEAD"),cancellable=true)
    private void machines$nativeStorage(Direction side,CallbackInfoReturnable<Object> ci){
        if(capability!=(Object)appeng.capabilities.Capabilities.STORAGE || !level.hasChunkAt(fromPos))return;
        var storage=NativeParallelAccess.storage(level.getBlockEntity(fromPos),side);if(storage!=null)ci.setReturnValue(storage);
    }
}
