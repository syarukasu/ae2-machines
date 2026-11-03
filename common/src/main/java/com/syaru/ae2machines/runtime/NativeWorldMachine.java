package com.syaru.ae2machines.runtime;

import appeng.api.stacks.AEKey;
import com.syaru.ae2machines.compat.mek.*;
import com.syaru.ae2machines.platform.*;
import java.math.BigInteger;
import java.util.*;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Optional aggregated batch executors, not an admission list for native world ticking. */
public final class NativeWorldMachine {
    private NativeWorldMachine() {}
    public static boolean supports(BlockEntity machine) {
        return machine!=null && (NativeMachines.gt() && (GregTechAdapter.supports(machine) || GtMultiblockAdapter.supports(machine))
                || NativeMachines.mek() && (MekWorldParallel.supports(machine) || NativeMachines.chemicals()
                && (MekChemicalAdapter.supports(machine) || MekExactRecipeWork.supports(machine))));
    }
    public static Optional<MachineProfile> inspect(BlockEntity machine,Map<AEKey,BigInteger> inputs) {
        if(NativeMachines.multiblock(machine))return GtMultiblockAdapter.inspectWorld(machine,inputs);
        if(NativeMachines.mek() && NativeMachines.chemicals() && MekExactRecipeWork.supports(machine))return MekExactRecipeWork.inspect(machine,inputs);
        return supports(machine)?NativeMachines.inspect(machine,inputs):Optional.empty();
    }
    public static NativeBatchProcessor processor(BlockEntity machine,MachineProfile profile) {
        if(NativeMachines.gt() && (GregTechAdapter.supports(machine) || GtMultiblockAdapter.supports(machine)))return GtNativeProcessor.inWorld(machine,profile.recipe());
        if(NativeMachines.mek() && MekExactRecipeWork.supports(machine))return new MekExactRecipeWork(machine,profile.recipe());
        return MekWorldParallel.supports(machine)?new MekNativeProcessor(machine,profile.recipe())
                :new MekChemicalNativeProcessor(machine,profile.recipe());
    }
    public static boolean idle(BlockEntity machine) {
        if(!supports(machine))return false;
        if(NativeMachines.multiblock(machine))return GtMultiblockAdapter.worldIdle(machine);
        if(NativeMachines.gt() && GregTechAdapter.supports(machine)) {
            try{GregTechAdapter.verifyIdleBoundary(machine,Direction.NORTH);return true;}catch(IllegalArgumentException unavailable){return false;}
        }
        var nativeMachine=(mekanism.common.tile.prefab.TileEntityConfigurableMachine)machine;
        if(nativeMachine.getControlType()!=mekanism.common.tile.interfaces.IRedstoneControl.RedstoneControl.DISABLED)return false;
        if(MekWorldParallel.supports(machine))return MekItemMachine.idle(nativeMachine)
                && nativeMachine.getInventorySlots(null).stream().allMatch(s->s.isEmpty());
        return (!(machine instanceof mekanism.common.tile.prefab.TileEntityProgressMachine<?> p) || p.getOperatingTicks()==0)
                && nativeMachine.getInventorySlots(null).stream().allMatch(s->s.isEmpty())
                && nativeMachine.getFluidTanks(null).stream().allMatch(t->t.isEmpty())
                && MekChemicalWork.chemicalsEmpty(nativeMachine);
    }
    public static void verifyRoute(BlockEntity machine,Direction side,MachineProfile profile) {
        if(NativeMachines.multiblock(machine)){GtMultiblockAdapter.worldSnapshot(machine);return;}
        if(NativeMachines.mek() && NativeMachines.chemicals() && MekExactRecipeWork.supports(machine)){
            MekRecipeMaterials.verifyRoute((mekanism.common.tile.prefab.TileEntityConfigurableMachine)machine,side,profile);return;
        }
        NativeMachines.verifyRecipeRoute(machine,side,profile);
    }
    public static BigInteger energy(BlockEntity machine){return NativeMachines.multiblock(machine)?GtMultiblockAdapter.worldEnergy(machine):NativeMachines.energy(machine);}
    public static void energy(BlockEntity machine,BigInteger amount){if(NativeMachines.multiblock(machine))GtMultiblockAdapter.worldEnergy(machine,amount);else NativeMachines.setStoredEnergy(machine,amount);}
    public static MachineProfile idleProfile(BlockEntity machine){return NativeMachines.multiblock(machine)?GtMultiblockAdapter.worldIdleProfile(machine):NativeMachines.idleProfile(machine);}
    public static Set<net.minecraft.core.BlockPos> members(BlockEntity machine){return NativeMachines.multiblock(machine)?GtMultiblockAdapter.worldOwnedBlocks(machine):Set.of(machine.getBlockPos());}
    public static NativeCompletion completion(BlockEntity machine,net.minecraft.nbt.CompoundTag saved) {
        return NativeMachines.multiblock(machine)?GtMultiblockAdapter.worldCompletion(machine,saved):null;
    }
}
