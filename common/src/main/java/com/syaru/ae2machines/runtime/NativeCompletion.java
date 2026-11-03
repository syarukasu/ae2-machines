package com.syaru.ae2machines.runtime;

import net.minecraft.nbt.CompoundTag;
import java.math.BigInteger;
import java.util.UUID;

/** Native side effects are completed before outputs become an externally deliverable receipt. */
public interface NativeCompletion {
    boolean online();
    boolean ready(UUID id,BigInteger completed);
    boolean tick(ProcessingRuntime runtime);
    CompoundTag save();
    void restoreTo(CompoundTag lane);
}
