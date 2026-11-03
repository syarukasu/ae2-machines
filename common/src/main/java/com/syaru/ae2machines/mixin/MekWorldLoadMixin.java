package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.compat.mek.MekWorldParallel;
import com.syaru.ae2machines.runtime.NativeMachines;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native deserialization precedes setLevel; only an owned cell may restore aggregated energy. */
@Mixin(BlockEntity.class)
public abstract class MekWorldLoadMixin {
    @Inject(method="setLevel",at=@At("RETURN"))
    private void machines$loaded(Level level,CallbackInfo ci) {
        if(NativeMachines.mek())MekWorldParallel.levelAssigned((BlockEntity)(Object)this);
    }
}
