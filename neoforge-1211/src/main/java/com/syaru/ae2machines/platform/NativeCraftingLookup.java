package com.syaru.ae2machines.platform;

import appeng.api.implementations.blockentities.ICraftingMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

public final class NativeCraftingLookup {
    private NativeCraftingLookup() {}

    public static ICraftingMachine find(Level level, BlockPos pos, Direction side) {
        return ICraftingMachine.of(level, pos, side);
    }
}
