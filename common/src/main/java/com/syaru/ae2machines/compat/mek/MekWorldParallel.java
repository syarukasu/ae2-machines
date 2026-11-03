package com.syaru.ae2machines.compat.mek;

import com.syaru.ae2machines.platform.MekanismAdapter;
import com.syaru.ae2machines.world.NativeCellWorlds;
import com.syaru.ae2machines.world.NativeWorldRuntime;
import java.math.BigInteger;
import java.util.List;
import java.util.Set;
import mekanism.api.recipes.ItemStackToItemStackRecipe;
import mekanism.api.recipes.cache.CachedRecipe;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import mekanism.common.tile.machine.*;
import mekanism.common.tile.prefab.TileEntityElectricMachine;
import mekanism.common.tile.prefab.TileEntityConfigurableMachine;
import mekanism.common.tile.factory.TileEntityItemStackToItemStackFactory;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Native multi-operation recipes over aggregated virtual lanes, never multiplied receipts. */
public final class MekWorldParallel {
    private static final Set<Class<?>> TYPES=Set.of(TileEntityCrusher.class,
            TileEntityEnrichmentChamber.class,TileEntityEnergizedSmelter.class,TileEntityItemStackToItemStackFactory.class);
    private MekWorldParallel() {}
    public static boolean supports(BlockEntity machine){return machine!=null && TYPES.contains(machine.getClass());}
    public static boolean chemical(BlockEntity machine){return machine!=null && (machine.getClass()==TileEntityChemicalOxidizer.class
            || machine.getClass()==TileEntityChemicalInfuser.class || machine.getClass()==TileEntityRotaryCondensentrator.class
            || machine.getClass()==TileEntityElectrolyticSeparator.class || machine.getClass()==TileEntityChemicalWasher.class
            || machine.getClass()==TileEntityChemicalCrystallizer.class || machine.getClass()==TileEntityIsotopicCentrifuge.class
            || machine.getClass()==TileEntityPressurizedReactionChamber.class);}
    public static boolean supportsWorld(BlockEntity machine){return supports(machine) || chemical(machine) || MekRecipeMachines.supports(machine);}
    public static boolean requiresWorld(BlockEntity machine){return MekRecipeMachines.supports(machine) || chemical(machine)
            && (!com.syaru.ae2machines.runtime.NativeMachines.chemicals() || machine.getClass()!=TileEntityChemicalOxidizer.class
            && machine.getClass()!=TileEntityChemicalInfuser.class && machine.getClass()!=TileEntityRotaryCondensentrator.class);}
    public static int lanes(BlockEntity machine) {
        if(MekRestorationStorage.projecting())return 1;
        if(machine==null || !(machine.getLevel() instanceof ServerLevel level) || !NativeCellWorlds.owns(level)
                || !supportsWorld(machine) && !MekEvaporationParallel.supports(machine) && !MekStructureParallel.supports(machine) && !MekTransportParallel.supports(machine))return 1;
        return NativeWorldRuntime.get(level).state().nativeLanes();
    }
    public static int slotLimit(BlockEntity machine,int original) {
        return (int)Math.min(Integer.MAX_VALUE,(long)original*lanes(machine));
    }
    public static void attach(List<BlockEntity> machines){for(var machine:machines){
        prepare(machine);
        MekTransportParallel.prepare(machine,false);
        if(machine instanceof mekanism.common.lib.multiblock.IMultiblock<?> tile && machine.getLevel()!=null)
            MekStructureParallel.prepare(tile.getMultiblock(),machine.getLevel());
    }}
    public static void levelAssigned(BlockEntity block) {
        MekTransportParallel.prepare(block,true);
        if(!supportsWorld(block))return;
        var machine=(TileEntityConfigurableMachine)block;
        for(var energy:machine.getEnergyContainers(null))if(energy instanceof MekWorldEnergyAccess access)
            access.machines$levelAssigned(block.getLevel() instanceof ServerLevel level && NativeCellWorlds.owns(level));
    }
    public static void prepare(BlockEntity block) {
        if(com.syaru.ae2machines.runtime.NativeWorldBatches.executing(block))return;
        if(!supportsWorld(block) || !(block.getLevel() instanceof ServerLevel level) || !NativeCellWorlds.owns(level))return;
        var machine=(TileEntityConfigurableMachine)block;
        for(var slot:machine.getInventorySlots(null))if(MekRecipeMachines.processingSlot(slot))
            ((MekVirtualSlot)slot).machines$owner(machine);
        com.syaru.ae2machines.platform.MekPassiveChemical.prepare(machine);
        if(machine.getClass().getName().equals("fr.iglee42.evolvedmekanism.tiles.machine.TileEntityMelter"))
            for(var heat:machine.getHeatCapacitors(null))if(heat instanceof MekStructureHeat scaled)scaled.machines$structureOwner(machine);
    }
    public static boolean canConfigure(List<BlockEntity> machines,BigInteger lanes) {
        boolean present=false;
        for(var machine:machines)if(supportsWorld(machine)) {
            present=true;
            for(int i=0;i<MekRecipeMachines.processes((TileEntityConfigurableMachine)machine);i++)if(progress((TileEntityConfigurableMachine)machine,i)>0)return false;
            if(!fits((TileEntityConfigurableMachine)machine,lanes))return false;
        }
        for(var machine:machines)if(MekEvaporationParallel.supports(machine)) {
            present=true;if(!MekEvaporationParallel.idle(machine) || !MekEvaporationParallel.fits(machine,lanes))return false;
        }
        for(var machine:machines)if(MekStructureParallel.supports(machine)) {
            present=true;if(!MekStructureParallel.fits(machine,lanes))return false;
        }
        for(var machine:machines)if(MekTransportParallel.supports(machine)){
            present=true;if(!MekTransportParallel.fits(machine,lanes))return false;
        }
        return present;
    }
    public static boolean canRestore(BlockEntity machine){return MekTransportParallel.fits(machine,BigInteger.ONE) && MekEvaporationParallel.fits(machine,BigInteger.ONE) && MekStructureParallel.fits(machine,BigInteger.ONE)
            && (!supportsWorld(machine) || fits((TileEntityConfigurableMachine)machine,BigInteger.ONE));}
    public static boolean hasParallelWork(BlockEntity block) {
        if(MekEvaporationParallel.supports(block))return lanes(block)>1 && !MekEvaporationParallel.idle(block);
        if(!supportsWorld(block) || !(block.getLevel() instanceof ServerLevel level) || !NativeCellWorlds.owns(level))return false;
        var machine=(TileEntityConfigurableMachine)block;var state=NativeWorldRuntime.get(level).state();
        for(int i=0;i<MekRecipeMachines.processes(machine);i++)if(progress(machine,i)>0 && state.processWork(block.getBlockPos().asLong(),i)>1)return true;
        return false;
    }
    private static boolean fits(TileEntityConfigurableMachine machine,BigInteger lanes) {
        for(var slot:machine.getInventorySlots(null))if(MekRecipeMachines.processingSlot(slot)) {
            int base=((MekVirtualSlot)slot).machines$nativeLimit(slot.getStack());
            if(BigInteger.valueOf(slot.getCount()).compareTo(BigInteger.valueOf(base).multiply(lanes))>0)return false;
        }
        if(!com.syaru.ae2machines.platform.MekPassiveChemical.fits(machine,lanes))return false;
        for(var energy:machine.getEnergyContainers(null))if(energy instanceof MekWorldEnergyAccess access
                && new java.math.BigDecimal(String.valueOf(energy.getEnergy())).movePointRight(4).toBigIntegerExact().compareTo(access.machines$nativeCapacity().multiply(lanes))>0)return false;
        return true;
    }
    public static void bind(TileEntityElectricMachine machine,ItemStackToItemStackRecipe recipe,CachedRecipe<?> cached) {
        bind(machine,recipe,cached,0);
    }
    public static void bind(TileEntityConfigurableMachine machine,ItemStackToItemStackRecipe recipe,CachedRecipe<?> cached,int process) {
        if(com.syaru.ae2machines.runtime.NativeWorldBatches.executing(machine))return;
        if(!supports(machine) || !MekanismAdapter.fixedOutput(recipe))return;
        bindGroup(machine,cached,process);
    }
    public static int progress(TileEntityConfigurableMachine machine,int process) {
        return machine instanceof mekanism.common.tile.factory.TileEntityFactory<?> factory?factory.getProgress(process)
                :machine instanceof mekanism.common.tile.prefab.TileEntityProgressMachine<?> p?p.getOperatingTicks():0;
    }
    public static void bindGroup(TileEntityConfigurableMachine machine,CachedRecipe<?> cached,int process) {
        if(cached instanceof MekRecipeMachines.Usage usage)usage.machines$usageOwner(machine,process);
        var cachedAccess=(com.syaru.ae2machines.mixin.MekCachedOperationsAccess)(Object)cached;
        var baseline=cachedAccess.machines$baseline();var post=cachedAccess.machines$postProcess();
        // The supplier follows the owning cell, including detach/reinsert and recipe-cache reuse.
        cached.setBaselineMaxOperations(()->{
            if(progress(machine,process)>0)return operations(machine,process);
            return (int)Math.min(Integer.MAX_VALUE,(long)baseline.getAsInt()*operations(machine,process));
        });
        cached.setPostProcessOperations(tracker->{
            post.accept(tracker);
            if(!(machine.getLevel() instanceof ServerLevel level) || !NativeCellWorlds.owns(level))return;
            var runtime=NativeWorldRuntime.get(level);if(!runtime.running())return;
            var access=(com.syaru.ae2machines.mixin.MekOperationsAccess)(Object)tracker;
            int possible=Math.min(access.machines$currentMax(),access.machines$maxForEnergy());
            long pos=machine.getBlockPos().asLong();
            if(progress(machine,process)==0) {
                if(possible>0)runtime.state().processWork(pos,process,possible);
            }else if(possible<runtime.state().processWork(pos,process)) {
                // A native progress tick belongs to the entire paid group, not newly arriving input.
                tracker.updateOperations(0);
            }
        });
    }
    public static int operations(BlockEntity machine) {
        return operations(machine,0);
    }
    public static int operations(BlockEntity machine,int process) {
        if(!(machine.getLevel() instanceof ServerLevel level) || !NativeCellWorlds.owns(level))return 1;
        var runtime=NativeWorldRuntime.get(level);
        if(!runtime.running())return 1;
        if(progress((TileEntityConfigurableMachine)machine,process)>0)return runtime.state().processWork(machine.getBlockPos().asLong(),process);
        runtime.state().processWork(machine.getBlockPos().asLong(),process,0);
        // This is the native int operation API, not a conversion of a job's exact total.
        return runtime.state().nativeLanes();
    }
}
