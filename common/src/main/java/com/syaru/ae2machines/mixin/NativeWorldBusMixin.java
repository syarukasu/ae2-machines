package com.syaru.ae2machines.mixin;

import appeng.parts.automation.IOBusPart;
import com.syaru.ae2machines.world.NativeCellWorlds;
import com.syaru.ae2machines.world.NativeWorldRuntime;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fold virtual bus copies into the native transfer budget, never into extra grid ticks. */
@Mixin(value=IOBusPart.class,remap=false)
public abstract class NativeWorldBusMixin {
    @Inject(method="getOperationsPerTick",at=@At("RETURN"),cancellable=true)
    private void machines$parallelTransfer(CallbackInfoReturnable<Integer> result){
        var bus=(IOBusPart)(Object)this;
        if(bus.getHost()==null || !(bus.getHost().getBlockEntity().getLevel() instanceof ServerLevel level) || !NativeCellWorlds.owns(level))return;
        int copies=NativeWorldRuntime.get(level).state().nativeLanes();
        // AE2's operation budget is int; chemical/fluid amounts still use its native long transfer API.
        result.setReturnValue((int)Math.min(Integer.MAX_VALUE,(long)result.getReturnValueI()*copies));
    }
}
