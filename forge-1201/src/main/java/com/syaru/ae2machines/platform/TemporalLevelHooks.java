package com.syaru.ae2machines.platform;
import com.syaru.ae2machines.world.NativeTemporalHooks;
import java.lang.reflect.Field;
public final class TemporalLevelHooks {
    private static final Field BUS_ID=busId();
    private static Object previous;
    private static boolean accepted;
    private TemporalLevelHooks(){}
    private static Field busId(){try{var f=net.minecraftforge.eventbus.EventBus.class.getDeclaredField("busID");return f.trySetAccessible()?f:null;}catch(ReflectiveOperationException|RuntimeException unavailable){return null;}}
    public static boolean safe(){
        if(BUS_ID==null)return false;
        try{
            int id=BUS_ID.getInt(net.minecraftforge.common.MinecraftForge.EVENT_BUS);
            var list=net.minecraftforge.eventbus.api.EventListenerHelper.getListenerList(net.minecraftforge.event.TickEvent.LevelTickEvent.class);
            var listeners=list.getListeners(id);if(previous==listeners)return accepted;
            previous=listeners;accepted=false;
            for(var listener:listeners)if(!NativeTemporalHooks.known(listener) && !SparkLevelObservers.ignoresLevel(listener))return false;
            return accepted=true;
        }catch(ReflectiveOperationException|RuntimeException unavailable){return false;}
    }
    public static String diagnostic(){
        try{return java.util.Arrays.toString(net.minecraftforge.eventbus.api.EventListenerHelper.getListenerList(net.minecraftforge.event.TickEvent.LevelTickEvent.class).getListeners(BUS_ID.getInt(net.minecraftforge.common.MinecraftForge.EVENT_BUS)));}
        catch(Exception unavailable){return "UNKNOWN";}
    }
}
