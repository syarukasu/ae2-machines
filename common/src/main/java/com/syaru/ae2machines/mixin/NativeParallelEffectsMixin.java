package com.syaru.ae2machines.mixin;
import com.syaru.ae2machines.runtime.NativeParallelContext;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(ServerLevel.class)
public abstract class NativeParallelEffectsMixin {
    @Inject(method="addFreshEntity",at=@At("HEAD"))
    private void machines$noExternalEntity(CallbackInfoReturnable<Boolean> ci){NativeParallelContext.rejectWorldEffect();}
    @Inject(method="blockEvent",at=@At("HEAD"))
    private void machines$noExternalEvent(CallbackInfo ci){NativeParallelContext.rejectWorldEffect();}
}
