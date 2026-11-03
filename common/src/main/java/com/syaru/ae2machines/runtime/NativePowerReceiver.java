package com.syaru.ae2machines.runtime;

import com.syaru.ae2machines.engine.MeteredEnergy;
import java.math.BigInteger;

/** Native conversion boundary; demand is not an energy-storage capacity. */
public interface NativePowerReceiver {
    BigInteger stored();
    BigInteger capacity(long tick);
    BigInteger receive(BigInteger offered,long tick,boolean simulate);

    static NativePowerReceiver demand(ProcessingRuntime runtime) {
        return new NativePowerReceiver() {
            public BigInteger stored() { return BigInteger.ZERO; }
            public BigInteger capacity(long tick) { return runtime.energyDemand(tick); }
            public BigInteger receive(BigInteger n,long tick,boolean simulate) { return runtime.receiveEnergy(n,tick,simulate); }
        };
    }
    static NativePowerReceiver demand(ProcessingRuntime runtime,BigInteger quantum) {
        return new NativePowerReceiver() {
            public BigInteger stored() { return BigInteger.ZERO; }
            public BigInteger capacity(long tick) { return runtime.roundedEnergyDemand(tick,quantum); }
            public BigInteger receive(BigInteger n,long tick,boolean simulate) { return runtime.receiveRoundedEnergy(n,tick,quantum,simulate); }
        };
    }
    static NativePowerReceiver reservoir(MeteredEnergy energy) {
        return new NativePowerReceiver() {
            public BigInteger stored() { return energy.stored(); }
            public BigInteger capacity(long tick) { return energy.capacity(); }
            public BigInteger receive(BigInteger n,long tick,boolean simulate) { return energy.insert(n,tick,simulate); }
        };
    }
}
