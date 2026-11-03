package com.syaru.ae2machines.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets="appeng.crafting.execution.ExecutingCraftingJob$TaskProgress",remap=false)
public interface TaskProgressAccess {
    @Accessor("value") long machines$count();
    @Accessor("value") void machines$count(long value);
}
