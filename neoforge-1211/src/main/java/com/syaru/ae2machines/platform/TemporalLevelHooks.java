package com.syaru.ae2machines.platform;
import com.syaru.ae2machines.world.NativeTemporalHooks;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.*;
public final class TemporalLevelHooks {
    private static final Method LISTENERS=listeners();
    private static final Field OWNERS=owners();
    private static Object previousPre,previousPost;
    private static boolean accepted;
    private TemporalLevelHooks(){}
    private static Method listeners(){try{var m=net.neoforged.bus.EventBus.class.getDeclaredMethod("getListenerList",Class.class);return m.trySetAccessible()?m:null;}catch(ReflectiveOperationException|RuntimeException unavailable){return null;}}
    private static Field owners(){try{var f=net.neoforged.bus.EventBus.class.getDeclaredField("listeners");return f.trySetAccessible()?f:null;}catch(ReflectiveOperationException|RuntimeException unavailable){return null;}}
    @SuppressWarnings("unchecked") private static Map<Object,Object> ownerIndex()throws IllegalAccessException{
        var result=new IdentityHashMap<Object,Object>();
        var registry=(Map<Object,List<net.neoforged.bus.api.EventListener>>)OWNERS.get(net.neoforged.neoforge.common.NeoForge.EVENT_BUS);
        registry.forEach((owner,list)->{for(var listener:list){result.put(listener,owner);if(listener instanceof net.neoforged.bus.IWrapperListener wrapper)result.put(wrapper.getWithoutCheck(),owner);}});
        return result;
    }
    public static boolean safe(){
        if(LISTENERS==null || OWNERS==null)return false;
        try{
            var pre=((net.neoforged.bus.ListenerList)LISTENERS.invoke(net.neoforged.neoforge.common.NeoForge.EVENT_BUS,net.neoforged.neoforge.event.tick.LevelTickEvent.Pre.class)).getListeners();
            var post=((net.neoforged.bus.ListenerList)LISTENERS.invoke(net.neoforged.neoforge.common.NeoForge.EVENT_BUS,net.neoforged.neoforge.event.tick.LevelTickEvent.Post.class)).getListeners();
            if(previousPre==pre && previousPost==post)return accepted;
            previousPre=pre;previousPost=post;accepted=false;
            var owners=ownerIndex();
            for(var listener:pre)if(!NativeTemporalHooks.known(listener) && !NativeTemporalHooks.knownOwner(owners.get(listener)))return false;
            for(var listener:post)if(!NativeTemporalHooks.known(listener) && !NativeTemporalHooks.knownOwner(owners.get(listener)))return false;
            return accepted=true;
        }catch(ReflectiveOperationException|RuntimeException unavailable){return false;}
    }
    public static String diagnostic(){
        try{var list=(net.neoforged.bus.ListenerList)LISTENERS.invoke(net.neoforged.neoforge.common.NeoForge.EVENT_BUS,net.neoforged.neoforge.event.tick.LevelTickEvent.Post.class);var owners=ownerIndex();return java.util.Arrays.stream(list.getListeners()).map(l->{var o=owners.get(l);return l+" owner="+(o instanceof Class<?> c?c.getName():o==null?"UNKNOWN":o.getClass().getName());}).toList().toString();}
        catch(Exception unavailable){return "UNKNOWN";}
    }
}
