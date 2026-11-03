package com.syaru.ae2machines.world;

import com.syaru.ae2machines.runtime.NativeWorldSession;
import java.util.*;
import net.minecraft.server.MinecraftServer;

/** Live owners only; resolving a location must never open a world or load a cell. */
public final class NativeCellLocations {
    private static final Map<MinecraftServer,Map<UUID,NativeWorldSession>> LIVE=new WeakHashMap<>();
    public static void attach(UUID id,NativeWorldSession session){
        var server=session.world().getServer();check(server);
        var previous=LIVE.computeIfAbsent(server,ignored->new LinkedHashMap<>()).putIfAbsent(id,session);
        if(previous!=null && previous!=session)throw new IllegalStateException("Duplicate live cell owner");
    }
    public static void detach(UUID id,NativeWorldSession session){var server=session.world().getServer();check(server);var map=LIVE.get(server);if(map!=null)map.remove(id,session);}
    public static List<NativeWorldSession> sessions(MinecraftServer server){check(server);var map=LIVE.get(server);return map==null?List.of():List.copyOf(map.values());}
    public static NativeWorldSession find(MinecraftServer server,UUID id){check(server);var map=LIVE.get(server);return map==null?null:map.get(id);}
    public static void stopped(MinecraftServer server){check(server);LIVE.remove(server);}
    private static void check(MinecraftServer server){if(!server.isSameThread())throw new IllegalStateException("Cell locations require server thread");}
    private NativeCellLocations(){}
}
