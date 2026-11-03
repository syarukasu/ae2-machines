package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.compat.mek.MekRecipeMachines;
import mekanism.common.tile.prefab.TileEntityConfigurableMachine;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

/** The native usage scheduler expects one lane's history; its saved counter retains the real total. */
@Pseudo
@Mixin(targets={"mekanism.api.recipes.cache.ItemStackConstantChemicalToItemStackCachedRecipe",
        "mekanism.api.recipes.cache.ItemStackConstantChemicalToObjectCachedRecipe"},remap=false)
public abstract class MekGroupedUsageMixin implements MekRecipeMachines.Usage {
    @Unique private TileEntityConfigurableMachine machines$owner;
    @Unique private int machines$process;
    @Override public void machines$usageOwner(TileEntityConfigurableMachine owner,int process){machines$owner=owner;machines$process=process;}
    @Group(name="nativeUsage",min=1,max=1)
    @ModifyArg(method="setupVariableValues",at=@At(value="INVOKE",target="Lmekanism/api/recipes/cache/ItemStackConstantChemicalToItemStackCachedRecipe$ChemicalUsageMultiplier;getToUse(JI)J"),index=0,require=0)
    private long machines$forge(long used){return MekRecipeMachines.perLaneUsage(machines$owner,machines$process,used);}
    @Group(name="nativeUsage",min=1,max=1)
    @ModifyArg(method="setupVariableValues",at=@At(value="INVOKE",target="Lmekanism/api/recipes/cache/ItemStackConstantChemicalToObjectCachedRecipe$ChemicalUsageMultiplier;getToUse(JI)J"),index=0,require=0)
    private long machines$neo(long used){return MekRecipeMachines.perLaneUsage(machines$owner,machines$process,used);}
}
