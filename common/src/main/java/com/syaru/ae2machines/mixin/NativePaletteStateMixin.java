package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.world.NativePaletteState;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(PalettedContainer.class)
public abstract class NativePaletteStateMixin implements NativePaletteState {
    @Unique private long machines$stateRevision;
    @Override public long machines$stateRevision(){return machines$stateRevision;}
    // Observe the storage itself, including direct section/palette writes and palette resizing.
    @Inject(method={"getAndSet(ILjava/lang/Object;)Ljava/lang/Object;","onResize"},at={@At("HEAD"),@At("RETURN")})
    private void machines$changedValue(CallbackInfoReturnable<?> ci){machines$stateRevision++;}
    @Inject(method={"set(ILjava/lang/Object;)V","read"},at={@At("HEAD"),@At("RETURN")})
    private void machines$changed(CallbackInfo ci){machines$stateRevision++;}
}
