package com.syaru.ae2machines.api;

import java.math.BigInteger;

/** FE transfer for exact-count sources. The returned amount is the source's actual debit. */
public interface ExactEnergyReceiver {
    BigInteger receiveExactFE(BigInteger offered,boolean simulate);
    /** Offered/returned amounts are EU, divisible by the supplied native packet voltage. */
    default BigInteger receiveExactEU(long voltage,BigInteger offered,boolean simulate){return BigInteger.ZERO;}
}
