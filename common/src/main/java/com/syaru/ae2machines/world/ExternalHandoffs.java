package com.syaru.ae2machines.world;

import appeng.me.cluster.implementations.CraftingCPUCluster;
import com.syaru.ae2machines.engine.HandoffLog;
import com.syaru.ae2machines.platform.PlatformData;
import com.syaru.ae2machines.runtime.FactorySession;
import com.syaru.ae2machines.runtime.CountedCraftingProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.storage.LevelResource;
import java.io.*;
import java.util.*;

/**
 * Closed, explicitly supported participants only. A durable before/after image spans the CPU caller,
 * factory journal and affected chunks. Recovery runs before those chunks become live.
 */
public final class ExternalHandoffs {
    private static final String STAMP="ae2machines:checkpoint";
    private static final Map<net.minecraft.server.MinecraftServer,Transaction> ACTIVE=new IdentityHashMap<>();
    private ExternalHandoffs() {}
    public static final class PersistenceFailure extends Error {
        public PersistenceFailure(Throwable cause){super("External handoff is unresolved; stopping to preserve its durable recovery image",cause);}
    }
    private static HandoffLog log(ServerLevel level) {
        return new HandoffLog(level.getServer().getWorldPath(LevelResource.ROOT).resolve("data/ae2machines-handoffs"));
    }
    public static Map<Long,CompoundTag> cpuBefore(CraftingCPUCluster cpu) {
        var result=new HashMap<Long,CompoundTag>();
        cpu.getBlockEntities().forEachRemaining(be->result.put(be.getBlockPos().asLong(),PlatformData.saveBlock(be)));
        return result;
    }
    public static Transaction begin(FactorySession session,Map<Long,CompoundTag> beforeCpu) {
        return begin((CountedCraftingProvider)session,beforeCpu);
    }
    public static Transaction begin(CountedCraftingProvider session,Map<Long,CompoundTag> beforeCpu) {
        var owner=session.handoffOwner();var level=owner.handoffLevel();
        if(!level.getServer().isSameThread())throw new IllegalStateException("External handoff requires server thread");
        if(ACTIVE.containsKey(level.getServer()))throw new PersistenceFailure(new IllegalStateException("Nested external handoff"));
        var participants=owner.recoveryParticipants();
        // Custom cells/requesters/CPUs cannot be made transactional by guessing their persistence.
        if(participants.isEmpty())return null;
        if(!RecoveryParticipants.linksCovered(participants))return null;
        var journal=owner.handoffJournal();
        try {
            var tx=new Transaction(level,journal,participants,owner.nativeWorldHandoff());
            tx.entry=tx.log.prepare(journal.id(),encode(tx.image(journal.read(),beforeCpu)));
            journal.beginHandoff();
            ACTIVE.put(level.getServer(),tx);return tx;
        } catch(Exception failure){throw new PersistenceFailure(failure);}
    }
    public static boolean supports(FactorySession session) {
        return supports((CountedCraftingProvider)session);
    }
    public static boolean supports(CountedCraftingProvider session) {
        var participants=session.handoffOwner().recoveryParticipants();
        return !participants.isEmpty() && RecoveryParticipants.linksCovered(participants);
    }
    public static boolean supportsNativeOutput(com.syaru.ae2machines.runtime.NativeWorldSession session) {
        return !NativeWorldCheckpoint.outputParticipants(session.host(),session.world()).isEmpty();
    }
    public static boolean nativeHandoffActive(FactoryJournal journal) {
        for(var tx:ACTIVE.values())if(tx.journal==journal && tx.nativeOutput && !tx.closed)return true;
        return false;
    }
    public static Transaction beginNativeOutput(com.syaru.ae2machines.runtime.NativeWorldSession session,FactoryJournal journal) {
        var level=(ServerLevel)session.host().getLevel();
        if(!level.getServer().isSameThread())throw new IllegalStateException("Native output handoff requires server thread");
        if(ACTIVE.containsKey(level.getServer()))throw new PersistenceFailure(new IllegalStateException("Nested external handoff"));
        var participants=NativeWorldCheckpoint.outputParticipants(session.host(),session.world());
        if(participants.isEmpty())return null;
        try {
            var tx=new Transaction(level,journal,participants,true);
            tx.entry=tx.log.prepare(journal.id(),encode(tx.image(journal.read(),Map.of())));
            journal.beginHandoff();ACTIVE.put(level.getServer(),tx);return tx;
        }catch(Exception failure){throw new PersistenceFailure(failure);}
    }
    public static boolean validOwnedPowerSources(com.syaru.ae2machines.runtime.NativeWorldSession session,Collection<BlockEntity> sources){
        var level=session.host().getLevel();if(sources==null || sources.isEmpty())return false;
        for(var source:sources)if(source==null || source==session.host() || source.isRemoved() || source.getLevel()!=level || !level.hasChunkAt(source.getBlockPos()) || level.getBlockEntity(source.getBlockPos())!=source)return false;
        return true;
    }
    public static Transaction beginRestorationRecovery(ServerLevel level,FactoryJournal journal,Set<BlockEntity> participants,BlockPos min,BlockPos max){
        if(!level.getServer().isSameThread())throw new IllegalStateException("Cell recovery requires server thread");
        if(ACTIVE.containsKey(level.getServer()))throw new PersistenceFailure(new IllegalStateException("Nested cell recovery"));
        if(participants.isEmpty())throw new IllegalArgumentException("RESTORATION_RECIPIENT_MISSING");
        // Native multiblock caches live outside chunks. Validate their full boundary before custody changes.
        com.syaru.ae2machines.platform.MekStructureStorage.snapshot(level,List.copyOf(participants),min,max);
        try{
            var tx=new Transaction(level,journal,participants,true);tx.restorationMin=min;tx.restorationMax=max;
            tx.entry=tx.log.prepare(journal.id(),encode(tx.image(journal.read(),Map.of())));
            journal.beginHandoff();ACTIVE.put(level.getServer(),tx);return tx;
        }catch(Exception failure){throw new PersistenceFailure(failure);}
    }
    public static Transaction beginOwnedPower(com.syaru.ae2machines.runtime.NativeWorldSession session,FactoryJournal journal,Collection<BlockEntity> sources){
        var level=(ServerLevel)session.host().getLevel();
        if(!level.getServer().isSameThread())throw new IllegalStateException("Owned power handoff requires server thread");
        if(ACTIVE.containsKey(level.getServer()))throw new PersistenceFailure(new IllegalStateException("Nested external handoff"));
        if(!validOwnedPowerSources(session,sources))return null;
        var participants=Collections.newSetFromMap(new IdentityHashMap<BlockEntity,Boolean>());
        for(var source:sources){
            participants.add(source);
        }
        participants.add(session.host());
        try{
            var tx=new Transaction(level,journal,participants,true);tx.entry=tx.log.prepare(journal.id(),encode(tx.image(journal.read(),Map.of())));
            journal.beginHandoff();ACTIVE.put(level.getServer(),tx);return tx;
        }catch(Exception failure){throw new PersistenceFailure(failure);}
    }
    public static final class Transaction {
        private final ServerLevel level;
        private final FactoryJournal journal;
        private final Set<BlockEntity> participants;
        private final UUID token=UUID.randomUUID();
        private final HandoffLog log;
        private final boolean nativeOutput;
        private HandoffLog.Entry entry;
        private boolean closed;
        private BlockPos restorationMin,restorationMax;
        private Transaction(ServerLevel level,FactoryJournal journal,Set<BlockEntity> participants,boolean nativeOutput) {
            this.level=level;this.journal=journal;this.participants=Set.copyOf(participants);this.nativeOutput=nativeOutput;log=log(level);
        }
        public boolean owns(FactorySession session) {return owns((CountedCraftingProvider)session);}
        public boolean owns(CountedCraftingProvider session) {return !closed && session.handoffOwner().handoffJournal()==journal;}
        private CompoundTag image(CompoundTag record,Map<Long,CompoundTag> override) {
            var assets=assets(record);
            if(assets==null)throw new IllegalStateException("Handoff ledger is not a native normal job");
            if(restorationMin!=null)com.syaru.ae2machines.compat.mek.MekRestorationStorage.refreshSaveShares(List.copyOf(participants));
            var chunks=new CompoundTag();var tokens=new CompoundTag();var positions=new ListTag();
            for(var be:participants) {
                if(level.getBlockEntity(be.getBlockPos())!=be)throw new IllegalStateException("Handoff participant replaced");
                long pos=be.getBlockPos().asLong();positions.add(LongTag.valueOf(pos));
                var chunkPos=new ChunkPos(be.getBlockPos());String key=Long.toString(chunkPos.toLong());
                if(chunks.contains(key))continue;
                var chunk=level.getChunkSource().getChunkNow(chunkPos.x,chunkPos.z);
                if(chunk==null)throw new IllegalStateException("Handoff chunk unloaded");
                var raw=ChunkSerializer.write(level,chunk);
                ExternalRecovery.captureHandoffChunk(()->PlatformData.chunkSaveEvent(level,chunk,raw));
                raw.putUUID(STAMP,token);
                replaceBlocks(raw,override);
                chunks.put(key,raw);tokens.putUUID(key,token);
            }
            var checkpoint=new CompoundTag();checkpoint.putInt("schema",1);checkpoint.put("assets",assets);
            checkpoint.put("chunks",tokens);checkpoint.put("participants",positions);
            record=record.copy();record.put("externalCheckpoint",checkpoint);
            var image=new CompoundTag();image.putInt("schema",1);
            image.putString("dimension",level.dimension().location().toString());
            if(restorationMin!=null)image.put("restorationMekCaches",com.syaru.ae2machines.platform.MekStructureStorage.snapshot(level,List.copyOf(participants),restorationMin,restorationMax));
            image.put("factory",record);image.put("chunks",chunks);return image;
        }
        private CompoundTag assets(CompoundTag record) {
            if(restorationMin!=null){
                if(!record.getString("state").equals("RESTORED") || record.getCompound(RestorationRemainder.KEY).getInt("schema")!=1)throw new IllegalStateException("Restoration ledger missing");
                return record.getCompound(RestorationRemainder.KEY).copy();
            }
            if(!nativeOutput)return ExternalRecovery.assets(record);
            if(record.getCompound("blueprint").getInt("schema")!=4 || record.getCompound("worldBatches").getInt("schema")!=1 && record.getCompound("variableMekRequests").getInt("schema")!=1)
                throw new IllegalStateException("Native output handoff ledger missing");
            var assets=new CompoundTag();assets.put("worldBatches",record.getCompound("worldBatches").copy());
            if(record.contains("variableMekRequests"))assets.put("variableMekRequests",record.getCompound("variableMekRequests").copy());return assets;
        }
        /** Called only after AE2 has updated waitingFor, task count and pattern energy. */
        public void commit() {
            if(closed)throw new IllegalStateException("Handoff already closed");
            try {
                var after=image(journal.read(),Map.of());
                entry=log.decide(entry,encode(after));
                if(restorationMin!=null)level.getServer().overworld().getDataStorage().save();
                journal.finishHandoff(after.getCompound("factory"));
                writeChunks(level,after.getCompound("chunks"),false);
                log.clean(entry);closed=true;ACTIVE.remove(level.getServer());
            } catch(Exception failure){throw new PersistenceFailure(failure);}
        }
        /** A declined provider did not take ownership; CPU will reinject its temporary inputs. */
        public void declined() {
            if(closed)throw new IllegalStateException("Handoff already closed");
            try {
                var before=assets(decode(entry.image()).getCompound("factory"));
                if(!Objects.equals(before,assets(journal.read())))
                    throw new IllegalStateException("Declined provider changed factory ownership");
                journal.finishHandoff(journal.read());
                log.clean(entry);closed=true;ACTIVE.remove(level.getServer());
            }
            catch(Exception failure){throw new PersistenceFailure(failure);}
        }
    }
    static void replaceBlocks(CompoundTag raw,Map<Long,CompoundTag> replacements) {
        var blocks=raw.getList("block_entities",Tag.TAG_COMPOUND);
        var remaining=new HashSet<>(replacements.keySet());
        var chunkPos=new ChunkPos(raw.getInt("xPos"),raw.getInt("zPos"));
        remaining.removeIf(pos->!new ChunkPos(BlockPos.of(pos)).equals(chunkPos));
        for(int i=0;i<blocks.size();i++) {
            var block=blocks.getCompound(i);long pos=new BlockPos(block.getInt("x"),block.getInt("y"),block.getInt("z")).asLong();
            var replacement=replacements.get(pos);
            if(replacement!=null){blocks.set(i,replacement.copy());remaining.remove(pos);}
        }
        if(!remaining.isEmpty())throw new IllegalStateException("CPU before-image is absent from serialized chunk");
    }
    private static byte[] encode(CompoundTag image) throws IOException {
        var bytes=new ByteArrayOutputStream();NbtIo.write(image,new DataOutputStream(bytes));return bytes.toByteArray();
    }
    private static CompoundTag decode(byte[] image) throws IOException {
        var result=PlatformData.readNbt(new DataInputStream(new ByteArrayInputStream(image)));
        if(result==null || result.getInt("schema")!=1 || result.getCompound("chunks").isEmpty()
                || !result.getCompound("factory").hasUUID("id"))throw new IOException("Invalid external handoff image");
        return result;
    }
    private static void writeChunks(ServerLevel level,CompoundTag chunks,boolean recovery) {
        var map=level.getChunkSource().chunkMap;
        for(var key:chunks.getAllKeys()) {
            var pos=new ChunkPos(Long.parseLong(key));
            if(recovery && level.getChunkSource().getChunkNow(pos.x,pos.z)!=null)
                throw new IllegalStateException("Cannot replay external handoff over a live chunk");
            map.write(pos,chunks.getCompound(key).copy());
        }
        map.flushWorker();
        for(var key:chunks.getAllKeys()) {
            var actual=map.read(new ChunkPos(Long.parseLong(key))).join().orElseThrow();
            if(!actual.equals(chunks.getCompound(key)))throw new IllegalStateException("Handoff chunk was not durably written");
            ExternalRecovery.handoffChunkWritten(level,new ChunkPos(Long.parseLong(key)),actual);
        }
    }
    /** LevelEvent.Load is before spawn preparation/ticking. Never overwrite live participants. */
    public static void recoverBeforeChunks(ServerLevel level) {
        if(!level.getServer().isSameThread())throw new IllegalStateException("Handoff replay requires server thread");
        try {
            var log=log(level);
            for(var entry:log.pending()) {
                var image=decode(entry.image());
                if(!image.getString("dimension").equals(level.dimension().location().toString()))continue;
                var record=image.getCompound("factory");
                if(!entry.owner().equals(record.getUUID("id")))throw new IOException("Handoff factory identity mismatch");
                // Keep the decision pending until every chunk AND the factory ledger is durable.
                if(image.contains("restorationMekCaches")){
                    com.syaru.ae2machines.platform.MekStructureStorage.restore(level,image.getList("restorationMekCaches",Tag.TAG_COMPOUND),List.of());
                    level.getServer().overworld().getDataStorage().save();
                }
                writeChunks(level,image.getCompound("chunks"),true);
                FactoryJournal.open(level,entry.owner()).write(record);
                log.clean(entry);
            }
        } catch(Exception failure){throw new PersistenceFailure(failure);}
    }
    public static void stopped(net.minecraft.server.MinecraftServer server){ACTIVE.remove(server);}
}
