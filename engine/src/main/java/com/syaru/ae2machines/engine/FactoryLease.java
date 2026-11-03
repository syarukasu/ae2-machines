package com.syaru.ae2machines.engine;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** World-scoped ownership gate; copying a cell never duplicates a factory. */
public final class FactoryLease {
    private final Map<UUID, UUID> active = new HashMap<>();
    public boolean acquire(UUID factory, UUID accelerator) {
        return accelerator.equals(active.computeIfAbsent(factory, ignored -> accelerator));
    }
    public boolean release(UUID factory, UUID accelerator) { return active.remove(factory, accelerator); }
    public boolean isActive(UUID factory) { return active.containsKey(factory); }
}
