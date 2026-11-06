package com.syaru.ae2machines.compat.mek;

import com.syaru.ae2machines.mixin.*;
import com.syaru.ae2machines.platform.MekanismAdapter;
import com.syaru.ae2machines.world.*;
import java.math.BigInteger;
import mekanism.common.tile.prefab.TileEntityProgressMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/** Native completions and a paid remainder, shared by item and chemical processing. */
final class MekSubtickTime {
    private MekSubtickTime() {}

    static NativeQuietStep plan(TileEntityProgressMachine<?> machine,MekRecipeState state,NativeQuietStep clock) {
        if(Boolean.getBoolean("ae2machines.disableSubtickRecipes") || !(machine.getLevel() instanceof ServerLevel world)
                || !NativeCellWorlds.owns(world) || !MekThermalHooks.processingSafe(machine.getClass())
                || !unobserved(machine))return null;
        var runtime=NativeWorldRuntime.get(world);
        if(state.operations()!=runtime.state().nativeLanes())return null;
        int limit=state.work(clock.limit()).elapsed().intValueExact();
        if(limit<state.duration()-state.progress())return null;
        var base=(MekTimeBaseAccess)machine;
        int redstone=base.machines$timeRedstone();boolean comparator=base.machines$timeComparator();
        var cached=state.cached();var nativeStep=(MekNativeStep)cached;
        var progressChanged=((MekTimeCacheAccess)cached).machines$timeProgressChanged();
        var nativeOperations=((MekCachedOperationsAccess)cached).machines$baseline();
        long position=machine.getBlockPos().asLong();
        int oldGroup=runtime.state().recordedProcessWork(position,0);
        var applied=new boolean[1];
        return new NativeQuietStep(limit,n->{
            if(n<0 || n>limit)throw new IllegalArgumentException("SUBTICK_INTERVAL_EXCEEDS_OFFER");
            if(n==0)return;
            if(!state.current(machine))throw new NativeQuietStep.StateChanged("NATIVE_RECIPE_STATE_CHANGED");
            applied[0]=true;
            var work=state.work(n);var completed=work.completed();
            if(completed.signum()>0){
                int nativeCount=completed.multiply(BigInteger.valueOf(state.operations())).intValueExact();
                var toCompletion=completed.multiply(BigInteger.valueOf(state.duration())).subtract(BigInteger.valueOf(state.progress()));
                // Native process pays the last tick of each cycle; all skipped progress is paid here.
                MekanismAdapter.setStoredEnergy(machine,state.energy().subtract(state.cost().multiply(toCompletion.subtract(completed))));
                nativeStep.machines$progress(state.duration()-1);progressChanged.accept(state.duration()-1);
                cached.setBaselineMaxOperations(()->nativeCount);
                try{cached.process();}finally{cached.setBaselineMaxOperations(nativeOperations);}
                if(nativeStep.machines$completed()!=nativeCount || nativeStep.machines$progress()!=0)
                    throw new IllegalStateException("NATIVE_SUBTICK_COMPLETION_CHANGED");
                if(work.progress().signum()>0)MekanismAdapter.setStoredEnergy(machine,
                        MekanismAdapter.energy(machine).subtract(state.cost().multiply(work.progress())));
            }else{
                MekanismAdapter.setStoredEnergy(machine,state.energy().subtract(state.cost().multiply(work.elapsed())));
            }
            int remainder=work.progress().intValueExact();
            nativeStep.machines$progress(remainder);progressChanged.accept(remainder);
            runtime.state().processWork(position,0,state.operations());
            if(!MekanismAdapter.energy(machine).equals(state.energy().subtract(state.cost().multiply(work.elapsed()))))
                throw new IllegalStateException("NATIVE_SUBTICK_ENERGY_CHANGED");
            if(machine.supportsComparator()){base.machines$timeRedstone(base.machines$timeRedstoneLevel());base.machines$timeComparator(false);}
            clock.advance().accept(n);world.getChunkAt(machine.getBlockPos()).setUnsaved(true);
            runtime.subtickWork(work.completed().longValueExact(),n);
        },()->{
            if(!applied[0])return;
            state.restoreAssets();MekanismAdapter.setStoredEnergy(machine,state.energy());
            nativeStep.machines$progress(state.progress());progressChanged.accept(state.progress());
            base.machines$timeRedstone(redstone);base.machines$timeComparator(comparator);clock.rollback().run();
            runtime.state().processWork(position,0,oldGroup);
        });
    }

    private static boolean unobserved(TileEntityProgressMachine<?> machine) {
        var world=machine.getLevel();var pos=machine.getBlockPos();
        for(var p:BlockPos.betweenClosed(pos.offset(-2,-2,-2),pos.offset(2,2,2))){
            if(p.equals(pos))continue;
            var state=world.getBlockState(p);
            if(state.isAir() || state.is(Blocks.BARRIER))continue;
            var be=world.getBlockEntity(p);
            if(be==null || !NativeTemporalScheduler.passiveContract(be))return false;
        }
        return machine.playersUsing.isEmpty();
    }
}
