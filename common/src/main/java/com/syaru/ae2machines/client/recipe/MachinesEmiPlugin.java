package com.syaru.ae2machines.client.recipe;

import com.lowdragmc.lowdraglib.emi.ModularEmiRecipe;
import com.syaru.ae2machines.platform.*;
import dev.emi.emi.api.*;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.widget.WidgetHolder;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

@EmiEntrypoint
public final class MachinesEmiPlugin implements EmiPlugin {
    @Override public void register(EmiRegistry registry){
        if(!PlatformData.ldlibPresent())return;
        Registration.register(registry);
    }
    private static final class Registration {
        static final EmiRecipeCategory CATEGORY=new EmiRecipeCategory(PlatformData.id("ae2machines:computation_structure"),EmiStack.of(MachinesMod.COMPUTATION_CONTROLLER.get()));
        static void register(EmiRegistry registry){
            registry.addCategory(CATEGORY);
            registry.addWorkstation(CATEGORY,EmiStack.of(MachinesMod.COMPUTATION_CONTROLLER.get()));
            for(var example:ComputationExample.all())registry.addRecipe(new StructureRecipe(example));
        }
    }
    private static final class StructureRecipe extends ModularEmiRecipe<ComputationPreviewWidget> {
        private final ComputationExample example;
        StructureRecipe(ComputationExample example){super(()->new ComputationPreviewWidget(example));this.example=example;}
        // Structure controls change amounts. Do not freeze these into EMI's recipe slot copies.
        @Override public List<Widget> getFlatWidgetCollection(ComputationPreviewWidget widget){return super.getFlatWidgetCollection(widget).stream().filter(w->!(w instanceof SlotWidget)).toList();}
        @Override public List<EmiIngredient> getInputs(){return example.materials(1).stream().<EmiIngredient>map(EmiStack::of).toList();}
        @Override public void addWidgets(WidgetHolder widgets){
            super.addWidgets(widgets);
            widgets.addSlot(EmiStack.of(MachinesMod.COMPUTATION_CONTROLLER.get()),154,2).recipeContext(this).drawBack(false);
        }
        @Override public EmiRecipeCategory getCategory(){return Registration.CATEGORY;}
        @Override public ResourceLocation getId(){return PlatformData.id("ae2machines:/computation_structure/t"+example.tier());}
        @Override public List<EmiStack> getOutputs(){return List.of(EmiStack.of(MachinesMod.COMPUTATION_CONTROLLER.get()));}
        @Override public boolean supportsRecipeTree(){return false;}
    }
}
