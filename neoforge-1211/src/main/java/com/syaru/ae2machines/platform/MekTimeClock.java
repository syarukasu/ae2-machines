package com.syaru.ae2machines.platform;
import com.syaru.ae2machines.mixin.*;
import com.syaru.ae2machines.world.NativeQuietStep;
import mekanism.common.tile.base.TileEntityMekanism;
public final class MekTimeClock {
    private MekTimeClock(){}
    public static boolean transmitterQuiet(mekanism.common.tile.transmitter.TileEntityTransmitter m){var a=(MekThermalTransportAccess)m;return !a.machines$thermalForceUpdate() && !a.machines$thermalJoining();}
    public static boolean glassStable(mekanism.common.tile.prefab.TileEntityStructuralMultiblock tile,String expected){return ((MekThermalGlassAccess)tile).machines$thermalFormed();}
    public static boolean worldQuiet(net.minecraft.server.level.ServerLevel level){
        if(mekanism.common.CommonWorldTickHandler.flushTagAndRecipeCaches)return false;
        var regen=((MekTimeWorldAccess)mekanism.common.Mekanism.worldTickHandler).machines$timeRegeneration();
        if(regen!=null && regen.containsKey(level.dimension().location()) && !regen.get(level.dimension().location()).isEmpty())return false;
        var data=level.getExistingDataOrNull(mekanism.common.registries.MekanismAttachmentTypes.MELTDOWN_DATA);
        return data==null || ((MekTimeRadiationAccess)data).machines$timeMeltdowns().isEmpty();
    }
    public static boolean fixedInput(Object input){return input instanceof mekanism.api.recipes.ingredients.ItemStackIngredient item && NativeIngredients.fixedItem(item.ingredient().ingredient());}
    public static NativeQuietStep plan(TileEntityMekanism m,int offered){
        var access=(MekTimeBaseAccess)m;int delay=access.machines$timeDelay(),ticker=m.ticker;
        var tracker=access.machines$timeEnergyTracker();var a=(MekTimeTrackerAccess)tracker;
        long time=a.machines$time(),now=m.getLevel().getGameTime(),current=a.machines$current(),last=a.machines$last();
        return new NativeQuietStep(delay>0?Math.min(offered,delay-1):offered,n->{
            m.ticker=ticker+n;access.machines$timeDelay(Math.max(0,delay-n));
            if(n>1)tracker.received(now+n-1,0);tracker.received(now+n,0);
        },()->{m.ticker=ticker;access.machines$timeDelay(delay);a.machines$time(time);a.machines$current(current);a.machines$last(last);});
    }
}
