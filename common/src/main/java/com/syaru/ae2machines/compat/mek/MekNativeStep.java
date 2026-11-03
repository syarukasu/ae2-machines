package com.syaru.ae2machines.compat.mek;

/** Observes an actual native process call; never substitutes a recipe result. */
public interface MekNativeStep {
    int machines$completed();
    int machines$progress();
    void machines$progress(int ticks);
}
