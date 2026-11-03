package com.syaru.ae2machines.api;

import java.math.BigInteger;

public interface RecoverableEnergyTarget {
    /** Debits the declared source and credits owned processing in one durable before/after decision. */
    BigInteger receiveCheckpointedFE(CheckpointedEnergySource source,BigInteger offered,boolean simulate);
}
