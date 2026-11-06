package com.syaru.ae2machines.runtime;

public interface NativeWorldProvider {
    net.minecraft.world.level.block.entity.BlockEntity machines$providerEntity();
    default boolean machines$countedTarget(appeng.api.crafting.IPatternDetails pattern,net.minecraft.core.BlockPos target){return false;}
}
