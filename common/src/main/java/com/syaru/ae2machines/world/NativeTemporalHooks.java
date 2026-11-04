package com.syaru.ae2machines.world;

import java.lang.reflect.*;
import java.util.function.Consumer;

/** A world callback with unknown effects is a real event, not an ignorable empty interval. */
public final class NativeTemporalHooks {
    private NativeTemporalHooks(){}
    public static boolean knownOwner(Object owner){return owner instanceof Class<?> type?knownName(type.getName()):known(owner);}
    private static boolean knownName(String name){
        for(var owner:new String[]{"appeng.","mekanism.common.CommonWorldTickHandler","mekanism.common.lib.radiation.MeltdownLevelData","com.syaru.ae2machines.platform.MachinesMod","net.minecraftforge.common.ForgeInternalHandler","net.neoforged.neoforge.common.NeoForgeMod"})
            if(name.startsWith(owner))return true;
        return false;
    }
    public static boolean known(Object listener){return known(listener,0);}
    private static boolean known(Object listener,int depth){
        if(listener==null || depth>5)return false;
        String name=listener.getClass().getName(),readable=String.valueOf(listener);
        if(knownName(name))return true;
        for(var owner:new String[]{"appeng.","mekanism.common.CommonWorldTickHandler","com.syaru.ae2machines.platform.MachinesMod","net.minecraftforge.common.ForgeInternalHandler"})
            if(readable.contains(owner))return true;
        if(listener instanceof Enum<?> && name.endsWith("EventPriority"))return true;
        if(!name.startsWith("net.minecraftforge.eventbus.") && !name.startsWith("net.neoforged.bus."))return false;
        boolean delegate=false;
        for(Class<?> type=listener.getClass();type!=null;type=type.getSuperclass())for(var field:type.getDeclaredFields()){
            if(Modifier.isStatic(field.getModifiers()))continue;
            boolean callback=Consumer.class.isAssignableFrom(field.getType()) || field.getType().getName().endsWith("IEventListener") || field.getType().getName().endsWith(".EventListener");
            if(!callback)continue;
            try{if(!field.trySetAccessible() || !known(field.get(listener),depth+1))return false;delegate=true;}
            catch(ReflectiveOperationException|RuntimeException inaccessible){return false;}
        }
        return delegate;
    }
}
