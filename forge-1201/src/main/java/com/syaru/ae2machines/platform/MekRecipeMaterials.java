package com.syaru.ae2machines.platform;

import appeng.api.stacks.*;
import java.math.BigInteger;
import java.util.*;
import mekanism.api.*;
import mekanism.api.chemical.*;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.inventory.slot.*;
import mekanism.common.tile.prefab.TileEntityConfigurableMachine;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/** Scratch-only native slots/tanks; no real machine or ME inventory is modified. */
@SuppressWarnings({"rawtypes","unchecked"})
public final class MekRecipeMaterials {
    private final TileEntityConfigurableMachine machine;
    private final List<IChemicalTank> chemicals=new ArrayList<>();
    private final Set<Object> inputs=Collections.newSetFromMap(new IdentityHashMap<>());
    public MekRecipeMaterials(TileEntityConfigurableMachine machine,Map<AEKey,BigInteger> offered) {
        this.machine=machine;
        chemicals.addAll(machine.getGasTanks(null));chemicals.addAll(machine.getInfusionTanks(null));
        chemicals.addAll(machine.getPigmentTanks(null));chemicals.addAll(machine.getSlurryTanks(null));
        for(var slot:machine.getInventorySlots(null))slot.setEmpty();
        for(var tank:machine.getFluidTanks(null))tank.setEmpty();
        for(var tank:chemicals)tank.setEmpty();
        for(var slot:machine.getInventorySlots(null))if(slot instanceof InputInventorySlot) {
            for(var entry:offered.entrySet())if(entry.getKey() instanceof AEItemKey item) {
                var stack=item.toStack(entry.getValue().intValueExact());
                if(slot.isItemValid(stack)){slot.setStack(stack);inputs.add(slot);break;}
            }
        }
        for(var tank:machine.getFluidTanks(null))for(var entry:offered.entrySet())if(entry.getKey() instanceof AEFluidKey fluid) {
            var stack=new FluidStack(fluid.getFluid(),entry.getValue().intValueExact(),fluid.getTag());
            if(tank.insert(stack,Action.SIMULATE,AutomationType.EXTERNAL).getAmount()<stack.getAmount()) {
                tank.setStack(stack);inputs.add(tank);break;
            }
        }
        for(var tank:chemicals)for(var entry:offered.entrySet())if(entry.getKey() instanceof MekanismKey chemical) {
            var stack=chemical.withAmount(entry.getValue().longValueExact());
            if(stack.getClass()!=tank.getEmptyStack().getClass())continue;
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
        for(var tank:chemicals)if(inputs.contains(tank)==input && !tank.isEmpty())add(result,MekanismKey.of(tank.getStack()),tank.getStored());
        return result;
    }
    private static void add(Map<AEKey,BigInteger> result,AEKey key,long n){result.merge(Objects.requireNonNull(key),BigInteger.valueOf(n),BigInteger::add);}
    public static String id(TileEntityConfigurableMachine machine,Object recipe){return ((mekanism.api.recipes.MekanismRecipe)recipe).getId().toString();}
    public static boolean fixedIngredient(Object ingredient){
        if(ingredient instanceof mekanism.common.recipe.ingredient.IMultiIngredient<?,?> multi)return multi.getIngredients().stream().allMatch(MekRecipeMaterials::fixedIngredient);
        if(ingredient instanceof mekanism.common.recipe.ingredient.creator.ItemStackIngredientCreator.SingleItemStackIngredient item)return fixedItem(item.getInputRaw());
        return ingredient.getClass().getName().startsWith("mekanism.common.recipe.ingredient.");
    }
    public static boolean fixedUsage(Object recipe){return true;}
    private static boolean fixedItem(net.minecraft.world.item.crafting.Ingredient ingredient){
        if(NativeIngredients.fixedItem(ingredient))return true;
        if(ingredient.getClass()==net.minecraftforge.common.crafting.CompoundIngredient.class)return ((net.minecraftforge.common.crafting.CompoundIngredient)ingredient).getChildren().stream().allMatch(MekRecipeMaterials::fixedItem);
        if(ingredient.getClass()==net.minecraftforge.common.crafting.DifferenceIngredient.class)return fixedItem((net.minecraft.world.item.crafting.Ingredient)ingredientField(ingredient,"base"))
                && fixedItem((net.minecraft.world.item.crafting.Ingredient)ingredientField(ingredient,"subtracted"));
        if(ingredient.getClass()==net.minecraftforge.common.crafting.IntersectionIngredient.class)return ((List<net.minecraft.world.item.crafting.Ingredient>)ingredientField(ingredient,"children")).stream().allMatch(MekRecipeMaterials::fixedItem);
        return Set.of("net.minecraftforge.common.crafting.StrictNBTIngredient","net.minecraftforge.common.crafting.PartialNBTIngredient").contains(ingredient.getClass().getName());
    }
    private static Object ingredientField(Object ingredient,String name){try{var field=ingredient.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(ingredient);}catch(ReflectiveOperationException e){throw new IllegalStateException("Forge ingredient contract changed",e);}}
    public static void verifyRoute(TileEntityConfigurableMachine machine,net.minecraft.core.Direction side,com.syaru.ae2machines.runtime.MachineProfile profile){
        var relative=RelativeSide.fromDirections(machine.getDirection(),side);
        for(boolean output:new boolean[]{false,true})for(var key:(output?profile.recipe().outputs():profile.recipe().inputs()).keySet()) {
            var type=key instanceof AEItemKey?mekanism.common.lib.transmitter.TransmissionType.ITEM:key instanceof AEFluidKey?mekanism.common.lib.transmitter.TransmissionType.FLUID
                    :((MekanismKey)key).getStack() instanceof mekanism.api.chemical.infuse.InfusionStack?mekanism.common.lib.transmitter.TransmissionType.INFUSION
                    :((MekanismKey)key).getStack() instanceof mekanism.api.chemical.pigment.PigmentStack?mekanism.common.lib.transmitter.TransmissionType.PIGMENT
                    :((MekanismKey)key).getStack() instanceof mekanism.api.chemical.slurry.SlurryStack?mekanism.common.lib.transmitter.TransmissionType.SLURRY:mekanism.common.lib.transmitter.TransmissionType.GAS;
            var config=machine.getConfig().getConfig(type);var info=config==null?null:config.getSlotInfo(relative);
            boolean valid=output?config!=null && config.isEjecting() && java.util.Arrays.stream(RelativeSide.values()).map(config::getSlotInfo).anyMatch(s->s!=null && s.canOutput()):info!=null && info.canInput();
            if(!valid)throw new IllegalArgumentException("NATIVE_MATERIAL_ROUTE_NOT_CONFIGURED");
        }
    }
}
