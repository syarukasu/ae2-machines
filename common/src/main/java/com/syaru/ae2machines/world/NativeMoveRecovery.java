package com.syaru.ae2machines.world;

import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import com.syaru.ae2machines.platform.PlatformData;

/** Runs before the first server tick. A prepared move is rolled back, never replayed as a new capture. */
public final class NativeMoveRecovery {
    private NativeMoveRecovery() {}
    public static void recover(MinecraftServer server) {
        var directory=server.getWorldPath(LevelResource.ROOT).resolve("data/ae2machines");
        if(!Files.isDirectory(directory))return;
        try(var files=Files.list(directory)) {
            for(var path:files.filter(p->p.getFileName().toString().endsWith(".factory")).toList()) {
                var name=path.getFileName().toString();var id=UUID.fromString(name.substring(0,name.length()-8));
                var journal=FactoryJournal.open(directory,id);var record=journal.read();var blueprint=record.getCompound("blueprint");
                if(blueprint.getInt("schema")!=4)continue;
                if(record.getString("state").equals("SNAPSHOT_SAVED")){journal.state("RESTORED");continue;}
                if(!record.getString("state").equals("WORLD_MOVE_PREPARED"))continue;
                var ordinary=server.getLevel(ResourceKey.create(Registries.DIMENSION,PlatformData.id(blueprint.getString("dimension"))));
                if(ordinary==null)throw new IllegalStateException("NATIVE_RECOVERY_DIMENSION_MISSING: "+id);
                var cell=NativeCellWorlds.open(ordinary,id);var runtime=NativeWorldRuntime.get(cell);
                var min=BlockPos.of(blueprint.getLong("first"));var max=BlockPos.of(blueprint.getLong("last"));
                runtime.state().initialRules(ordinary.getGameRules());runtime.state().claim(id,min.getX()>>4,min.getZ()>>4,max.getX()>>4,max.getZ()>>4,ordinary.getDayTime());runtime.retain();
                boolean reverse=record.getCompound("worldTransfer").getBoolean("restoring");
                NativeRegionMove.rollback(reverse?cell:ordinary,reverse?ordinary:cell,min,max,journal);
                org.slf4j.LoggerFactory.getLogger(NativeMoveRecovery.class).info("Native cell move {} recovered to {} before world ticks",id,journal.read().getString("state"));
            }
        }catch(IOException|RuntimeException failure){throw new ExternalHandoffs.PersistenceFailure(failure);}
        NativeWorldCheckpoint.recoverBeforeTicks(server);
    }
}
