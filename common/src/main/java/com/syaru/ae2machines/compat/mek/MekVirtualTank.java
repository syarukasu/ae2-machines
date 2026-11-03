package com.syaru.ae2machines.compat.mek;

import net.minecraft.world.level.block.entity.BlockEntity;

/** Native tank remains the sole owner; only its representable lane capacity changes. */
public interface MekVirtualTank {
    void machines$owner(BlockEntity owner);
    long machines$nativeCapacity();
}
