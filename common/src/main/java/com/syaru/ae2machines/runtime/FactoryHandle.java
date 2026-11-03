package com.syaru.ae2machines.runtime;

import java.math.BigInteger;
import java.util.List;

/** Common accelerator lifecycle; native-world factories do not masquerade as pattern providers. */
public interface FactoryHandle extends AutoCloseable {
    ProcessingRuntime runtime();
    List<ProcessingRuntime> runtimes();
    boolean isOnline();
    boolean canConfigure();
    boolean supportsConfiguration(BigInteger speed,BigInteger lanes);
    void configure(BigInteger speed,BigInteger lanes);
    boolean canDetach();
    void detach();
    void persist();
    void tick(long tick);
    int receiveFE(int offered,long tick,boolean simulate);
    default int extractFE(int requested,long tick,boolean simulate){return 0;}
    default boolean exposesEnergyOutput(){return false;}
    int storedFE();
    int capacityFE();
    long receiveEU(long voltage,long amps,long tick,boolean simulate);
    long voltageEU();
    long demandEU();
    @Override void close();
}
