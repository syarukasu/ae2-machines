package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.world.*;
import java.util.function.BooleanSupplier;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
abstract class ThermalCellBoundaryMixin {
    @Inject(method="tick", at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ServerLevel;runBlockEvents()V"), cancellable=true)
    private void machines$thermalBoundary(BooleanSupplier time, CallbackInfo ci) {
        var world=(ServerLevel)(Object)this;
        if (NativeCellWorlds.owns(world) && NativeWorldRuntime.get(world).foldThermalBoundary(time)) ci.cancel();
    }
}
