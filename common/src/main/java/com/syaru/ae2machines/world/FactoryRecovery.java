package com.syaru.ae2machines.world;

import com.syaru.ae2machines.engine.RecoveryEpoch;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import java.io.IOException;
import java.util.*;

/** The epoch commit follows a synchronous world save, never precedes the AE2 CPU/storage checkpoint. */
public final class FactoryRecovery {
    private static final Map<MinecraftServer,RecoveryEpoch> SERVERS=new WeakHashMap<>();
    private FactoryRecovery() {}
    public static RecoveryEpoch epoch(MinecraftServer server) {
        return SERVERS.computeIfAbsent(server,s->{
            try { return new RecoveryEpoch(s.getWorldPath(LevelResource.ROOT).resolve("data/ae2machines/recovery")); }
            catch(IOException e) { throw new IllegalStateException("FACTORY_RECOVERY_UNREADABLE",e); }
        });
    }
    public static void stopping(MinecraftServer server) {
        var epoch=SERVERS.get(server);if(epoch==null)return;
        epoch.seal();
        try {
            server.getPlayerList().saveAll();
            if(!server.saveEverything(true,true,true))throw new IOException("World save did not run");
            epoch.savedShutdown();
        } catch(IOException|RuntimeException e) {
            org.slf4j.LoggerFactory.getLogger(FactoryRecovery.class).error("Normal AE2 jobs require reconciliation: shutdown checkpoint failed",e);
        }
    }
    public static void stopped(MinecraftServer server) { SERVERS.remove(server); }
}
