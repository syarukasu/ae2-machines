package com.syaru.ae2machines.platform;
import com.syaru.ae2machines.mixin.*;
import com.syaru.ae2machines.world.NativeQuietStep;
import mekanism.api.math.FloatingLong;
import mekanism.common.tile.base.TileEntityMekanism;
public final class MekTimeClock {
    private MekTimeClock(){}
    public static boolean transmitterQuiet(mekanism.common.tile.transmitter.TileEntityTransmitter m){return !((MekThermalTransportAccess)m).machines$thermalForceUpdate();}
    public static boolean glassStable(mekanism.common.tile.prefab.TileEntityStructuralMultiblock tile,String expected){return java.util.Objects.equals(expected,((MekThermalGlassAccess)tile).machines$thermalActive());}
    public static boolean worldQuiet(net.minecraft.server.level.ServerLevel level){
        if(mekanism.common.CommonWorldTickHandler.flushTagAndRecipeCaches)return false;
        var regen=((MekTimeWorldAccess)mekanism.common.Mekanism.worldTickHandler).machines$timeRegeneration();
        if(regen!=null && regen.containsKey(level.dimension().location()) && !regen.get(level.dimension().location()).isEmpty())return false;
        var meltdowns=((MekTimeRadiationAccess)mekanism.common.lib.radiation.RadiationManager.get()).machines$timeMeltdowns().get(level.dimension().location());
        return meltdowns==null || meltdowns.isEmpty();
    }
    public static boolean fixedInput(Object input){
        if(input instanceof mekanism.common.recipe.ingredient.IMultiIngredient<?,?> multi)return multi.getIngredients().stream().allMatch(MekTimeClock::fixedInput);
        return input instanceof mekanism.common.recipe.ingredient.creator.ItemStackIngredientCreator.SingleItemStackIngredient single && NativeIngredients.fixedItem(single.getInputRaw());
    }
    public static NativeQuietStep plan(TileEntityMekanism m,int offered){
        var access=(MekTimeBaseAccess)m;int delay=access.machines$timeDelay(),ticker=m.ticker;
        var tracker=access.machines$timeEnergyTracker();var a=(MekTimeTrackerAccess)tracker;
        long time=a.machines$time(),now=m.getLevel().getGameTime();var current=a.machines$current().copy();var last=a.machines$last().copy();
        return new NativeQuietStep(delay>0?Math.min(offered,delay-1):offered,n->{
            m.ticker=ticker+n;access.machines$timeDelay(Math.max(0,delay-n));
            if(n>1)tracker.received(now+n-1,FloatingLong.ZERO);tracker.received(now+n,FloatingLong.ZERO);
        },()->{m.ticker=ticker;access.machines$timeDelay(delay);a.machines$time(time);a.machines$current(current);a.machines$last(last);});
    }
}
