package com.syaru.ae2machines.platform;
import net.minecraft.world.item.crafting.Ingredient;
public final class NativeIngredients {
    private NativeIngredients(){}
    public static boolean fixedItem(Ingredient ingredient){return ingredient.getClass()==Ingredient.class;}
}
