package com.syaru.ae2machines.platform;

import com.syaru.ae2machines.mixin.FurnaceClockAccess;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

public final class FurnaceRecipeAccess {
    private FurnaceRecipeAccess(){}
    public static boolean parallelSafe(AbstractFurnaceBlockEntity furnace) {
        if(!constant(furnace))return false;
        var fuel=furnace.getItem(1);
        if(!fuel.isEmpty() && !fuel.getItem().getClass().getName().startsWith("net.minecraft."))return false;
        var handlers=new boolean[1];
        // Inspect dispatch without running any listener or changing its fuel result.
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                new net.minecraftforge.event.furnace.FurnaceFuelBurnTimeEvent(net.minecraft.world.item.ItemStack.EMPTY,0,null),
                (listener,event)->{if(!(listener instanceof net.minecraftforge.eventbus.api.EventPriority))handlers[0]=true;});
        return !handlers[0];
    }
    @SuppressWarnings("unchecked")
    public static boolean constant(AbstractFurnaceBlockEntity furnace) {
        var recipe=((FurnaceClockAccess)furnace).machines$recipeCheck().getRecipeFor(furnace,furnace.getLevel()).orElse(null);
        return recipe==null || recipe.getClass()==SmeltingRecipe.class || recipe.getClass()==BlastingRecipe.class || recipe.getClass()==SmokingRecipe.class;
    }
}
