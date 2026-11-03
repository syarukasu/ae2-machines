package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.compat.mek.MekNativeStep;
import mekanism.api.recipes.cache.CachedRecipe;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="mekanism.api.recipes.cache.CachedRecipe",remap=false)
public abstract class MekNativeStepMixin implements MekNativeStep {
    @Shadow private int operatingTicks;
    @Shadow protected abstract void finishProcessing(int operations);
    @Unique private int machines$finished;
    @Override public int machines$completed(){return machines$finished;}
    @Override public int machines$progress(){return operatingTicks;}
    @Override public void machines$progress(int ticks){if(ticks<0)throw new IllegalArgumentException("Negative native progress");operatingTicks=ticks;}
    @Inject(method="process",at=@At("HEAD"))
    private void machines$begin(CallbackInfo ci){machines$finished=0;}
    @Redirect(method="process",at=@At(value="INVOKE",target="Lmekanism/api/recipes/cache/CachedRecipe;finishProcessing(I)V"))
    private void machines$finish(CachedRecipe<?> ignored,int operations){finishProcessing(operations);machines$finished=operations;}
}
