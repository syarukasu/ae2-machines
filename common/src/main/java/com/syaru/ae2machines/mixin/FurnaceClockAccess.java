package com.syaru.ae2machines.mixin;

import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractFurnaceBlockEntity.class)
public interface FurnaceClockAccess {
    @Accessor("litTime") int machines$lit();
    @Accessor("litTime") void machines$lit(int value);
    @Accessor("cookingProgress") int machines$progress();
    @Accessor("cookingProgress") void machines$progress(int value);
    @Accessor("cookingTotalTime") int machines$duration();
    @Accessor("quickCheck") RecipeManager.CachedCheck machines$recipeCheck();
}
