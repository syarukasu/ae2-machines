package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.runtime.NativeWorldBatches;
import mekanism.common.tile.component.TileComponentConfig;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="mekanism.common.tile.component.TileComponentConfig",remap=false)
public abstract class MekBatchRouteConfigMixin {
    @Inject(method="sideChangedBasic",at=@At("RETURN"))
    private void machines$routeChanged(CallbackInfo ci){
        NativeWorldBatches.configurationChanged(((TileComponentConfig)(Object)this).tile);
    }
}
