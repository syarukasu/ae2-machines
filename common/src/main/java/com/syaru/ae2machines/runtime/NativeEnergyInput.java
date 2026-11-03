package com.syaru.ae2machines.runtime;

/** Platform-native FE conversion over the captured machine's exact energy buffer. */
public interface NativeEnergyInput {
    int receive(int offered, long tick, boolean simulate);
    int stored();
    int capacity();
}
