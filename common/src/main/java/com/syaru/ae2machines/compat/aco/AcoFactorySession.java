package com.syaru.ae2machines.compat.aco;

import com.syaru.ae2craftingoptimizer.api.batch.v2.ProviderOwnedPatternBatchTarget;
import com.syaru.ae2machines.block.BaseAccelerator;
import com.syaru.ae2machines.runtime.FactorySession;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.UUID;

/** Optional class: exposes the actual persistent BE through ACO's provider-owned target contract. */
public final class AcoFactorySession extends FactorySession implements ProviderOwnedPatternBatchTarget {
    public AcoFactorySession(BaseAccelerator host,UUID id) { super(host,id); }
    public AcoFactorySession(BaseAccelerator host,UUID id,int lane) { super(host,id,lane); }
    public AcoFactorySession(BaseAccelerator host,UUID id,int lane,com.syaru.ae2machines.world.FactoryJournal journal) { super(host,id,lane,journal); }
    @Override public BlockEntity aco$getProviderOwnedBatchTarget() { return host; }
}
