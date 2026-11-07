package com.syaru.ae2machines.compat.mek;

import com.syaru.ae2machines.mixin.MekCachedOperationsAccess;
import com.syaru.ae2machines.world.*;
import java.math.BigInteger;
import mekanism.api.recipes.cache.CachedRecipe;
import mekanism.common.content.evaporation.EvaporationMultiblockData;
import mekanism.common.tile.multiblock.TileEntityThermalEvaporationBlock;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Recipe parallelism; the real tower still owns temperature, solar input and dissipation. */
public final class MekEvaporationParallel {
    public interface Cache {
        CachedRecipe<?> machines$recipe();
        CachedRecipe<?> machines$bound();
        void machines$bound(CachedRecipe<?> value);
    }
    private MekEvaporationParallel() {}
    public static boolean supports(BlockEntity b){return b instanceof TileEntityThermalEvaporationBlock t && t.getMultiblock().isFormed();}
    public static void prepare(EvaporationMultiblockData data,BlockEntity owner,CachedRecipe<?> cached) {
        if(!(owner.getLevel() instanceof net.minecraft.server.level.ServerLevel level) || !NativeCellWorlds.owns(level))return;
        ((MekVirtualTank)data.inputTank).machines$owner(owner);((MekVirtualTank)data.outputTank).machines$owner(owner);
        var cache=(Cache)data;
        if(cached==null || cached==cache.machines$bound() || !com.syaru.ae2machines.platform.MekPassiveChemical.independent(cached.getRecipe()))return;
        var access=(MekCachedOperationsAccess)(Object)cached;var baseline=access.machines$baseline();var post=access.machines$postProcess();
        long pos=data.getMinPos().asLong();
        cached.setBaselineMaxOperations(()->{
            var runtime=runtime(owner);if(runtime==null || !runtime.running())return baseline.getAsInt();
            return access.machines$progress()>0?runtime.state().parallelWork(pos)
                    :(int)Math.min(Integer.MAX_VALUE,(long)baseline.getAsInt()*MekWorldParallel.lanes(owner));
        }).setPostProcessOperations(tracker->{
            post.accept(tracker);var runtime=runtime(owner);if(runtime==null || !runtime.running())return;
            var ops=(com.syaru.ae2machines.mixin.MekOperationsAccess)(Object)tracker;int possible=ops.machines$currentMax();
            if(access.machines$progress()==0){if(possible>0)runtime.state().parallelWork(pos,possible);}
            else if(possible<runtime.state().parallelWork(pos))tracker.updateOperations(0);
        });
        cache.machines$bound(cached);
    }
    private static NativeWorldRuntime runtime(BlockEntity owner){return owner.getLevel() instanceof net.minecraft.server.level.ServerLevel level
            && NativeCellWorlds.owns(level)?NativeWorldRuntime.get(level):null;}
    public static boolean idle(BlockEntity b) {
        if(!supports(b))return true;
        var cached=((Cache)((TileEntityThermalEvaporationBlock)b).getMultiblock()).machines$recipe();
        return cached==null || ((MekCachedOperationsAccess)(Object)cached).machines$progress()==0;
    }
    public static boolean fits(BlockEntity b,BigInteger lanes) {
        if(!supports(b))return true;
        var data=((TileEntityThermalEvaporationBlock)b).getMultiblock();
        for(var tank:java.util.List.of(data.inputTank,data.outputTank))
            if(MekWideFluidTank.amount(tank).compareTo(BigInteger.valueOf(((MekVirtualTank)tank).machines$nativeCapacity()).multiply(lanes))>0)return false;
        return true;
    }
}
