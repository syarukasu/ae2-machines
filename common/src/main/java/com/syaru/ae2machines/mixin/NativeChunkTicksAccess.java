package com.syaru.ae2machines.mixin;

import java.util.List;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.SavedTick;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelChunkTicks.class)
public interface NativeChunkTicksAccess<T> {
    @Accessor("pendingTicks") List<SavedTick<T>> machines$packed();
}
