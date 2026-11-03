package com.syaru.ae2machines.client.recipe;

import com.lowdragmc.lowdraglib.jei.*;
import com.syaru.ae2machines.platform.*;
import mezz.jei.api.*;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

@JeiPlugin
public final class MachinesJeiPlugin implements IModPlugin {
    public static final RecipeType<ComputationExample> TYPE=new RecipeType<>(PlatformData.id("ae2machines:computation_structure"),ComputationExample.class);
    @Override public ResourceLocation getPluginUid(){return PlatformData.id("ae2machines:computation_structure");}
    @Override public void registerCategories(IRecipeCategoryRegistration registration){
        if(enabled())registration.addRecipeCategories(new Category(registration.getJeiHelpers().getGuiHelper()));
    }
    @Override public void registerRecipes(IRecipeRegistration registration){
        if(enabled())registration.addRecipes(TYPE,ComputationExample.all());
    }
    @Override public void registerRecipeCatalysts(IRecipeCatalystRegistration registration){
        if(enabled())registration.addRecipeCatalyst(new ItemStack(MachinesMod.COMPUTATION_CONTROLLER.get()),TYPE);
    }
    private static boolean enabled(){return PlatformData.ldlibPresent() && !PlatformData.emiPresent();}
    private static final class Category extends ModularUIRecipeCategory<ComputationExample> {
        private final IDrawable background,icon;
        Category(mezz.jei.api.helpers.IGuiHelper helper){
            super(example->new ModularWrapper<>(new ComputationPreviewWidget(example)));
            background=helper.createBlankDrawable(ComputationPreviewWidget.WIDTH,ComputationPreviewWidget.HEIGHT);
            icon=helper.createDrawableItemStack(new ItemStack(MachinesMod.COMPUTATION_CONTROLLER.get()));
        }
        @Override public RecipeType<ComputationExample> getRecipeType(){return TYPE;}
        @Override public void setRecipe(mezz.jei.api.gui.builder.IRecipeLayoutBuilder builder,ComputationExample example,mezz.jei.api.recipe.IFocusGroup focuses){
            // Search the components, but let the live LDLib slots render current unit counts.
            builder.addInvisibleIngredients(mezz.jei.api.recipe.RecipeIngredientRole.INPUT).addItemStacks(example.materials(1));
            builder.addSlot(mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT,155,3).addItemStack(new ItemStack(MachinesMod.COMPUTATION_CONTROLLER.get()));
        }
        @Override public Component getTitle(){return Component.translatable("emi.category.ae2machines.computation_structure");}
        @Override public IDrawable getBackground(){return background;}
        @Override public IDrawable getIcon(){return icon;}
    }
}
