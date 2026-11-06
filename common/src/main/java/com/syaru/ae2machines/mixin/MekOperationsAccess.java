package com.syaru.ae2machines.mixin;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import mekanism.api.recipes.cache.CachedRecipe.OperationTracker;

@Pseudo
@Mixin(value=OperationTracker.class,remap=false)
public interface MekOperationsAccess {
    @Accessor("currentMax") int machines$currentMax();
    @Accessor("maxForEnergy") int machines$maxForEnergy();
    @Invoker("<init>")
    static OperationTracker machines$quoteTracker(java.util.Set<OperationTracker.RecipeError> errors,boolean all,int maximum) {
        throw new AssertionError("Mixin constructor invoker");
    }
}
