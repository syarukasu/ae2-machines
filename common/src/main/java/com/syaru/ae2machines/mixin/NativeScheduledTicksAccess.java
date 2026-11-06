package com.syaru.ae2machines.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import net.minecraft.world.ticks.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelTicks.class)
public interface NativeScheduledTicksAccess<T> {
    @Accessor("allContainers") Long2ObjectMap<LevelChunkTicks<T>> machines$containers();
    @Accessor("nextTickForContainer") it.unimi.dsi.fastutil.longs.Long2LongMap machines$deadlines();
    @Accessor("containersToTick") Queue<LevelChunkTicks<T>> machines$collecting();
    @Accessor("toRunThisTick") Queue<ScheduledTick<T>> machines$running();
    @Accessor("alreadyRunThisTick") List<ScheduledTick<T>> machines$ran();
    @Accessor("toRunThisTickSet") Set<ScheduledTick<?>> machines$runningSet();
}
