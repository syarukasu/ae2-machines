package com.syaru.ae2machines.compat.mek;

public interface MekFusionState {
    double machines$plasmaTemperature();
    void machines$rescalePlasma(double ratio);
    boolean machines$plasmaFits(double ratio);
    void machines$restorePlasma(double canonical,int lanes);
}
