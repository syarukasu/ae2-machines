package com.syaru.ae2machines.engine;

import java.util.List;

/** Splits a payment between independent machines, then offers unused shares again. */
public final class PowerDistribution {
    @FunctionalInterface public interface Receiver {
        long receive(long amount, boolean simulate);
    }
    private PowerDistribution() {}
    public static long receive(long offered, long tick, boolean simulate, List<Receiver> receivers) {
        if (offered < 0) throw new IllegalArgumentException("Negative payment");
        int size = receivers.size();
        if (offered == 0 || size == 0) return 0;
        if (size == 1) return take(receivers.get(0), offered, simulate);
        long left = offered, share = offered / size, remainder = offered % size;
        int start = Math.floorMod(tick, size);
        long[] allocations = new long[size];
        for (int i = 0; i < size; i++) {
            long amount = share + (i < remainder ? 1 : 0);
            int index = (start + i) % size;
            if (amount != 0) {
                allocations[index] = take(receivers.get(index), amount, true);
                left -= allocations[index];
            }
        }
        // Simulation must account for shares already promised by the same receiver.
        // Query the receiver's total allocation, not a second independent payment.
        for (int i = 0; i < size && left > 0; i++) {
            int index = (start + i) % size;
            long amount = take(receivers.get(index), allocations[index] + left, true);
            if (amount < allocations[index]) throw new IllegalStateException("Energy availability changed during simulation");
            left -= amount - allocations[index];
            allocations[index] = amount;
        }
        if (simulate) return offered - left;
        long received = 0;
        for (int i = 0; i < size; i++) {
            int index = (start + i) % size;
            if (allocations[index] != 0) received += take(receivers.get(index), allocations[index], false);
        }
        return received;
    }
    private static long take(Receiver receiver, long amount, boolean simulate) {
        long accepted = receiver.receive(amount, simulate);
        if (accepted < 0 || accepted > amount) throw new IllegalStateException("Invalid native energy acceptance");
        return accepted;
    }
}
