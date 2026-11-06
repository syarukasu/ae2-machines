package com.syaru.ae2machines.compat.mek;

import com.syaru.ae2machines.mixin.*;
import com.syaru.ae2machines.platform.*;
import com.syaru.ae2machines.world.NativeQuietStep;
import java.math.BigInteger;
import java.util.Set;
import mekanism.common.inventory.slot.*;
import mekanism.common.tile.machine.*;
import mekanism.common.tile.prefab.TileEntityProgressMachine;
import net.minecraft.world.level.block.entity.BlockEntity;

/** The tile clock contract and the native recipe transition are checked separately. */
public final class MekQuietTime {
    // These owners only add empty auxiliary-slot updates to the common progress-machine tick.
    // A recipe contract alone must not skip an unknown owner's heat, radiation or world work.
    private static final Set<Class<?>> CLOCKS=Set.of(TileEntityCrusher.class,TileEntityEnrichmentChamber.class,
            TileEntityEnergizedSmelter.class,TileEntityChemicalOxidizer.class,TileEntityChemicalCrystallizer.class);
    private MekQuietTime(){}
    public static boolean supports(BlockEntity be){return be!=null && CLOCKS.contains(be.getClass());}
    public static NativeQuietStep plan(BlockEntity be,int offered){
        if(offered<1 || !supports(be) || !MekThermalHooks.processingSafe(be.getClass()))return null;
        var m=(TileEntityProgressMachine<?>)be;
        if(m.getControlType()!=mekanism.common.tile.interfaces.IRedstoneControl.RedstoneControl.DISABLED
                || m.getSecurity().getMode()!=mekanism.api.security.SecurityMode.PUBLIC
                || !m.getComponent().getUpgradeSlot().isEmpty() || !m.getComponent().getUpgradeOutputSlot().isEmpty())return null;
        for(var transmission:m.getConfig().getTransmissions())if(m.getConfig().getConfig(transmission).isEjecting())return null;
        for(var slot:m.getInventorySlots(null))if(!(slot instanceof InputInventorySlot)
                && !(slot instanceof OutputInventorySlot) && !slot.isEmpty())return null;
        var clock=MekTimeClock.plan(m,offered);
        if(m instanceof mekanism.common.tile.prefab.TileEntityElectricMachine
                && !m.getActive() && m.getOperatingTicks()==0
                && m.getInventorySlots(null).stream().filter(s->s instanceof InputInventorySlot).allMatch(s->s.isEmpty()))return clock;
        var state=MekRecipeState.capture(m,clock.limit());
        if(state==null || !MekRecipeTime.quietChemistry(m,state.cached()))return null;
        var subtick=MekSubtickTime.plan(m,state,clock);
        if(subtick!=null)return subtick;
        int limit=Math.min(clock.limit(),state.duration()-state.progress()-1);
        if(state.cost().signum()>0)limit=state.energy().divide(state.cost()).min(BigInteger.valueOf(Math.max(0,limit))).intValueExact();
        int horizon=Math.max(0,limit);var applied=new boolean[1];
        return new NativeQuietStep(horizon,n->{
            if(n<0 || n>horizon)throw new IllegalArgumentException("NATIVE_RECIPE_INTERVAL_EXCEEDS_OFFER");
            if(n==0)return;
            if(!state.current(m))throw new NativeQuietStep.StateChanged("NATIVE_RECIPE_STATE_CHANGED");
            applied[0]=true;
            MekanismAdapter.setStoredEnergy(m,state.energy().subtract(state.cost().multiply(BigInteger.valueOf(n))));
            ((MekNativeStep)state.cached()).machines$progress(state.progress()+n);
            ((MekTimeCacheAccess)state.cached()).machines$timeProgressChanged().accept(state.progress()+n);
            clock.advance().accept(n);m.getLevel().getChunkAt(m.getBlockPos()).setUnsaved(true);
        },()->{
            if(!applied[0])return;
            MekanismAdapter.setStoredEnergy(m,state.energy());((MekNativeStep)state.cached()).machines$progress(state.progress());
            ((MekTimeCacheAccess)state.cached()).machines$timeProgressChanged().accept(state.progress());
            clock.rollback().run();m.getLevel().getChunkAt(m.getBlockPos()).setUnsaved(true);
        });
    }
}
