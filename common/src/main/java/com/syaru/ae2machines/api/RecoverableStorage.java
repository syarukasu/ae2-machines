package com.syaru.ae2machines.api;

import java.util.Collection;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Explicit opt-in: all mutable backing assets must be contained in these blocks' native NBT. */
public interface RecoverableStorage {
    /** Include this block and every co-owner, loaded in the same level. Empty means unavailable. */
    Collection<BlockEntity> checkpointParticipants();
}
