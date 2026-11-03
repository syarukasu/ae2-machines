package com.syaru.ae2machines.platform;

import appeng.api.stacks.*;
import java.math.BigInteger;
import java.util.*;
import mekanism.api.*;
import mekanism.api.chemical.*;
import mekanism.common.inventory.slot.*;
import mekanism.common.tile.prefab.TileEntityConfigurableMachine;
import me.ramidzkh.mekae2.ae2.MekanismKey;

/** Scratch-only native slots/tanks; no real machine or ME inventory is modified. */
public final class MekRecipeMaterials {
    private final TileEntityConfigurableMachine machine;
    private final Set<Object> inputs=Collections.newSetFromMap(new IdentityHashMap<>());
    public MekRecipeMaterials(TileEntityConfigurableMachine machine,Map<AEKey,BigInteger> offered) {
        this.machine=machine;
        for(var slot:machine.getInventorySlots(null))slot.setEmpty();
        for(var tank:machine.getFluidTanks(null))tank.setEmpty();
        for(var tank:machine.getChemicalTanks(null))tank.setEmpty();
        for(var slot:machine.getInventorySlots(null))if(slot instanceof InputInventorySlot) {
            for(var entry:offered.entrySet())if(entry.getKey() instanceof AEItemKey item) {
                var stack=item.toStack(entry.getValue().intValueExact());
                if(slot.isItemValid(stack)){slot.setStack(stack);inputs.add(slot);break;}
            }
        }
        for(var tank:machine.getFluidTanks(null))for(var entry:offered.entrySet())if(entry.getKey() instanceof AEFluidKey fluid) {
            var stack=fluid.toStack(entry.getValue().intValueExact());
            if(tank.insert(stack,Action.SIMULATE,AutomationType.EXTERNAL).getAmount()<stack.getAmount()) {
                tank.setStack(stack);inputs.add(tank);break;
            }
        }
        for(var tank:machine.getChemicalTanks(null))for(var entry:offered.entrySet())if(entry.getKey() instanceof MekanismKey chemical) {
            var stack=chemical.withAmount(entry.getValue().longValueExact());
            if(tank.insert(stack,Action.SIMULATE,AutomationType.EXTERNAL).getAmount()<stack.getAmount()) {
                tank.setStack(stack);inputs.add(tank);break;
            }
        }
    }
    public Map<AEKey,BigInteger> inputs(){return amounts(true);}
    public Map<AEKey,BigInteger> outputs(){return amounts(false);}
    private Map<AEKey,BigInteger> amounts(boolean input) {
        var result=new LinkedHashMap<AEKey,BigInteger>();
        for(var slot:machine.getInventorySlots(null))if((input?inputs.contains(slot):slot instanceof OutputInventorySlot) && !slot.isEmpty())
            add(result,AEItemKey.of(slot.getStack()),slot.getCount());
        for(var tank:machine.getFluidTanks(null))if(inputs.contains(tank)==input && !tank.isEmpty())add(result,AEFluidKey.of(tank.getFluid()),tank.getFluidAmount());
        for(var tank:machine.getChemicalTanks(null))if(inputs.contains(tank)==input && !tank.isEmpty())add(result,MekanismKey.of(tank.getStack()),tank.getStored());
        return result;
    }
    private static void add(Map<AEKey,BigInteger> result,AEKey key,long n){result.merge(Objects.requireNonNull(key),BigInteger.valueOf(n),BigInteger::add);}
    public static String id(TileEntityConfigurableMachine machine,Object recipe){
        if(recipe.getClass().getName().equals("mekanism.common.recipe.impl.NutritionalLiquifierIRecipe")) {
            var stack=machine.getInventorySlots(null).stream().filter(s->s instanceof InputInventorySlot && !s.isEmpty()).findFirst().orElseThrow().getStack().copyWithCount(1);
            return "mekanism:nutritional_liquification/"+com.syaru.ae2machines.world.NbtWitness.digest(PlatformData.saveItem(stack,machine.getLevel()));
        }
        try {
            var type=machine.getClass().getMethod("getRecipeType").invoke(machine);
            var recipes=(List<?>)type.getClass().getMethod("getRecipes",net.minecraft.world.level.Level.class).invoke(type,machine.getLevel());
            return recipes.stream().map(h->(net.minecraft.world.item.crafting.RecipeHolder<?>)h).filter(h->h.value()==recipe).findFirst().orElseThrow().id().toString();
        }catch(ReflectiveOperationException e){throw new IllegalStateException("Mekanism recipe identity contract changed",e);}
    }
    public static boolean fixedIngredient(Object ingredient){
        if(ingredient instanceof mekanism.api.recipes.ingredients.ItemStackIngredient item)return fixedItem(item.ingredient().ingredient());
        if(ingredient instanceof mekanism.api.recipes.ingredients.ChemicalStackIngredient chemical)return Set.of(mekanism.api.recipes.ingredients.chemical.SingleChemicalIngredient.class,mekanism.api.recipes.ingredients.chemical.TagChemicalIngredient.class).contains(chemical.ingredient().getClass());
        if(ingredient instanceof mekanism.api.recipes.ingredients.FluidStackIngredient fluid)return Set.of(net.neoforged.neoforge.fluids.crafting.SingleFluidIngredient.class,net.neoforged.neoforge.fluids.crafting.TagFluidIngredient.class).contains(fluid.ingredient().ingredient().getClass());
        return false;
    }
    public static boolean fixedUsage(Object recipe){return !(recipe instanceof mekanism.api.recipes.ItemStackChemicalToObjectRecipe<?> chemical) || !chemical.perTickUsage();}
    private static boolean fixedItem(net.minecraft.world.item.crafting.Ingredient ingredient){
        if(NativeIngredients.fixedItem(ingredient))return true;
        var custom=ingredient.getCustomIngredient();if(custom==null)return false;
        if(custom.getClass()==net.neoforged.neoforge.common.crafting.DifferenceIngredient.class){var difference=(net.neoforged.neoforge.common.crafting.DifferenceIngredient)custom;return fixedItem(difference.base()) && fixedItem(difference.subtracted());}
        if(custom.getClass()==net.neoforged.neoforge.common.crafting.CompoundIngredient.class)return ((net.neoforged.neoforge.common.crafting.CompoundIngredient)custom).children().stream().allMatch(MekRecipeMaterials::fixedItem);
        if(custom.getClass()==net.neoforged.neoforge.common.crafting.IntersectionIngredient.class)return ((net.neoforged.neoforge.common.crafting.IntersectionIngredient)custom).children().stream().allMatch(MekRecipeMaterials::fixedItem);
        return custom.getClass()==net.neoforged.neoforge.common.crafting.DataComponentIngredient.class;
    }
    public static void verifyRoute(TileEntityConfigurableMachine machine,net.minecraft.core.Direction side,com.syaru.ae2machines.runtime.MachineProfile profile){
        var relative=RelativeSide.fromDirections(machine.getDirection(),side);
        for(boolean output:new boolean[]{false,true})for(var key:(output?profile.recipe().outputs():profile.recipe().inputs()).keySet()) {
            var type=key instanceof AEItemKey?mekanism.common.lib.transmitter.TransmissionType.ITEM:key instanceof AEFluidKey?mekanism.common.lib.transmitter.TransmissionType.FLUID:mekanism.common.lib.transmitter.TransmissionType.CHEMICAL;
            var config=machine.getConfig().getConfig(type);var info=config==null?null:config.getSlotInfo(relative);
            boolean valid=output?config!=null && config.isEjecting() && java.util.Arrays.stream(RelativeSide.values()).map(config::getSlotInfo).anyMatch(s->s!=null && s.canOutput()):info!=null && info.canInput();
            if(!valid)throw new IllegalArgumentException("NATIVE_MATERIAL_ROUTE_NOT_CONFIGURED");
        }
    }
}
