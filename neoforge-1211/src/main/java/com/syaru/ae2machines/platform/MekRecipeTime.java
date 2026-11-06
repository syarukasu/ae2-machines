package com.syaru.ae2machines.platform;

import com.syaru.ae2machines.compat.mek.MekRecipeState.Asset;
import com.syaru.ae2machines.mixin.MekRecipeEnergyAccess;
import java.math.BigInteger;
import java.util.*;
import mekanism.api.recipes.cache.CachedRecipe;
import mekanism.api.chemical.ChemicalStack;
import mekanism.common.tile.prefab.TileEntityConfigurableMachine;

public final class MekRecipeTime {
    private MekRecipeTime(){}
    public static BigInteger energyPerTick(CachedRecipe<?> cached) {
        return MekanismAdapter.units(((MekRecipeEnergyAccess)cached).machines$recipeEnergy().getAsLong());
    }
    public static boolean quietChemistry(TileEntityConfigurableMachine machine,CachedRecipe<?> cached) {
        var radiation=mekanism.api.chemical.attribute.ChemicalAttributes.Radiation.class;
        if(machine.getRadiationScale()!=0 || !machine.getHeatCapacitors(null).isEmpty()
                || machine.getChemicalTanks(null).stream().anyMatch(t->!t.isEmpty() && t.getStack().getChemical().has(radiation)))return false;
        return !(cached.getRecipe() instanceof mekanism.api.recipes.ItemStackToChemicalRecipe recipe)
                || recipe.getOutputDefinition().stream().noneMatch(s->s.getChemical().has(radiation));
    }
    public static List<Asset> tanks(TileEntityConfigurableMachine machine) {
        var result=new ArrayList<Asset>();
        for(var tank:machine.getFluidTanks(null))
            result.add(Asset.capture(tank::getFluid,tank::setStack,net.neoforged.neoforge.fluids.FluidStack::copy,
                    net.neoforged.neoforge.fluids.FluidStack::matches));
        for(var tank:machine.getChemicalTanks(null))
            result.add(Asset.capture(tank::getStack,tank::setStack,ChemicalStack::copy,Objects::equals));
        return result;
    }
}
