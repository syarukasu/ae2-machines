package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.compat.mek.MekEvaporationParallel;
import mekanism.api.recipes.FluidToFluidRecipe;
import mekanism.api.recipes.cache.CachedRecipe;
import mekanism.common.content.evaporation.EvaporationMultiblockData;
import mekanism.common.recipe.lookup.monitor.RecipeCacheLookupMonitor;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="mekanism.common.content.evaporation.EvaporationMultiblockData",remap=false)
public abstract class MekEvaporationParallelMixin implements MekEvaporationParallel.Cache {
    @Shadow @Final private RecipeCacheLookupMonitor<FluidToFluidRecipe> recipeCacheLookupMonitor;
    @Unique private CachedRecipe<?> machines$bound;
    @Override public CachedRecipe<?> machines$recipe(){return recipeCacheLookupMonitor.getCachedRecipe(0);}
    @Override public CachedRecipe<?> machines$bound(){return machines$bound;}
    @Override public void machines$bound(CachedRecipe<?> value){machines$bound=value;}
    @Inject(method="tick",at=@At("HEAD"))
    private void machines$prepare(Level world,CallbackInfoReturnable<Boolean> cir) {
        var self=(EvaporationMultiblockData)(Object)this;
        var owner=world.getBlockEntity(self.getMinPos());
        if(owner!=null)MekEvaporationParallel.prepare(self,owner,machines$recipe());
    }
    @Inject(method="createNewCachedRecipe(Lmekanism/api/recipes/FluidToFluidRecipe;I)Lmekanism/api/recipes/cache/CachedRecipe;",at=@At("RETURN"))
    private void machines$recipe(FluidToFluidRecipe recipe,int index,CallbackInfoReturnable<CachedRecipe<FluidToFluidRecipe>> cir) {
        var self=(EvaporationMultiblockData)(Object)this;var owner=com.syaru.ae2machines.platform.MekSpatialShape.level(self).getBlockEntity(self.getMinPos());
        if(owner!=null)MekEvaporationParallel.prepare(self,owner,cir.getReturnValue());
    }
}
