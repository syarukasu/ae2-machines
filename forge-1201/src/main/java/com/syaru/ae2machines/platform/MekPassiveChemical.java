package com.syaru.ae2machines.platform;

import com.syaru.ae2machines.compat.mek.*;
import com.syaru.ae2machines.runtime.NativeWorldBatches;
import com.syaru.ae2machines.world.NativeCellWorlds;
import java.math.BigInteger;
import mekanism.api.recipes.cache.CachedRecipe;
import mekanism.common.tile.prefab.TileEntityConfigurableMachine;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Native gas/fluid processing also works without Applied Mekanistics or ACO. */
public final class MekPassiveChemical {
    private MekPassiveChemical() {}
    public static long turbineSteam(mekanism.common.lib.multiblock.MultiblockData data){return data.getGasTanks(null).get(0).getStored();}
    public static long fissionFuel(mekanism.common.lib.multiblock.MultiblockData data){return data.getGasTanks(null).get(0).getStored();}
    public static long fusionFuel(mekanism.common.lib.multiblock.MultiblockData data){return data.getGasTanks(null).get(2).getStored();}
    private static java.util.List<mekanism.api.chemical.IChemicalTank<?,?>> structureTanks(mekanism.common.lib.multiblock.MultiblockData data){
        var tanks=new java.util.ArrayList<mekanism.api.chemical.IChemicalTank<?,?>>();tanks.addAll(data.getGasTanks(null));tanks.addAll(data.getInfusionTanks(null));tanks.addAll(data.getPigmentTanks(null));tanks.addAll(data.getSlurryTanks(null));return tanks;
    }
    public static void prepareStructure(mekanism.common.lib.multiblock.MultiblockData data,BlockEntity owner){
        for(var tank:structureTanks(data))if(tank instanceof MekVirtualTank virtual)virtual.machines$owner(owner);
    }
    public static boolean fitsStructure(mekanism.common.lib.multiblock.MultiblockData data,BigInteger lanes){
        for(var tank:structureTanks(data))if(!(tank instanceof MekVirtualTank virtual) || BigInteger.valueOf(tank.getStored()).compareTo(BigInteger.valueOf(virtual.machines$nativeCapacity()).multiply(lanes))>0)return false;return true;
    }
    public static void prepare(TileEntityConfigurableMachine machine) {
        for(var tank:machine.getGasTanks(null))((MekVirtualTank)tank).machines$owner(machine);
        for(var tank:machine.getSlurryTanks(null))((MekVirtualTank)tank).machines$owner(machine);
        for(var tank:machine.getInfusionTanks(null))((MekVirtualTank)tank).machines$owner(machine);
        for(var tank:machine.getPigmentTanks(null))((MekVirtualTank)tank).machines$owner(machine);
        for(var tank:machine.getFluidTanks(null))((MekVirtualTank)tank).machines$owner(machine);
    }
    public static boolean fits(TileEntityConfigurableMachine machine,BigInteger lanes) {
        for(var tank:machine.getGasTanks(null))if(BigInteger.valueOf(tank.getStored()).compareTo(BigInteger.valueOf(((MekVirtualTank)tank).machines$nativeCapacity()).multiply(lanes))>0)return false;
        for(var tank:machine.getSlurryTanks(null))if(BigInteger.valueOf(tank.getStored()).compareTo(BigInteger.valueOf(((MekVirtualTank)tank).machines$nativeCapacity()).multiply(lanes))>0)return false;
        for(var tank:machine.getInfusionTanks(null))if(BigInteger.valueOf(tank.getStored()).compareTo(BigInteger.valueOf(((MekVirtualTank)tank).machines$nativeCapacity()).multiply(lanes))>0)return false;
        for(var tank:machine.getPigmentTanks(null))if(BigInteger.valueOf(tank.getStored()).compareTo(BigInteger.valueOf(((MekVirtualTank)tank).machines$nativeCapacity()).multiply(lanes))>0)return false;
        for(var tank:machine.getFluidTanks(null))if(MekWideFluidTank.amount(tank).compareTo(BigInteger.valueOf(((MekVirtualTank)tank).machines$nativeCapacity()).multiply(lanes))>0)return false;
        return true;
    }
    public static void bind(BlockEntity block,CachedRecipe<?> cached) {
        if(NativeWorldBatches.executing(block) || !MekWorldParallel.chemical(block)
                || !(block.getLevel() instanceof ServerLevel level) || !NativeCellWorlds.owns(level) || !independent(cached.getRecipe()))return;
        MekWorldParallel.prepare(block);
        MekWorldParallel.bindGroup((TileEntityConfigurableMachine)block,cached,0);
    }
    public static boolean independent(Object recipe) {
        if(!recipe.getClass().getName().startsWith("mekanism.common.recipe.impl."))return false;
        if(recipe instanceof mekanism.api.recipes.ItemStackToGasRecipe r)return pure(r.getInput());
        if(recipe instanceof mekanism.api.recipes.ChemicalInfuserRecipe r)return pure(r.getLeftInput()) && pure(r.getRightInput());
        if(recipe instanceof mekanism.api.recipes.ElectrolysisRecipe r)return pure(r.getInput());
        if(recipe instanceof mekanism.api.recipes.FluidSlurryToSlurryRecipe r)return pure(r.getFluidInput()) && pure(r.getChemicalInput());
        if(recipe instanceof mekanism.api.recipes.ChemicalCrystallizerRecipe r)return pure(r.getInput());
        if(recipe instanceof mekanism.api.recipes.GasToGasRecipe r)return pure(r.getInput());
        if(recipe instanceof mekanism.api.recipes.PressurizedReactionRecipe r)return pure(r.getInputSolid()) && pure(r.getInputFluid()) && pure(r.getInputGas());
        if(recipe instanceof mekanism.api.recipes.FluidToFluidRecipe r)return pure(r.getInput());
        if(recipe instanceof mekanism.api.recipes.RotaryRecipe r)return (!r.hasFluidToGas() || pure(r.getFluidInput())) && (!r.hasGasToFluid() || pure(r.getGasInput()));
        return false;
    }
    private static boolean pure(Object ingredient){return ingredient.getClass().getName().startsWith("mekanism.common.recipe.ingredient.");}
}
