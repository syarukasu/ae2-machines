package com.syaru.ae2machines.compat.mek;

import mekanism.api.recipes.ItemStackToItemStackRecipe;
import mekanism.api.recipes.cache.CachedRecipe;
import mekanism.common.capabilities.energy.MachineEnergyContainer;
import mekanism.common.tile.factory.TileEntityItemStackToItemStackFactory;
import mekanism.common.tile.factory.TileEntityItemToItemFactory;
import mekanism.common.tile.prefab.TileEntityConfigurableMachine;
import mekanism.common.tile.prefab.TileEntityElectricMachine;
import net.minecraft.world.item.ItemStack;
import java.lang.reflect.Field;
import java.util.Arrays;

/** The factory keeps its native cached recipe; one private process represents an owned batch. */
public final class MekItemMachine {
    private static final Field SINGLE_INPUT = field(TileEntityElectricMachine.class, "inputHandler");
    private static final Field SINGLE_OUTPUT = field(TileEntityElectricMachine.class, "outputHandler");
    private static final Field FACTORY_INPUT = field(TileEntityItemToItemFactory.class, "inputHandlers");
    private static final Field FACTORY_OUTPUT = field(TileEntityItemToItemFactory.class, "outputHandlers");
    private MekItemMachine() {}
    public static boolean idle(TileEntityConfigurableMachine machine) {
        return machine instanceof TileEntityItemStackToItemStackFactory factory
                ? Arrays.stream(factory.progress).allMatch(p -> p == 0)
                : ((TileEntityElectricMachine) machine).getOperatingTicks() == 0;
    }
    public static int progress(TileEntityConfigurableMachine machine) {
        return progress(machine,0);
    }
    public static int progress(TileEntityConfigurableMachine machine,int process) {
        return machine instanceof TileEntityItemStackToItemStackFactory factory
                ? factory.getProgress(process) : ((TileEntityElectricMachine) machine).getOperatingTicks();
    }
    public static int ticks(TileEntityConfigurableMachine machine) {
        return machine instanceof TileEntityItemStackToItemStackFactory factory
                ? factory.getTicksRequired() : ((TileEntityElectricMachine) machine).getTicksRequired();
    }
    public static int lanes(TileEntityConfigurableMachine machine) {
        return machine instanceof TileEntityItemStackToItemStackFactory factory ? factory.progress.length : 1;
    }
    public static MachineEnergyContainer<?> energy(TileEntityConfigurableMachine machine) {
        return machine instanceof TileEntityItemStackToItemStackFactory factory
                ? factory.getEnergyContainer() : ((TileEntityElectricMachine) machine).getEnergyContainer();
    }
    public static ItemStackToItemStackRecipe find(TileEntityConfigurableMachine machine, ItemStack input) {
        return machine instanceof TileEntityItemStackToItemStackFactory factory
                ? factory.findFirstRecipe(input) : ((TileEntityElectricMachine) machine).findFirstRecipe(input);
    }
    public static CachedRecipe<ItemStackToItemStackRecipe> cached(TileEntityConfigurableMachine machine,
                                                                 ItemStackToItemStackRecipe recipe) {
        return machine instanceof TileEntityItemStackToItemStackFactory factory
                ? factory.createNewCachedRecipe(recipe, 0) : ((TileEntityElectricMachine) machine).createNewCachedRecipe(recipe, 0);
    }
    public static Object handler(TileEntityConfigurableMachine machine, boolean input) {
        return handler(machine,input,0);
    }
    public static Object handler(TileEntityConfigurableMachine machine, boolean input,int process) {
        try {
            boolean factory = machine instanceof TileEntityItemStackToItemStackFactory;
            Object value = (factory ? input ? FACTORY_INPUT : FACTORY_OUTPUT : input ? SINGLE_INPUT : SINGLE_OUTPUT).get(machine);
            return factory ? ((Object[]) value)[process] : value;
        } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
    }
    public static void handler(TileEntityConfigurableMachine machine, boolean input, Object value) {
        handler(machine,input,0,value);
    }
    public static void handler(TileEntityConfigurableMachine machine, boolean input,int process,Object value) {
        try {
            if (machine instanceof TileEntityItemStackToItemStackFactory) {
                ((Object[]) (input ? FACTORY_INPUT : FACTORY_OUTPUT).get(machine))[process] = value;
            } else (input ? SINGLE_INPUT : SINGLE_OUTPUT).set(machine, value);
        } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
    }
    private static Field field(Class<?> type, String name) {
        try { var field = type.getDeclaredField(name); field.setAccessible(true); return field; }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("Mekanism handler contract changed: " + name, e); }
    }
}
