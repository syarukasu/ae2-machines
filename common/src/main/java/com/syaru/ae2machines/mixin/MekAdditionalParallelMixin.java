package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.compat.mek.*;
import com.syaru.ae2machines.runtime.NativeWorldBatches;
import mekanism.api.recipes.cache.CachedRecipe;
import mekanism.common.tile.prefab.TileEntityConfigurableMachine;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets={"mekanism.common.tile.prefab.TileEntityAdvancedElectricMachine",
        "mekanism.common.tile.machine.TileEntityCombiner","mekanism.common.tile.machine.TileEntityPrecisionSawmill",
        "mekanism.common.tile.machine.TileEntityMetallurgicInfuser","mekanism.common.tile.machine.TileEntityChemicalDissolutionChamber",
        "mekanism.common.tile.machine.TileEntityAntiprotonicNucleosynthesizer","mekanism.common.tile.machine.TileEntityPigmentExtractor",
        "mekanism.common.tile.machine.TileEntityPigmentMixer","mekanism.common.tile.machine.TileEntityPaintingMachine",
        "mekanism.common.tile.machine.TileEntityNutritionalLiquifier","mekanism.common.tile.machine.TileEntitySolarNeutronActivator",
        "mekanism.common.tile.factory.TileEntityCombiningFactory","mekanism.common.tile.factory.TileEntitySawingFactory",
        "mekanism.common.tile.factory.TileEntityMetallurgicInfuserFactory","mekanism.common.tile.factory.TileEntityItemStackGasToItemStackFactory",
        "mekanism.common.tile.factory.TileEntityItemStackChemicalToItemStackFactory"},remap=false)
public abstract class MekAdditionalParallelMixin {
    @Inject(method={
            "createNewCachedRecipe(Lmekanism/api/recipes/CombinerRecipe;I)Lmekanism/api/recipes/cache/CachedRecipe;",
            "createNewCachedRecipe(Lmekanism/api/recipes/SawmillRecipe;I)Lmekanism/api/recipes/cache/CachedRecipe;",
            "createNewCachedRecipe(Lmekanism/api/recipes/MetallurgicInfuserRecipe;I)Lmekanism/api/recipes/cache/CachedRecipe;",
            "createNewCachedRecipe(Lmekanism/api/recipes/ItemStackGasToItemStackRecipe;I)Lmekanism/api/recipes/cache/CachedRecipe;",
            "createNewCachedRecipe(Lmekanism/api/recipes/ItemStackChemicalToItemStackRecipe;I)Lmekanism/api/recipes/cache/CachedRecipe;",
            "createNewCachedRecipe(Lmekanism/api/recipes/ChemicalDissolutionRecipe;I)Lmekanism/api/recipes/cache/CachedRecipe;",
            "createNewCachedRecipe(Lmekanism/api/recipes/NucleosynthesizingRecipe;I)Lmekanism/api/recipes/cache/CachedRecipe;",
            "createNewCachedRecipe(Lmekanism/api/recipes/ItemStackToPigmentRecipe;I)Lmekanism/api/recipes/cache/CachedRecipe;",
            "createNewCachedRecipe(Lmekanism/api/recipes/ItemStackToChemicalRecipe;I)Lmekanism/api/recipes/cache/CachedRecipe;",
            "createNewCachedRecipe(Lmekanism/api/recipes/PigmentMixingRecipe;I)Lmekanism/api/recipes/cache/CachedRecipe;",
            "createNewCachedRecipe(Lmekanism/api/recipes/ChemicalChemicalToChemicalRecipe;I)Lmekanism/api/recipes/cache/CachedRecipe;",
            "createNewCachedRecipe(Lmekanism/api/recipes/PaintingRecipe;I)Lmekanism/api/recipes/cache/CachedRecipe;",
            "createNewCachedRecipe(Lmekanism/api/recipes/ItemStackToFluidRecipe;I)Lmekanism/api/recipes/cache/CachedRecipe;",
            "createNewCachedRecipe(Lmekanism/api/recipes/basic/BasicItemStackToFluidOptionalItemRecipe;I)Lmekanism/api/recipes/cache/CachedRecipe;",
            "createNewCachedRecipe(Lmekanism/api/recipes/GasToGasRecipe;I)Lmekanism/api/recipes/cache/CachedRecipe;",
            "createNewCachedRecipe(Lmekanism/api/recipes/ChemicalToChemicalRecipe;I)Lmekanism/api/recipes/cache/CachedRecipe;"},at=@At("RETURN"))
    private void machines$parallel(@Coerce Object recipe,int index,CallbackInfoReturnable<CachedRecipe<?>> cir) {
        var machine=(TileEntityConfigurableMachine)(Object)this;
        if(NativeWorldBatches.executing(machine) || !MekRecipeMachines.supports(machine))return;
        String type=recipe.getClass().getName();
        if(!type.startsWith("mekanism.common.recipe.impl.") && !type.startsWith("mekanism.api.recipes.basic.Basic"))return;
        MekWorldParallel.prepare(machine);MekWorldParallel.bindGroup(machine,cir.getReturnValue(),index);
    }
}
