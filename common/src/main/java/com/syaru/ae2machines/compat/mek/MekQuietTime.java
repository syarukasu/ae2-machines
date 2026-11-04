package com.syaru.ae2machines.compat.mek;

import com.syaru.ae2machines.mixin.*;
import com.syaru.ae2machines.platform.*;
import com.syaru.ae2machines.world.NativeQuietStep;
import java.math.BigInteger;
import java.util.Set;
import mekanism.api.recipes.ItemStackToItemStackRecipe;
import mekanism.common.inventory.slot.*;
import mekanism.common.tile.machine.*;
import mekanism.common.tile.prefab.TileEntityElectricMachine;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Only constant-cost, resource-free progress between native completion/IO events is coalesced. */
public final class MekQuietTime {
    private static final Set<Class<?>> TYPES=Set.of(TileEntityCrusher.class,TileEntityEnrichmentChamber.class,TileEntityEnergizedSmelter.class);
    private static final Set<String> RECIPES=Set.of("mekanism.common.recipe.impl.CrushingIRecipe","mekanism.common.recipe.impl.EnrichingIRecipe","mekanism.common.recipe.impl.SmeltingIRecipe",
        "mekanism.api.recipes.basic.BasicCrushingRecipe","mekanism.api.recipes.basic.BasicEnrichingRecipe","mekanism.api.recipes.basic.BasicSmeltingRecipe");
    private MekQuietTime(){}
    public static boolean supports(BlockEntity be){return TYPES.contains(be.getClass());}
    public static NativeQuietStep plan(BlockEntity be,int offered){
        if(!TYPES.contains(be.getClass()))return null;
        var m=(TileEntityElectricMachine)be;
        if(m.getControlType()!=mekanism.common.tile.interfaces.IRedstoneControl.RedstoneControl.DISABLED
                || m.getSecurity().getMode()!=mekanism.api.security.SecurityMode.PUBLIC
                || !m.getComponent().getUpgradeSlot().isEmpty() || !m.getComponent().getUpgradeOutputSlot().isEmpty())return null;
        for(var transmission:m.getConfig().getTransmissions())if(m.getConfig().getConfig(transmission).isEjecting())return null;
        for(var slot:m.getInventorySlots(null))if(!(slot instanceof InputInventorySlot) && !(slot instanceof OutputInventorySlot) && !slot.isEmpty())return null;
        var clock=MekTimeClock.plan(m,offered);
        int progress=m.getOperatingTicks();
        var input=m.getInventorySlots(null).stream().filter(s->s instanceof InputInventorySlot).findFirst().orElseThrow();
        var energy=MekanismAdapter.energy(m);
        var rate=new java.math.BigDecimal(String.valueOf(m.getEnergyContainer().getEnergyPerTick())).movePointRight(4).toBigIntegerExact();
        // No material can arrive, and no battery can charge it, before the scheduler's IO horizon.
        if(!m.getActive() && progress==0 && input.isEmpty())return clock;
        var cached=((MekTimeRecipeAccess)m).machines$timeMonitor().getCachedRecipe(0);
        if(progress<=0 || !m.getActive() || cached==null || ((MekNativeStep)cached).machines$progress()!=progress
                || !(cached.getRecipe() instanceof ItemStackToItemStackRecipe recipe) || !RECIPES.contains(recipe.getClass().getName()) || !MekanismAdapter.fixedOutput(recipe)
                || !MekTimeClock.fixedInput(recipe.getInput()))return null;
        int operations=MekWorldParallel.operations(m,0);
        if(operations<=0)return null;
        var matched=recipe.getInput().getMatchingInstance(input.getStack());
        var output=m.getInventorySlots(null).stream().filter(s->s instanceof OutputInventorySlot).findFirst().orElseThrow();
        var product=recipe.getOutput(matched);
        if(matched.isEmpty() || product.isEmpty() || (long)matched.getCount()*operations>input.getCount()
                || !output.isEmpty() && !java.util.Objects.equals(appeng.api.stacks.AEItemKey.of(output.getStack()),appeng.api.stacks.AEItemKey.of(product))
                || (long)output.getCount()+(long)product.getCount()*operations>output.getLimit(product))return null;
        var cost=rate.multiply(BigInteger.valueOf(operations));
        int limit=Math.min(clock.limit(),m.getTicksRequired()-progress-1);
        if(cost.signum()>0)limit=energy.divide(cost).min(BigInteger.valueOf(Math.max(0,limit))).intValueExact();
        int horizon=Math.max(0,limit);
        return new NativeQuietStep(horizon,n->{
            MekanismAdapter.setStoredEnergy(m,energy.subtract(cost.multiply(BigInteger.valueOf(n))));
            ((MekNativeStep)cached).machines$progress(progress+n);
            ((MekTimeCacheAccess)cached).machines$timeProgressChanged().accept(progress+n);
            clock.advance().accept(n);m.getLevel().getChunkAt(m.getBlockPos()).setUnsaved(true);
        },()->{
            MekanismAdapter.setStoredEnergy(m,energy);((MekNativeStep)cached).machines$progress(progress);
            ((MekTimeCacheAccess)cached).machines$timeProgressChanged().accept(progress);clock.rollback().run();m.getLevel().getChunkAt(m.getBlockPos()).setUnsaved(true);
        });
    }
}
