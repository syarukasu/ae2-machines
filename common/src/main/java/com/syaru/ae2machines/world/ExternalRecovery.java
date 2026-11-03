package com.syaru.ae2machines.world;

import com.syaru.ae2machines.platform.PlatformData;
import com.syaru.ae2machines.runtime.FactorySession;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.*;

/** Matches durable chunk generations, never guesses whether an external transfer committed. */
public final class ExternalRecovery {
    private static final String STAMP="ae2machines:checkpoint";
    private static final Map<ServerLevel,State> LEVELS=new WeakHashMap<>();
    private static final ThreadLocal<Boolean> HANDOFF_CAPTURE=ThreadLocal.withInitial(()->false);
    private static final class State {
        final Set<FactorySession> sessions=Collections.newSetFromMap(new IdentityHashMap<>());
        final Map<Long,CompoundTag> written=new HashMap<>();
        final Map<Long,UUID> loaded=new HashMap<>();
        final Map<Long,CompoundTag> loadedBlocks=new HashMap<>();
        final Map<UUID,String> reasons=new HashMap<>();
        final Set<Long> trackedChunks=new HashSet<>();
        long preparedTick=Long.MIN_VALUE;
    }
    private ExternalRecovery() {}
    private static State state(ServerLevel level){return LEVELS.computeIfAbsent(level,l->new State());}
    public static synchronized void register(ServerLevel level,FactorySession session){var s=state(level);s.sessions.add(session);s.preparedTick=Long.MIN_VALUE;}
    public static synchronized void unregister(ServerLevel level,FactorySession session){var s=LEVELS.get(level);if(s!=null){s.sessions.remove(session);s.reasons.remove(session.id());s.preparedTick=Long.MIN_VALUE;}}
    public static synchronized void unload(ServerLevel level){LEVELS.remove(level);}

    /** Save events add other mods' chunk data, but a prepared image is not a world-save witness. */
    public static void captureHandoffChunk(Runnable saveEvent) {
        boolean previous=HANDOFF_CAPTURE.get();HANDOFF_CAPTURE.set(true);
        try {saveEvent.run();} finally {HANDOFF_CAPTURE.set(previous);}
    }
    public static synchronized void handoffChunkWritten(ServerLevel level,ChunkPos pos,CompoundTag data) {
        var s=LEVELS.get(level);if(s==null)return;
        var copy=new CompoundTag();copy.putUUID(STAMP,data.getUUID(STAMP));
        copy.put("blocks",data.getList("block_entities",Tag.TAG_COMPOUND).copy());
        s.written.put(pos.toLong(),copy);
    }

    public static synchronized void chunkLoaded(ServerLevel level,ChunkPos pos,CompoundTag data) {
        var s=state(level);s.loaded.remove(pos.toLong());s.loadedBlocks.remove(pos.toLong());
        if(data.hasUUID(STAMP)) {
            s.loaded.put(pos.toLong(),data.getUUID(STAMP));var copy=new CompoundTag();copy.put("blocks",data.getList("block_entities",Tag.TAG_COMPOUND).copy());s.loadedBlocks.put(pos.toLong(),copy);
        }
    }
    public static synchronized void chunkSaved(ServerLevel level,ChunkPos pos,CompoundTag data) {
        if(HANDOFF_CAPTURE.get())return;
        var s=LEVELS.get(level);if(s==null || s.sessions.isEmpty())return;
        if(s.preparedTick!=level.getGameTime()) {
            s.preparedTick=level.getGameTime();s.trackedChunks.clear();
            for(var session:s.sessions)for(var be:session.recoveryParticipants())s.trackedChunks.add(new ChunkPos(be.getBlockPos()).toLong());
            s.written.keySet().retainAll(s.trackedChunks);
        }
        if(!s.trackedChunks.contains(pos.toLong()))return;
        // Only already serialized data is tagged; a token never asserts that disk I/O succeeded.
        data.putUUID(STAMP,UUID.randomUUID());
        var copy=new CompoundTag();copy.putUUID(STAMP,data.getUUID(STAMP));
        copy.put("blocks",data.getList("block_entities",Tag.TAG_COMPOUND).copy());
        s.written.put(pos.toLong(),copy);
    }
    public static synchronized void levelSaved(ServerLevel level) {
        var s=LEVELS.get(level);if(s==null)return;
        for(var session:List.copyOf(s.sessions)) {
            try {
                var participants=session.recoveryParticipants();if(participants.isEmpty()){s.reasons.put(session.id(),"UNSUPPORTED_PARTICIPANTS_OR_LEDGER");continue;}
                var chunks=new CompoundTag();var positions=new ListTag();boolean matches=true;
                for(var be:participants) {
                    var chunk=new ChunkPos(be.getBlockPos());var saved=s.written.get(chunk.toLong());
                    if(saved==null || !savedBlockMatches(saved,be)){s.reasons.put(session.id(),"SERIALIZED_BLOCK_MISMATCH:"+be.getBlockPos()+":"+be.getClass().getName());matches=false;break;}
                    chunks.putUUID(Long.toString(chunk.toLong()),saved.getUUID(STAMP));
                    positions.add(LongTag.valueOf(be.getBlockPos().asLong()));
                }
                if(matches){session.checkpointExternal(chunks,positions);s.reasons.put(session.id(),"CHECKPOINT_RECORDED");}
            } catch(RuntimeException failure) {
                org.slf4j.LoggerFactory.getLogger(ExternalRecovery.class).warn("Factory {} external checkpoint unavailable",session.id(),failure);
            }
        }
    }
    private static boolean savedBlockMatches(CompoundTag chunk,BlockEntity be) {
        if(be.getClass().getName().equals(RecoveryParticipants.REQUESTER)) {
            for(var value:chunk.getList("blocks",Tag.TAG_COMPOUND)) {
                var block=(CompoundTag)value;
                if(new BlockPos(block.getInt("x"),block.getInt("y"),block.getInt("z")).equals(be.getBlockPos()))
                    return requesterBlockMatches(block,PlatformData.saveBlock(be));
            }
            return false;
        }
        if(be.getClass().getName().equals(RecoveryParticipants.ADVANCED_CPU)) {
            for(var value:chunk.getList("blocks",Tag.TAG_COMPOUND)) {
                var block=(CompoundTag)value;
                if(new BlockPos(block.getInt("x"),block.getInt("y"),block.getInt("z")).equals(be.getBlockPos()))
                    return advancedBlockMatches(block,PlatformData.saveBlock(be));
            }
            return false;
        }
        var terminalSides=new HashSet<String>();
        if(be instanceof appeng.blockentity.networking.CableBusBlockEntity cable) {
            for(var side:net.minecraft.core.Direction.values()) {
                var part=cable.getPart(side);
                if(part!=null && part.getClass()==appeng.parts.reporting.ItemTerminalPart.class)terminalSides.add(side.getName());
            }
        }
        return serializedBlockMatches(chunk,be.getBlockPos(),PlatformData.saveBlock(be),be.getClass()==appeng.blockentity.crafting.CraftingBlockEntity.class,terminalSides);
    }
    static boolean serializedBlockMatches(CompoundTag chunk,BlockPos pos,CompoundTag current) {
        return serializedBlockMatches(chunk,pos,current,false);
    }
    static boolean serializedBlockMatches(CompoundTag chunk,BlockPos pos,CompoundTag current,boolean cpu) {
        return serializedBlockMatches(chunk,pos,current,cpu,Set.of());
    }
    static boolean serializedBlockMatches(CompoundTag chunk,BlockPos pos,CompoundTag current,boolean cpu,Set<String> terminalSides) {
        for(var value:chunk.getList("blocks",Tag.TAG_COMPOUND)) {
            var block=(CompoundTag)value;
            if(new BlockPos(block.getInt("x"),block.getInt("y"),block.getInt("z")).equals(pos)) {
                var normalized=block.copy();normalized.remove("keepPacked");
                var live=current.copy();
                // KeyTypeSelection serializes registry iteration order, not a user-defined order.
                for(var side:terminalSides)if(!sameUnorderedList(normalized.getCompound(side),live.getCompound(side),"enabledKeyTypes"))return false;
                if(!cpu)return normalized.equals(live);
                if(!sameUnorderedList(normalized,live,"inventory"))return false;
                var savedJob=normalized.getCompound("job");var liveJob=live.getCompound("job");
                if(!sameUnorderedList(savedJob,liveJob,"tasks") || !sameUnorderedList(savedJob,liveJob,"waitingFor"))return false;
                // AE2's elapsedTime is a wall-clock display value, not work or quantity accounting.
                normalizeCpuClock(savedJob.getCompound("timeTracker"));normalizeCpuClock(liveJob.getCompound("timeTracker"));
                return normalized.equals(live);
            }
        }
        return false;
    }
    static boolean requesterBlockMatches(CompoundTag saved,CompoundTag current) {
        var left=saved.copy();var right=current.copy();left.remove("keepPacked");
        normalizeRequester(left);normalizeRequester(right);
        return left.equals(right);
    }
    private static void normalizeRequester(CompoundTag block) {
        var links=block.getCompound("request_status");
        for(var slot:List.copyOf(links.getAllKeys())) {
            var link=links.getCompound(slot);
            // LinkState moves a completed request to ExportState without changing owned inventory.
            if(link.getAllKeys().equals(Set.of("craftId","done","canceled","req","standalone"))
                    && link.hasUUID("craftId") && link.getBoolean("done") && !link.getBoolean("canceled")
                    && link.getBoolean("req") && !link.getBoolean("standalone"))links.remove(slot);
        }
        var requests=block.getCompound("requests");
        for(var slot:requests.getAllKeys())requests.getCompound(slot).remove("status"); // Client display, not request enablement.
    }
    private static void normalizeCpuClock(CompoundTag clock) {
        clock.remove("elapsedTime");
        // ElapsedTimeTracker initializes every AEKeyType to zero on load, including absent entries.
        for(var name:List.of("startedWork","completedWork")) {
            var work=clock.getCompound(name);
            for(var key:List.copyOf(work.getAllKeys()))if(work.contains(key,Tag.TAG_LONG) && work.getLong(key)==0)work.remove(key);
        }
    }
    static boolean advancedBlockMatches(CompoundTag saved,CompoundTag current) {
        var left=saved.copy();var right=current.copy();left.remove("keepPacked");
        if(left.contains("cpuList")!=right.contains("cpuList"))return false;
        if(left.contains("cpuList") && (!(left.get("cpuList") instanceof ListTag l) || !(right.get("cpuList") instanceof ListTag r)
                || !l.isEmpty() && l.getElementType()!=Tag.TAG_COMPOUND || !r.isEmpty() && r.getElementType()!=Tag.TAG_COMPOUND))return false;
        var a=left.getList("cpuList",Tag.TAG_COMPOUND);var b=right.getList("cpuList",Tag.TAG_COMPOUND);
        removeRetiredCpus(a);removeRetiredCpus(b);
        if(a.size()!=b.size())return false;
        var byId=new HashMap<String,CompoundTag>();
        for(var value:b){var cpu=(CompoundTag)value;if(!cpu.contains("key",Tag.TAG_STRING) || byId.put(cpu.getString("key"),cpu)!=null)return false;}
        for(var value:a) {
            var cpu=(CompoundTag)value;var other=byId.remove(cpu.getString("key"));if(other==null)return false;
            var x=cpu.getCompound("cpu");var y=other.getCompound("cpu");
            if(!sameUnorderedList(x,y,"inventory"))return false;
            var j=x.getCompound("job");var k=y.getCompound("job");
            if(!sameUnorderedList(j,k,"tasks") || !sameUnorderedList(j,k,"waitingFor"))return false;
            normalizeCpuClock(j.getCompound("timeTracker"));normalizeCpuClock(k.getCompound("timeTracker"));
            if(!cpu.equals(other))return false;
        }
        left.remove("cpuList");right.remove("cpuList");return byId.isEmpty() && left.equals(right);
    }
    private static void removeRetiredCpus(ListTag cpus) {
        // AdvancedAE releases a virtual CPU after finishJob clears its job and all remaining items.
        // Do not erase jobs, residual materials, or extension-owned fields with this normalization.
        cpus.removeIf(value->{
            var entry=(CompoundTag)value;var cpu=entry.getCompound("cpu");
            return entry.getAllKeys().equals(Set.of("key","bytes","cpu")) && entry.contains("key",Tag.TAG_STRING)
                    && entry.contains("bytes",Tag.TAG_LONG) && cpu.getAllKeys().equals(Set.of("inventory"))
                    && cpu.get("inventory") instanceof ListTag items && items.isEmpty();
        });
    }
    private static boolean sameUnorderedList(CompoundTag a,CompoundTag b,String key) {
        if(!a.contains(key) && !b.contains(key))return true;
        if(!(a.get(key) instanceof ListTag left) || !(b.get(key) instanceof ListTag right))return false;
        var counts=new HashMap<Tag,Integer>();for(var t:left)counts.merge(t,1,Integer::sum);for(var t:right)counts.merge(t,-1,Integer::sum);
        if(counts.values().stream().anyMatch(n->n!=0))return false;
        a.remove(key);b.remove(key);return true;
    }
    private static void differences(Tag a,Tag b,String path,Set<String> out) {
        if(Objects.equals(a,b))return;
        if(a instanceof CompoundTag left && b instanceof CompoundTag right) {
            var keys=new TreeSet<String>();keys.addAll(left.getAllKeys());keys.addAll(right.getAllKeys());
            for(var key:keys)differences(left.get(key),right.get(key),path+"/"+key,out);
        } else out.add(path);
    }
    public static synchronized String status(ServerLevel level,UUID id){return state(level).reasons.getOrDefault(id,"NO_WORLD_CHECKPOINT");}
    public static synchronized boolean permits(ServerLevel level,CompoundTag record) {
        return failure(level,record)==null;
    }
    public static synchronized String failure(ServerLevel level,CompoundTag record) {
        var checkpoint=record.getCompound("externalCheckpoint");
        var assets=assets(record);if(assets==null || checkpoint.getInt("schema")!=1
                || !assets.equals(checkpoint.getCompound("assets")))return "ASSET_WITNESS_MISMATCH";
        var chunks=checkpoint.getCompound("chunks");if(chunks.isEmpty())return "NO_CHUNK_WITNESS";
        var state=LEVELS.get(level);if(state==null || !tokensMatch(chunks,state.loaded))return "LOADED_CHUNK_WITNESS_MISMATCH";
        for(var key:chunks.getAllKeys()) {
            if(!chunks.hasUUID(key))return "INVALID_CHUNK_WITNESS";
            long pos;
            try {pos=Long.parseLong(key);}catch(NumberFormatException e){return "INVALID_CHUNK_POSITION";}
            var chunk=new ChunkPos(pos);
            if(!level.hasChunk(chunk.x,chunk.z))return "CHUNK_NOT_LOADED";
        }
        var positions=checkpoint.getList("participants",Tag.TAG_LONG);if(positions.isEmpty())return "NO_PARTICIPANT_WITNESS";
        for(var value:positions) {
            var pos=BlockPos.of(((LongTag)value).getAsLong());var block=level.getBlockEntity(pos);
            var raw=state.loadedBlocks.get(new ChunkPos(pos).toLong());
            if(block==null || raw==null)return "PARTICIPANT_MISSING:"+pos;
            if(!savedBlockMatches(raw,block)) {
                var current=PlatformData.saveBlock(block);var keys=new TreeSet<String>();
                for(var entry:raw.getList("blocks",Tag.TAG_COMPOUND)) {
                    var saved=(CompoundTag)entry;
                    if(saved.getInt("x")!=pos.getX() || saved.getInt("y")!=pos.getY() || saved.getInt("z")!=pos.getZ())continue;
                    var normalized=saved.copy();normalized.remove("keepPacked");differences(normalized,current,"",keys);
                }
                return "LIVE_PARTICIPANT_CHANGED:"+pos+":"+block.getClass().getSimpleName()+":"+keys;
            }
        }
        return null;
    }
    static boolean tokensMatch(CompoundTag chunks,Map<Long,UUID> loaded) {
        if(chunks.isEmpty())return false;
        for(var key:chunks.getAllKeys()) {
            if(!chunks.hasUUID(key))return false;
            try {if(!chunks.getUUID(key).equals(loaded.get(Long.parseLong(key))))return false;}
            catch(NumberFormatException e){return false;}
        }
        return true;
    }
    public static boolean waitingForChunks(ServerLevel level,CompoundTag record) {
        var chunks=record.getCompound("externalCheckpoint").getCompound("chunks");
        for(var key:chunks.getAllKeys()) {
            try {var pos=new ChunkPos(Long.parseLong(key));if(!level.hasChunk(pos.x,pos.z))return true;}
            catch(NumberFormatException e){return false;}
        }
        return false;
    }
    /** Only paid progress may move past the witness. Any new handoff invalidates it. */
    public static CompoundTag assets(CompoundTag record) {
        if(record.getCompound("blueprint").getInt("schema")!=3)return null;
        var result=new CompoundTag();
        for(var key:List.of("blueprint","networkCells","networkNative","state","accelerator")) {
            if(!record.contains(key))return null;result.put(key,record.get(key).copy());
        }
        var lanes=record.getCompound("runtimeLanes").copy();
        if(lanes.isEmpty())return null;
        for(var key:lanes.getAllKeys()) {
            var lane=lanes.getCompound(key);var runtime=lane.getCompound("runtime");
            if(!Set.of(3,4,5).contains(runtime.getInt("schema")) || lane.getBoolean("inputIntent") || lane.getBoolean("deliveryIntent") || runtime.getBoolean("quarantined")
                    || !runtime.getList("forgotten",Tag.TAG_COMPOUND).isEmpty() || lane.contains("nativeCompletion"))return null;
            if(runtime.getList("jobs",Tag.TAG_COMPOUND).size()!=(lane.hasUUID("normalJob")?1:0))return null;
            lane.remove("normalEpoch");
            // ACO's acknowledgement releases only a work gate; the exact owned job remains in the witness.
            lane.remove("batchReceipts");lane.remove("acoSourceCommitted");
            if(!lane.getBoolean("acoBatchJob"))lane.remove("acoBatchJob");
            runtime.remove("lastWorkTick");runtime.remove("power");runtime.remove("stored");
            // Native work only transforms already-owned inputs. IDs remain in the witness;
            // material/energy progress is validated against each job when RuntimeCodec restores it.
            for(var value:runtime.getList("nativeJobs",Tag.TAG_COMPOUND)) {
                var nativeJob=(CompoundTag)value;
                for(var name:List.of("remaining","produced","energyUsed","calls"))nativeJob.remove(name);
            }
            for(var value:runtime.getList("jobs",Tag.TAG_COMPOUND)) {
                var job=(CompoundTag)value;
                if(!job.getString("digest").equals("ae2-normal") || !lane.hasUUID("normalJob")
                        || !lane.getUUID("normalJob").equals(job.getUUID("id"))
                        || !Set.of("RUNNING","OUTPUT_READY").contains(job.getString("state")))return null;
                for(var name:List.of("completed","phases","energyUsed","state"))job.remove(name);
            }
        }
        result.put("lanes",lanes);return result;
    }
}
