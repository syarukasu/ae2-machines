package com.syaru.ae2machines.compat.mek;

import com.syaru.ae2machines.mixin.MekHeatAccess;
import net.minecraft.nbt.CompoundTag;

/** Additive persistence for enrolled capacitors; never settles the native heat queue early. */
public final class MekPendingHeat {
    public static final String KEY = "ae2machines:deferred_heat";
    public interface Owner { boolean machines$heatEnrolled(); void machines$heatEnrolled(boolean value); }
    private MekPendingHeat() {}
    public static void enroll(Object capacitor) {
        if (capacitor instanceof Owner owner) owner.machines$heatEnrolled(true);
    }
    public static void save(Object capacitor, CompoundTag tag) {
        if (capacitor instanceof Owner owner && owner.machines$heatEnrolled()) {
            double pending = ((MekHeatAccess)capacitor).ae2machines$pendingHeat();
            if (!Double.isFinite(pending)) throw new IllegalStateException("MEK_PENDING_HEAT_INVALID");
            tag.putDouble(KEY, pending);
        }
    }
    public static void load(Object capacitor, CompoundTag tag) {
        if (tag.contains(KEY)) {
            double pending = tag.getDouble(KEY);
            if (!Double.isFinite(pending)) throw new IllegalStateException("MEK_PENDING_HEAT_INVALID");
            ((Owner)capacitor).machines$heatEnrolled(true);
            ((MekHeatAccess)capacitor).ae2machines$pendingHeat(pending);
        }
    }
}
