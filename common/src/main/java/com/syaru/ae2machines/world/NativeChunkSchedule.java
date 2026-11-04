package com.syaru.ae2machines.world;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;

/** Native-order ticking holders, independent of installed machine mods or thermal contracts. */
final class NativeChunkSchedule {
    private Iterable<ChunkHolder> source;
    private List<ChunkHolder> ticking;
    private long epoch=Long.MIN_VALUE,revision=Long.MIN_VALUE;
    private int hostTick=Integer.MIN_VALUE;
    private int candidates;

    Iterable<ChunkHolder> select(NativeWorldRuntime runtime,Iterable<ChunkHolder> original){
        if(source!=original || epoch!=runtime.circuitChunkEpoch() || revision!=runtime.topologyRevision() || hostTick!=runtime.hostTick() || ticking==null){
            source=original;epoch=runtime.circuitChunkEpoch();revision=runtime.topologyRevision();
            hostTick=runtime.hostTick();
            var found=new ArrayList<ChunkHolder>();boolean ready=true;candidates=0;
            for(var holder:original){
                candidates++;
                if(holder.getTickingChunk()!=null)found.add(holder);
                else if(ChunkLevel.isBlockTicking(holder.getTicketLevel()))ready=false;
            }
            ticking=ready?List.copyOf(found):null;
        }
        if(ticking==null)return original;
        runtime.circuitChunkChecks(candidates-ticking.size());return ticking;
    }
}
