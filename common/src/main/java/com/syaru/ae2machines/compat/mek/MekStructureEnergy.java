package com.syaru.ae2machines.compat.mek;

import java.math.BigInteger;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Base energy units use MekanismAdapter's exact 1/10000 J convention. */
public interface MekStructureEnergy {
    void machines$structureOwner(BlockEntity owner);
    BigInteger machines$structureBaseEnergy();
}
