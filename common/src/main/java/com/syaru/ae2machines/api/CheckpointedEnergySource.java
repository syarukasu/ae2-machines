package com.syaru.ae2machines.api;

import java.math.BigInteger;
import java.util.Collection;
import net.minecraft.world.level.block.entity.BlockEntity;

/** A server-thread pull source whose entire balance is authoritative in the declared blocks' NBT. */
public interface CheckpointedEnergySource {
    Collection<BlockEntity> checkpointParticipants();
    /** Simulation must not mutate. A modulated extraction must match the same-thread simulated packet. */
    BigInteger extractFE(BigInteger maximum,boolean simulate);
}
