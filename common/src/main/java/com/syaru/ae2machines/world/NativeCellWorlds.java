package com.syaru.ae2machines.world;

import com.syaru.ae2machines.mixin.NativeWorldServerAccess;
import com.syaru.ae2machines.platform.*;
import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** A real level per cell; native structure managers and clocks are never shared between cells. */
public final class NativeCellWorlds {
    private static final Map<MinecraftServer,Set<ResourceKey<Level>>> RETIRED=new WeakHashMap<>();
    private NativeCellWorlds() {}
    public static ResourceKey<Level> key(UUID id) {
        return ResourceKey.create(Registries.DIMENSION,PlatformData.id("ae2machines:factory/"+id));
    }
    public static boolean owns(Level level) {
        var id=level.dimension().location();
        return id.getNamespace().equals("ae2machines") && id.getPath().startsWith("factory/");
    }
    public static ServerLevel open(ServerLevel source,UUID id) {
        var server=source.getServer();
        if(!server.isSameThread())throw new IllegalStateException("NATIVE_CELL_OFF_THREAD");
        var key=key(id);var existing=server.getLevel(key);if(existing!=null)return existing;
        var template=server.getLevel(NativeWorldRuntime.DIMENSION);
        if(template==null)throw new IllegalStateException("NATIVE_WORLD_TEMPLATE_MISSING");
        var access=(NativeWorldServerAccess)server;
        var level=NativeCellPlatform.create(source,key,template,access.ae2machines$storage());
        // A cell can be inserted by an existing world's tick. Preserve that iteration's old map.
        var levels=new LinkedHashMap<>(access.ae2machines$levels());levels.put(key,level);access.ae2machines$levels(levels);
        NativeCellPlatform.loaded(server,level);
        return level;
    }
    public static void retire(ServerLevel level) {
        if(!owns(level) || NativeWorldRuntime.get(level).running())throw new IllegalStateException("CELL_WORLD_STILL_ACTIVE");
        RETIRED.computeIfAbsent(level.getServer(),ignored->new LinkedHashSet<>()).add(level.dimension());
    }
    /** Close only after the server has finished iterating every level for this tick. Files are retained. */
    public static void endTick(MinecraftServer server) {
        var pending=RETIRED.remove(server);if(pending==null)return;
        for(var key:pending) {
            var level=server.getLevel(key);if(level==null)continue;
            try {
                NativeWorldRuntime.get(level).release();level.save(null,true,false);NativeCellPlatform.unloaded(server,level);level.close();
                com.syaru.ae2machines.runtime.NativeGridState.unload(level);
                if(com.syaru.ae2machines.runtime.NativeMachines.mek())com.syaru.ae2machines.compat.mek.MekNativeNetworks.unload(level);
                var access=(NativeWorldServerAccess)server;var levels=new LinkedHashMap<>(access.ae2machines$levels());levels.remove(key);access.ae2machines$levels(levels);server.markWorldsDirty();
            }catch(java.io.IOException|RuntimeException error){throw new ExternalHandoffs.PersistenceFailure(error);}
        }
    }
    public static void stopped(MinecraftServer server){
        com.syaru.ae2machines.runtime.NativeGridState.stopped(server);
        NativeCellLocations.stopped(server);
        RETIRED.remove(server);
        com.syaru.ae2machines.compat.mek.MekIidDraws.stopped(server);
        if(com.syaru.ae2machines.runtime.NativeMachines.mek())com.syaru.ae2machines.compat.mek.MekNativeNetworks.stopped(server);
    }
}
