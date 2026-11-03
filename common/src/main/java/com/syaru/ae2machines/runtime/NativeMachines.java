package com.syaru.ae2machines.runtime;

import appeng.api.stacks.AEKey;
import com.syaru.ae2machines.platform.*;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.math.BigInteger;
import java.util.*;

/** Optional integrations are linked only after their native API is present. */
public final class NativeMachines {
    private NativeMachines() {}
    public static boolean craftingCpu(BlockEntity block) {
        return block!=null && craftingCpuType(block.getClass());
    }
    static boolean craftingCpuType(Class<?> candidate) {
        for(Class<?> type=candidate;type!=null;type=type.getSuperclass())
            if(type==appeng.blockentity.crafting.CraftingBlockEntity.class
                    || type.getName().equals(com.syaru.ae2machines.world.RecoveryParticipants.ADVANCED_CPU))return true;
        return false;
    }
    public static boolean mek() { return present("mekanism.api.recipes.ItemStackToItemStackRecipe"); }
    public static boolean chemicals() { return mek() && present("me.ramidzkh.mekae2.ae2.MekanismKey"); }
    public static boolean gt() { return present("com.gregtechceu.gtceu.api.machine.SimpleTieredMachine"); }
    private static boolean present(String name) {
        try { Class.forName(name,false,NativeMachines.class.getClassLoader());return true; }
        catch(ClassNotFoundException|LinkageError e) { return false; }
    }
    public static boolean supports(BlockEntity be) {
        return NativeAssemblers.supports(be) || VanillaFurnaces.supports(be) || mek() && MekanismAdapter.supports(be) || gt() && (GregTechAdapter.supports(be) || GtMultiblockAdapter.supports(be));
    }
    public static boolean multiblock(BlockEntity be) { return gt() && GtMultiblockAdapter.supports(be); }
    public static boolean buffered(BlockEntity be) {
        return be!=null && (NativeAssemblers.supports(be) || VanillaFurnaces.supports(be) || mek() && MekanismAdapter.buffered(be) || isGt(be));
    }
    public static BufferedMachine buffer(BlockEntity be,Runnable changed) {
        if(NativeAssemblers.supports(be))throw new IllegalArgumentException("ASSEMBLER_OWNED_BY_NATIVE_NETWORK");
        if(!buffered(be))throw new IllegalArgumentException("NATIVE_BUFFER_UNSUPPORTED");
        if(VanillaFurnaces.supports(be))return VanillaFurnaces.buffer(be,changed);
        return isGt(be)?GtMaterialAccess.create(be,changed):MekanismAdapter.buffer(be,changed);
    }
    public static MachineProfile idleProfile(BlockEntity be) {
        if(multiblock(be))return GtMultiblockAdapter.worldIdleProfile(be);
        if(NativeAssemblers.supports(be))return VanillaFurnaces.profile();
        if(VanillaFurnaces.supports(be))return VanillaFurnaces.profile();
        return isGt(be)?GregTechAdapter.idleProfile(be):MekanismAdapter.idleProfile(be);
    }
    public static void verifyPassiveBoundary(BlockEntity be,Set<net.minecraft.core.BlockPos> captured) {
        if(NativeAssemblers.supports(be)){
            if(PlatformData.saveBlock(be).contains("myPlan"))throw new IllegalArgumentException("ASSEMBLER_HAS_CPU_OWNED_WORK");
            return;
        }
        if(VanillaFurnaces.supports(be))return;
        if(isGt(be))GregTechAdapter.verifyPassiveBoundary(be,captured);else MekanismAdapter.verifyPassiveBoundary(be,captured);
    }
    public static Set<net.minecraft.core.BlockPos> members(BlockEntity be) {
        return multiblock(be)?GtMultiblockAdapter.members(be):Set.of(be.getBlockPos());
    }
    public static boolean reaches(BlockEntity be,net.minecraft.core.BlockPos provider) {
        return multiblock(be)?GtMultiblockAdapter.reaches(be,provider):be.getBlockPos().distManhattan(provider)==1;
    }
    private static boolean isGt(BlockEntity be) { return gt() && GregTechAdapter.supports(be); }
    public static Optional<MachineProfile> inspect(BlockEntity be,Map<AEKey,BigInteger> inputs) {
        if(multiblock(be))return GtMultiblockAdapter.inspectWorld(be,inputs);
        if(NativeAssemblers.supports(be))return Optional.empty();
        if(VanillaFurnaces.supports(be))return Optional.empty();
        return isGt(be)?GregTechAdapter.inspect(be,inputs):MekanismAdapter.inspect(be,inputs);
    }
    public static void verifyIdleBoundary(BlockEntity be,Direction side) {
        if(NativeAssemblers.supports(be))throw new IllegalArgumentException("ASSEMBLER_NATIVE_PROVIDER_PENDING");
        if(VanillaFurnaces.supports(be))throw new IllegalArgumentException("FURNACE_REQUIRES_NATIVE_PROVIDER_ROUTE");
        if(isGt(be))GregTechAdapter.verifyIdleBoundary(be,side);else MekanismAdapter.verifyIdleBoundary(be,side);
    }
    public static void verifyRecipeRoute(BlockEntity be,Direction side,MachineProfile p) {
        if(NativeAssemblers.supports(be))throw new IllegalArgumentException("ASSEMBLER_NATIVE_PROVIDER_PENDING");
        if(VanillaFurnaces.supports(be))throw new IllegalArgumentException("FURNACE_REQUIRES_NATIVE_PROVIDER_ROUTE");
        if(isGt(be))GregTechAdapter.verifyRecipeRoute(be,side,p);else MekanismAdapter.verifyRecipeRoute(be,side,p);
    }
    public static void setStoredEnergy(BlockEntity be,BigInteger stored) {
        if(multiblock(be)){GtMultiblockAdapter.worldEnergy(be,stored);return;}
        if(NativeAssemblers.supports(be)){if(stored.signum()!=0)throw new IllegalArgumentException("ASSEMBLER_USES_GRID_POWER");return;}
        if(VanillaFurnaces.supports(be)){if(stored.signum()!=0)throw new IllegalArgumentException("FURNACE_HAS_NO_ELECTRIC_BUFFER");return;}
        if(isGt(be))GregTechAdapter.setStoredEnergy(be,stored);else MekanismAdapter.setStoredEnergy(be,stored);
    }
    public static BigInteger energy(BlockEntity be) { return multiblock(be)?GtMultiblockAdapter.worldEnergy(be):NativeAssemblers.supports(be) || VanillaFurnaces.supports(be)?BigInteger.ZERO:isGt(be)?GregTechAdapter.energy(be):MekanismAdapter.energy(be); }
    public static long feedEU(BlockEntity be,long voltage,long amps) { return isGt(be)?GregTechAdapter.feedEU(be,voltage,amps):0; }
}
