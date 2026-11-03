package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.compat.mek.MekRecipeMachines;
import com.syaru.ae2machines.compat.mek.MekWorldParallel;
import com.syaru.ae2machines.runtime.NativeWorldBatches;
import mekanism.api.recipes.cache.CachedRecipe;
import mekanism.common.tile.prefab.TileEntityConfigurableMachine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep Evolved's matching, conditions, material handlers and per-operation energy debit. */
@Pseudo
@Mixin(targets={
        "fr.iglee42.evolvedmekanism.tiles.machine.TileEntityAlloyer",
        "fr.iglee42.evolvedmekanism.tiles.machine.TileEntityChemixer",
        "fr.iglee42.evolvedmekanism.tiles.machine.TileEntityMelter",
        "fr.iglee42.evolvedmekanism.tiles.machine.TileEntitySolidifier",
        "fr.iglee42.evolvedmekanism.tiles.machine.TileEntityFluidAlloyer",
        "fr.iglee42.evolvedmekanism.tiles.machine.TileEntityFluidCombiner",
        "fr.iglee42.evolvedmekanism.tiles.factory.TileEntityAlloyingFactory",
        "fr.iglee42.evolvedmekanism.tiles.TileEntityLunarNeutronActivator"},remap=false)
public abstract class EvolvedMekParallelMixin {
    @Inject(method={
            "createNewCachedRecipe(Lfr/iglee42/evolvedmekanism/recipes/AlloyerRecipe;I)Lmekanism/api/recipes/cache/CachedRecipe;",
            "createNewCachedRecipe(Lfr/iglee42/evolvedmekanism/recipes/ChemixerRecipe;I)Lmekanism/api/recipes/cache/CachedRecipe;",
            "createNewCachedRecipe(Lfr/iglee42/evolvedmekanism/recipes/MeltingRecipe;I)Lmekanism/api/recipes/cache/CachedRecipe;",
            "createNewCachedRecipe(Lfr/iglee42/evolvedmekanism/recipes/SolidificationRecipe;I)Lmekanism/api/recipes/cache/CachedRecipe;",
            "createNewCachedRecipe(Lfr/iglee42/evolvedmekanism/recipes/FluidAlloyingRecipe;I)Lmekanism/api/recipes/cache/CachedRecipe;",
            "createNewCachedRecipe(Lfr/iglee42/evolvedmekanism/recipes/FluidCombiningRecipe;I)Lmekanism/api/recipes/cache/CachedRecipe;",
            "createNewCachedRecipe(Lmekanism/api/recipes/ItemStackToFluidRecipe;I)Lmekanism/api/recipes/cache/CachedRecipe;",
            "createNewCachedRecipe(Lmekanism/api/recipes/GasToGasRecipe;I)Lmekanism/api/recipes/cache/CachedRecipe;",
            "createNewCachedRecipe(Lmekanism/api/recipes/ChemicalToChemicalRecipe;I)Lmekanism/api/recipes/cache/CachedRecipe;"},at=@At("RETURN"))
    private void machines$parallel(@Coerce Object recipe,int process,CallbackInfoReturnable<CachedRecipe<?>> ci) {
        var machine=(TileEntityConfigurableMachine)(Object)this;
        if(NativeWorldBatches.executing(machine) || !MekRecipeMachines.evolved(machine))return;
        MekWorldParallel.prepare(machine);
        MekWorldParallel.bindGroup(machine,ci.getReturnValue(),process);
    }
}
