package com.syaru.ae2machines.compat.mek;

import net.minecraft.world.level.block.entity.BlockEntity;

public interface MekStructureHeat {
    void machines$structureOwner(BlockEntity owner);
    double machines$baseHeatCapacity();
    boolean machines$scaledHeat();
}
