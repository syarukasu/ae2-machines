package com.syaru.ae2machines.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.crafting.execution.ExecutingCraftingJob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.Map;

@Mixin(value=ExecutingCraftingJob.class,remap=false)
public interface ExecutingJobAccess {
    @Accessor("tasks") Map<IPatternDetails,Object> machines$tasks();
}
