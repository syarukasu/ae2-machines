package com.syaru.ae2machines.compat.mek;

import java.math.BigInteger;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Native transmitter fallback container; stored energy is never multiplied. */
public interface MekTransportEnergy {
    void machines$transportOwner(BlockEntity owner);
    BigInteger machines$transportBaseEnergy();
}
