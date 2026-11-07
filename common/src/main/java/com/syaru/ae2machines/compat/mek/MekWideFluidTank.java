package com.syaru.ae2machines.compat.mek;

import java.math.BigInteger;
import java.util.function.Supplier;

/** Exact pool quantity; the public fluid capability remains an int-sized view. */
public interface MekWideFluidTank {
    BigInteger machines$fluidAmount();
    BigInteger machines$fluidCapacity();
    void machines$fluidAmount(BigInteger amount);
    void machines$fluidCapacity(Supplier<BigInteger> capacity);
    boolean machines$wideFluid();
    static BigInteger amount(mekanism.api.fluid.IExtendedFluidTank tank) {
        return tank instanceof MekWideFluidTank wide?wide.machines$fluidAmount():BigInteger.valueOf(tank.getFluidAmount());
    }
}
