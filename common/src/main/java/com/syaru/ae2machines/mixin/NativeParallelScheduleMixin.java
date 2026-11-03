package com.syaru.ae2machines.mixin;
import com.syaru.ae2machines.runtime.NativeParallelContext;
import net.minecraft.world.ticks.LevelTicks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LevelTicks.class)
public abstract class NativeParallelScheduleMixin {
    @Inject(method="schedule",at=@At("HEAD"))
    private void machines$noExternalScheduledWork(CallbackInfo ci){NativeParallelContext.rejectWorldEffect();}
}
