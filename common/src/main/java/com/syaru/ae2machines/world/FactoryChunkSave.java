package com.syaru.ae2machines.world;

import com.syaru.ae2machines.platform.PlatformData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import java.util.*;

/** Flush only the loaded chunks whose ownership is changing, not unrelated generation futures. */
final class FactoryChunkSave {
    private FactoryChunkSave() {}
    static void flush(ServerLevel level,ListTag blocks) {
        if(!level.getServer().isSameThread())throw new IllegalStateException("Factory save must run on the server thread");
        var positions=new LinkedHashSet<ChunkPos>();
        for(var value:blocks)positions.add(new ChunkPos(BlockPos.of(((CompoundTag)value).getLong("pos"))));
        var map=level.getChunkSource().chunkMap;
        var written=new LinkedHashMap<ChunkPos,CompoundTag>();
        for(var pos:positions) {
            var chunk=level.getChunkSource().getChunkNow(pos.x,pos.z);
            if(chunk==null)throw new IllegalStateException("Factory chunk unloaded during save: "+pos);
            level.getPoiManager().flush(pos);
            var data=ChunkSerializer.write(level,chunk);
            PlatformData.chunkSaveEvent(level,chunk,data);
            map.write(pos,data);written.put(pos,data.copy());
            // Leave the dirty flag set: another save must not lose changes from event listeners.
        }
        map.flushWorker();
        for(var entry:written.entrySet()) {
            var stored=map.read(entry.getKey()).join().orElseThrow();
            if(!stored.equals(entry.getValue()))throw new IllegalStateException("Factory chunk save mismatch: "+entry.getKey());
        }
    }
}
