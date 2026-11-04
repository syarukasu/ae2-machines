package com.syaru.ae2machines.world;

/** Emergency opt-out and an identical-code control for isolated benchmarks. */
public final class NativeThermalOptions {
    public static final boolean ENABLED=!Boolean.getBoolean("ae2machines.disableThermalBatching");
    private NativeThermalOptions(){}
}
