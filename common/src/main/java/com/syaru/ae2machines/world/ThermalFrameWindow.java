package com.syaru.ae2machines.world;

/**
 * Entered after vanilla's chunk work and before its block/entity events. The final suffix
 * stays on the actual ServerLevel.tick stack, including events created by chunk work.
 */
final class ThermalFrameWindow {
    interface Pulse {
        boolean clearSuffix();
        void finishMachines();
        boolean clearNext();
        void postAndDebit();
        boolean running();
        void pre();
        void nativeBody();
        void ambientPrefix();
    }

    private final int extra;
    private int settled, prefixes;
    private boolean entered, lastPosted;

    ThermalFrameWindow(int extra) { this.extra=extra; }

    boolean fold(Pulse pulse) {
        if (entered) return false;
        entered=true;
        while (pulse.clearSuffix() && settled<extra) {
            pulse.finishMachines();
            // The current suffix has run. The caller still owes exactly one POST.
            if (!pulse.clearNext() || !pulse.running()) return true;
            pulse.postAndDebit();
            settled++;
            // A POST can enqueue an AE2 PRE callback or take the cell offline.
            if (!pulse.running() || !pulse.clearNext()) { lastPosted=true; return true; }
            pulse.pre();
            if (!pulse.clearNext() || !pulse.running()) {
                // PRE already ran; vanilla owns the entire next body, not another PRE.
                pulse.nativeBody();
                return true;
            }
            pulse.ambientPrefix();
            prefixes++;
        }
        return false;
    }

    boolean entered(){ return entered; }
    boolean lastPosted(){ return lastPosted; }
    int settled(){ return settled; }
    int extraCompleted(){ return settled - (lastPosted ? 1 : 0); }
    int prefixes(){ return prefixes; }
    int remaining(){ return extra+1-settled; }
}
