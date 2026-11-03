package com.syaru.ae2machines.platform;

import com.syaru.ae2machines.compat.mek.*;
import com.syaru.ae2machines.runtime.NativeWorldBatches;
import com.syaru.ae2machines.world.NativeCellWorlds;
import java.math.BigInteger;
import java.util.Set;
import mekanism.api.recipes.cache.CachedRecipe;
import mekanism.common.tile.prefab.TileEntityConfigurableMachine;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Native chemical/fluid processing also works without Applied Mekanistics or ACO. */
public final class MekPassiveChemical {
    private MekPassiveChemical() {}
    public static long fusionFuel(mekanism.common.lib.multiblock.MultiblockData data){return data.getChemicalTanks(null).get(2).getStored();}
    public static void prepareStructure(mekanism.common.lib.multiblock.MultiblockData data,BlockEntity owner){
        for(var tank:data.getChemicalTanks(null))if(tank instanceof MekVirtualTank virtual)virtual.machines$owner(owner);
    }
    public static boolean fitsStructure(mekanism.common.lib.multiblock.MultiblockData data,BigInteger lanes){
        for(var tank:data.getChemicalTanks(null))if(!(tank instanceof MekVirtualTank virtual) || BigInteger.valueOf(tank.getStored()).compareTo(BigInteger.valueOf(virtual.machines$nativeCapacity()).multiply(lanes))>0)return false;return true;
    }
    public static void prepare(TileEntityConfigurableMachine machine) {
        for(var tank:machine.getChemicalTanks(null))((MekVirtualTank)tank).machines$owner(machine);
        for(var tank:machine.getFluidTanks(null))((MekVirtualTank)tank).machines$owner(machine);
    }
    public static boolean fits(TileEntityConfigurableMachine machine,BigInteger lanes) {
        for(var tank:machine.getChemicalTanks(null))if(BigInteger.valueOf(tank.getStored()).compareTo(BigInteger.valueOf(((MekVirtualTank)tank).machines$nativeCapacity()).multiply(lanes))>0)return false;
        for(var tank:machine.getFluidTanks(null))if(BigInteger.valueOf(tank.getFluidAmount()).compareTo(BigInteger.valueOf(((MekVirtualTank)tank).machines$nativeCapacity()).multiply(lanes))>0)return false;
        return true;
    }
    public static void bind(BlockEntity block,CachedRecipe<?> cached) {
        if(NativeWorldBatches.executing(block) || !MekWorldParallel.chemical(block)
                || !(block.getLevel() instanceof ServerLevel level) || !NativeCellWorlds.owns(level) || !independent(cached.getRecipe()))return;
        MekWorldParallel.prepare(block);
        MekWorldParallel.bindGroup((TileEntityConfigurableMachine)block,cached,0);
    }
    public static boolean independent(Object recipe) {
        if(recipe.getClass()==mekanism.api.recipes.basic.BasicChemicalOxidizerRecipe.class)
            return NativeIngredients.fixedItem(((mekanism.api.recipes.ItemStackToChemicalRecipe)recipe).getInput().ingredient().ingredient());
        if(recipe.getClass()==mekanism.api.recipes.basic.BasicChemicalInfuserRecipe.class) {
            var r=(mekanism.api.recipes.ChemicalChemicalToChemicalRecipe)recipe;return pure(r.getLeftInput()) && pure(r.getRightInput());
        }
        if(recipe.getClass()==mekanism.api.recipes.basic.BasicElectrolysisRecipe.class)
            return pure(((mekanism.api.recipes.ElectrolysisRecipe)recipe).getInput());
        if(recipe.getClass()==mekanism.api.recipes.basic.BasicFluidToFluidRecipe.class)
            return pure(((mekanism.api.recipes.FluidToFluidRecipe)recipe).getInput());
        if(recipe.getClass()==mekanism.api.recipes.basic.BasicWashingRecipe.class) {
            var r=(mekanism.api.recipes.FluidChemicalToChemicalRecipe)recipe;return pure(r.getFluidInput()) && pure(r.getChemicalInput());
        }
        if(recipe.getClass()==mekanism.api.recipes.basic.BasicChemicalCrystallizerRecipe.class)
            return pure(((mekanism.api.recipes.ChemicalCrystallizerRecipe)recipe).getInput());
        if(recipe.getClass()==mekanism.api.recipes.basic.BasicCentrifugingRecipe.class)
            return pure(((mekanism.api.recipes.ChemicalToChemicalRecipe)recipe).getInput());
        if(recipe.getClass()==mekanism.api.recipes.basic.BasicPressurizedReactionRecipe.class) {
            var r=(mekanism.api.recipes.PressurizedReactionRecipe)recipe;
            return NativeIngredients.fixedItem(r.getInputSolid().ingredient().ingredient()) && pure(r.getInputFluid()) && pure(r.getInputChemical());
        }
        if(recipe.getClass()==mekanism.api.recipes.basic.BasicRotaryRecipe.class) {
            var r=(mekanism.api.recipes.RotaryRecipe)recipe;
            return (!r.hasChemicalToFluid() || pure(r.getChemicalInput())) && (!r.hasFluidToChemical()
                    || Set.of(net.neoforged.neoforge.fluids.crafting.SingleFluidIngredient.class,net.neoforged.neoforge.fluids.crafting.TagFluidIngredient.class).contains(r.getFluidInput().ingredient().ingredient().getClass()));
        }
        return false;
    }
    private static boolean pure(mekanism.api.recipes.ingredients.ChemicalStackIngredient ingredient) {
        return Set.of(mekanism.api.recipes.ingredients.chemical.SingleChemicalIngredient.class,mekanism.api.recipes.ingredients.chemical.TagChemicalIngredient.class).contains(ingredient.ingredient().getClass());
    }
    private static boolean pure(mekanism.api.recipes.ingredients.FluidStackIngredient ingredient) {
        return Set.of(net.neoforged.neoforge.fluids.crafting.SingleFluidIngredient.class,net.neoforged.neoforge.fluids.crafting.TagFluidIngredient.class).contains(ingredient.ingredient().ingredient().getClass());
    }
}
