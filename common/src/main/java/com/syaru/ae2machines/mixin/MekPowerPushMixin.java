package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.world.NativePowerPushes;
import mekanism.common.tile.base.TileEntityMekanism;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="mekanism.common.tile.component.TileComponentEjector",remap=false)
public abstract class MekPowerPushMixin {
    @Shadow @Final private TileEntityMekanism tile;
    @Unique private NativePowerPushes.Scope machines$powerScope;
    @Inject(method="tickServer",at=@At("HEAD"))
    private void machines$begin(CallbackInfo ci){
        if(machines$powerScope!=null)throw new IllegalStateException("Previous native power emission did not finish");
        machines$powerScope=NativePowerPushes.begin(tile);
    }
    @Inject(method="tickServer",at=@At("RETURN"))
    private void machines$finish(CallbackInfo ci){var scope=machines$powerScope;machines$powerScope=null;if(scope!=null)scope.close();}
}
