package com.syaru.ae2machines.platform;

import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.util.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.*;

/** Forge 1.10.53 subscribes to the common TickEvent but returns on a mismatched type. */
final class SparkLevelObservers {
    private static final Map<String,String> AUDITED=Map.of(
            "me.lucko.spark.forge.ForgeTickHook","13cb455c85cafdd3a089729a241b119f3193316ecd78de1383f519a7f71850a0",
            "me.lucko.spark.forge.ForgeTickReporter","8fdf000ad9fc6f6f07322956608bc0ea0d42d167b1450cbc9b7e801e6da55f83");
    private static final Field OWNERS=field(EventBus.class,"listeners");
    private static Field field(Class<?> type,String name){
        try{var f=type.getDeclaredField(name);return f.trySetAccessible()?f:null;}
        catch(ReflectiveOperationException|RuntimeException unavailable){return null;}
    }
    private static final ClassValue<Optional<Field>> FILTER=new ClassValue<>(){
        @Override protected Optional<Field> computeValue(Class<?> type){
            String expected=AUDITED.get(type.getName());
            if(expected==null || !com.syaru.ae2machines.compat.mek.MekThermalHooks.safe(type))return Optional.empty();
            try(var bytes=type.getResourceAsStream("/"+type.getName().replace('.','/')+".class")){
                if(bytes!=null && HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.readAllBytes())).equals(expected))
                    return Optional.ofNullable(field(type,"type"));
            }catch(Exception unavailable){return Optional.empty();}
            return Optional.empty();
        }
    };
    static boolean ignoresLevel(Object listener){
        if(OWNERS==null || listener.getClass()!=ASMEventHandler.class)return false;
        try{
            var owners=(Map<?,?>)OWNERS.get(MinecraftForge.EVENT_BUS);
            for(var entry:owners.entrySet()){
                Object owner=entry.getKey();
                if(!AUDITED.containsKey(owner.getClass().getName()) || !(entry.getValue() instanceof List<?> list)
                        || list.stream().noneMatch(l->l==listener))continue;
                var filter=FILTER.get(owner.getClass());if(filter.isEmpty())return false;
                Object type=filter.get().get(owner);
                return type==TickEvent.Type.SERVER || type==TickEvent.Type.CLIENT;
            }
        }catch(ReflectiveOperationException|RuntimeException unavailable){return false;}
        return false;
    }
}
