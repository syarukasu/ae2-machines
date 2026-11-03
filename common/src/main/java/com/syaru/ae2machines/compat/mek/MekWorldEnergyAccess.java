package com.syaru.ae2machines.compat.mek;

import java.math.BigInteger;

public interface MekWorldEnergyAccess {
    BigInteger machines$nativeCapacity();
    void machines$loadedEnergy(BigInteger energy);
    void machines$levelAssigned(boolean virtual);
}
