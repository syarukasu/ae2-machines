package com.syaru.ae2machines.runtime;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import com.syaru.ae2machines.world.FactoryJournal;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/** A native AE2 provider whose counted input ownership shares the CPU handoff journal. */
public interface CountedCraftingProvider extends ICraftingProvider {
    int normalBatchLimit(IPatternDetails pattern,int limit);
    boolean pushPatterns(IPatternDetails pattern,KeyCounter[] holders,int executions);
    CountedCraftingProvider handoffOwner();
    ServerLevel handoffLevel();
    FactoryJournal handoffJournal();
    Set<BlockEntity> recoveryParticipants();
    default boolean nativeWorldHandoff(){return false;}

    static CountedCraftingProvider resolve(ICraftingProvider provider,IPatternDetails pattern) {
        if(provider instanceof CountedCraftingProvider counted)return counted;
        if(provider instanceof NativeWorldProvider nativeProvider) {
            var block=nativeProvider.machines$providerEntity();
            var batches=NativeWorldBatches.get(block);
            if(batches!=null)return batches.normalProvider(block,pattern);
        }
        return null;
    }
}
