package com.syaru.ae2machines.mixin;

import appeng.me.service.TickManagerService;
import appeng.me.service.helpers.TickTracker;
import java.util.*;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value=TickManagerService.class,remap=false)
public interface NativeLevelTickQueue {
    @Accessor("upcomingTicks") Map<Level,PriorityQueue<TickTracker>> machines$upcoming();
}
