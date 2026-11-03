package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.platform.MekPassiveChemical;
import mekanism.api.recipes.cache.CachedRecipe;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets={"mekanism.common.tile.machine.TileEntityChemicalOxidizer","mekanism.common.tile.machine.TileEntityChemicalInfuser","mekanism.common.tile.machine.TileEntityRotaryCondensentrator",
        "mekanism.common.tile.machine.TileEntityElectrolyticSeparator","mekanism.common.tile.machine.TileEntityChemicalWasher",
        "mekanism.common.tile.machine.TileEntityChemicalCrystallizer","mekanism.common.tile.machine.TileEntityIsotopicCentrifuge",
        "mekanism.common.tile.machine.TileEntityPressurizedReactionChamber"},remap=false)
public abstract class MekChemicalParallelMixin {
    @Inject(method={"createNewCachedRecipe(Lmekanism/api/recipes/ItemStackToGasRecipe;I)Lmekanism/api/recipes/cache/CachedRecipe;",
            "createNewCachedRecipe(Lmekanism/api/recipes/ItemStackToChemicalRecipe;I)Lmekanism/api/recipes/cache/CachedRecipe;",
            "createNewCachedRecipe(Lmekanism/api/recipes/ChemicalInfuserRecipe;I)Lmekanism/api/recipes/cache/CachedRecipe;",
            "createNewCachedRecipe(Lmekanism/api/recipes/ChemicalChemicalToChemicalRecipe;I)Lmekanism/api/recipes/cache/CachedRecipe;",
            "createNewCachedRecipe(Lmekanism/api/recipes/RotaryRecipe;I)Lmekanism/api/recipes/cache/CachedRecipe;",
            "createNewCachedRecipe(Lmekanism/api/recipes/ElectrolysisRecipe;I)Lmekanism/api/recipes/cache/CachedRecipe;",
            "createNewCachedRecipe(Lmekanism/api/recipes/FluidSlurryToSlurryRecipe;I)Lmekanism/api/recipes/cache/CachedRecipe;",
            "createNewCachedRecipe(Lmekanism/api/recipes/FluidChemicalToChemicalRecipe;I)Lmekanism/api/recipes/cache/CachedRecipe;",
            "createNewCachedRecipe(Lmekanism/api/recipes/ChemicalCrystallizerRecipe;I)Lmekanism/api/recipes/cache/CachedRecipe;",
            "createNewCachedRecipe(Lmekanism/api/recipes/GasToGasRecipe;I)Lmekanism/api/recipes/cache/CachedRecipe;",
            "createNewCachedRecipe(Lmekanism/api/recipes/ChemicalToChemicalRecipe;I)Lmekanism/api/recipes/cache/CachedRecipe;",
            "createNewCachedRecipe(Lmekanism/api/recipes/PressurizedReactionRecipe;I)Lmekanism/api/recipes/cache/CachedRecipe;"},at=@At("RETURN"))
    private void machines$parallel(CallbackInfoReturnable<CachedRecipe<?>> cir) {
        var cached=cir.getReturnValue();
        MekPassiveChemical.bind((BlockEntity)(Object)this,cached);
    }
}
