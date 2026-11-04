package com.syaru.ae2machines.mixin;
import appeng.hooks.ticking.TickHandler;
import appeng.util.ILevelRunnable;
import com.google.common.collect.Multimap;
import java.util.*;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(value=TickHandler.class,remap=false)
public interface NativeTemporalCallsAccess {
    @Accessor("callQueue") Map<LevelAccessor,Queue<ILevelRunnable>> machines$timeCalls();
    @Accessor("craftingJobs") Multimap<LevelAccessor,?> machines$timeCalculations();
}
