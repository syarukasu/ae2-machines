package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.compat.mek.MekWorldParallel;
import mekanism.api.recipes.ItemStackToItemStackRecipe;
import mekanism.api.recipes.cache.CachedRecipe;
import mekanism.common.tile.factory.TileEntityItemStackToItemStackFactory;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="mekanism.common.tile.factory.TileEntityItemStackToItemStackFactory",remap=false)
public abstract class MekFactoryParallelMixin {
    @Inject(method="createNewCachedRecipe",at=@At("HEAD"))
    private void machines$slots(ItemStackToItemStackRecipe recipe,int index,CallbackInfoReturnable<CachedRecipe<?>> cir) {
        MekWorldParallel.prepare((TileEntityItemStackToItemStackFactory)(Object)this);
    }
    @Inject(method="createNewCachedRecipe",at=@At("RETURN"))
    private void machines$parallel(ItemStackToItemStackRecipe recipe,int index,CallbackInfoReturnable<CachedRecipe<?>> cir) {
        MekWorldParallel.bind((TileEntityItemStackToItemStackFactory)(Object)this,recipe,cir.getReturnValue(),index);
    }
}
