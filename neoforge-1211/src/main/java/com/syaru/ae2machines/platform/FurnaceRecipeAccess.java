package com.syaru.ae2machines.platform;

import com.syaru.ae2machines.mixin.FurnaceClockAccess;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

public final class FurnaceRecipeAccess {
    private FurnaceRecipeAccess(){}
    private static final java.lang.reflect.Method LISTENERS=listeners();
    private static java.lang.reflect.Method listeners() {
        try {var method=net.neoforged.bus.EventBus.class.getDeclaredMethod("getListenerList",Class.class);method.setAccessible(true);return method;}
        catch(ReflectiveOperationException|RuntimeException unavailable){return null;}
    }
    public static boolean parallelSafe(AbstractFurnaceBlockEntity furnace) {
        if(!constant(furnace) || LISTENERS==null)return false;
        var fuel=furnace.getItem(1);
        if(!fuel.isEmpty() && !fuel.getItem().getClass().getName().startsWith("net.minecraft."))return false;
        try {
            var list=(net.neoforged.bus.ListenerList)LISTENERS.invoke(net.neoforged.neoforge.common.NeoForge.EVENT_BUS,net.neoforged.neoforge.event.furnace.FurnaceFuelBurnTimeEvent.class);
            return list.getListeners().length==0;
        } catch(ReflectiveOperationException|RuntimeException unavailable){return false;}
    }
    @SuppressWarnings("unchecked")
    public static boolean constant(AbstractFurnaceBlockEntity furnace) {
        var holder=(RecipeHolder<?>)((FurnaceClockAccess)furnace).machines$recipeCheck()
                .getRecipeFor(new SingleRecipeInput(furnace.getItem(0)),furnace.getLevel()).orElse(null);
        if(holder==null)return true;
        var recipe=holder.value();
        return recipe.getClass()==SmeltingRecipe.class || recipe.getClass()==BlastingRecipe.class || recipe.getClass()==SmokingRecipe.class;
    }
}
