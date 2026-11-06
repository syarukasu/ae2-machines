package com.syaru.ae2machines.platform;

import com.syaru.ae2machines.compat.mek.MekRecipeState.Asset;
import com.syaru.ae2machines.mixin.MekRecipeEnergyAccess;
import java.math.BigInteger;
import java.util.*;
import mekanism.api.recipes.cache.CachedRecipe;
import mekanism.api.chemical.*;
import mekanism.common.tile.prefab.TileEntityConfigurableMachine;

public final class MekRecipeTime {
    private MekRecipeTime(){}
    public static BigInteger energyPerTick(CachedRecipe<?> cached) {
        return MekanismAdapter.units(((MekRecipeEnergyAccess)cached).machines$recipeEnergy().get().toString());
    }
    public static boolean quietChemistry(TileEntityConfigurableMachine machine,CachedRecipe<?> cached) {
        var radiation=mekanism.api.chemical.gas.attribute.GasAttributes.Radiation.class;
        if(machine.getRadiationScale()!=0 || !machine.getHeatCapacitors(null).isEmpty()
                || machine.getGasTanks(null).stream().anyMatch(t->!t.isEmpty() && t.getType().has(radiation)))return false;
        return !(cached.getRecipe() instanceof mekanism.api.recipes.ItemStackToGasRecipe recipe)
                || recipe.getOutputDefinition().stream().noneMatch(s->s.getType().has(radiation));
    }
    public static List<Asset> tanks(TileEntityConfigurableMachine machine) {
        var result=new ArrayList<Asset>();
        for(var tank:machine.getFluidTanks(null))
            result.add(Asset.capture(tank::getFluid,tank::setStack,net.minecraftforge.fluids.FluidStack::copy,
                    net.minecraftforge.fluids.FluidStack::isFluidStackIdentical));
        for(var tank:machine.getGasTanks(null))result.add(chemical(tank));
        for(var tank:machine.getInfusionTanks(null))result.add(chemical(tank));
        for(var tank:machine.getPigmentTanks(null))result.add(chemical(tank));
        for(var tank:machine.getSlurryTanks(null))result.add(chemical(tank));
        return result;
    }
    private static <C extends Chemical<C>,S extends ChemicalStack<C>> Asset chemical(IChemicalTank<C,S> tank) {
        return Asset.capture(tank::getStack,tank::setStack,s->tank.createStack(s,s.getAmount()),ChemicalStack::isStackIdentical);
    }
}
