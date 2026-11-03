package com.syaru.ae2machines.world;

import com.syaru.ae2machines.engine.AtomicSnapshotStore;
import com.syaru.ae2machines.platform.PlatformData;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import java.io.*;
import java.nio.file.Path;
import java.util.UUID;
import java.util.IdentityHashMap;

/** Server-owned, forced, atomic journal; the cell carries only its UUID. */
public final class FactoryJournal {
    private final AtomicSnapshotStore store;
    private final UUID id;
    private long generation;
    private CompoundTag record;
    private int transactionDepth;
    private boolean handoffBatch,flushing;
    private final IdentityHashMap<Object,Runnable> checkpoints=new IdentityHashMap<>();
    private FactoryJournal(Path directory,UUID id) {
        this.id=id;
        store=new AtomicSnapshotStore(directory);
    }
    public static FactoryJournal create(ServerLevel level,CompoundTag blueprint) {
        return create(directory(level),blueprint);
    }
    static FactoryJournal create(Path directory,CompoundTag blueprint) {
        var journal=new FactoryJournal(directory,UUID.randomUUID());
        var data=new CompoundTag();data.putInt("schema",1);data.putUUID("id",journal.id);
        data.putString("state","SNAPSHOT_SAVED");data.put("blueprint",blueprint.copy());
        journal.write(data);return journal;
    }
    public static FactoryJournal open(ServerLevel level,UUID id) {
        return open(directory(level),id);
    }
    private static Path directory(ServerLevel level){return level.getServer().getWorldPath(LevelResource.ROOT).resolve("data/ae2machines");}
    static FactoryJournal open(Path directory,UUID id) {
        var journal=new FactoryJournal(directory,id);
        try {
            var stored=journal.store.read(id);journal.generation=stored.generation();
            journal.record=PlatformData.readNbt(new DataInputStream(new ByteArrayInputStream(stored.payload())));
            if(journal.record==null || journal.record.getInt("schema")!=1 || !id.equals(journal.record.getUUID("id")))
                throw new IOException("Invalid factory identity");
            return journal;
        } catch(IOException|RuntimeException e) { throw new IllegalStateException("FACTORY_JOURNAL_UNREADABLE: "+id,e); }
    }
    public UUID id() { return id; }
    public CompoundTag read() { flushCheckpoints();return record.copy(); }
    /** Only ExternalHandoffs may enter this scope, after forcing its complete rollback image. */
    void beginHandoff() {
        if(handoffBatch || transactionDepth!=0)throw new IllegalStateException("Nested handoff checkpoint");
        handoffBatch=true;
    }
    public boolean deferCheckpoint(Object owner,Runnable save) {
        if((!handoffBatch && transactionDepth==0) || flushing)return false;
        checkpoints.put(owner,save);return true;
    }
    /** Private owned work only; any journal reader must observe its current checkpoint. */
    public void stageOwnedCheckpoint(Object owner,Runnable save){checkpoints.put(owner,save);}
    public void removeStagedCheckpoint(Object owner){checkpoints.remove(owner);}
    private void flushCheckpoints() {
        if(flushing || checkpoints.isEmpty())return;
        var pending=java.util.List.copyOf(checkpoints.values());checkpoints.clear();flushing=true;
        try {for(var save:pending)save.run();} finally {flushing=false;}
    }
    /** The after-image is already durable in the handoff decision before publishing this snapshot. */
    void finishHandoff(CompoundTag committed) {
        if(!handoffBatch || transactionDepth!=0 || !checkpoints.isEmpty())throw new IllegalStateException("Unfinished handoff checkpoint");
        handoffBatch=false;write(committed);
    }
    public void write(CompoundTag updated) {
        if(!id.equals(updated.getUUID("id"))) throw new IllegalArgumentException("Factory identity changed");
        if(transactionDepth>0 || handoffBatch){record=updated.copy();return;}
        try {
            var bytes=new ByteArrayOutputStream();
            NbtIo.write(updated,new DataOutputStream(bytes));
            generation=store.write(id,generation,bytes.toByteArray());record=updated.copy();
        } catch(IOException e) { throw new IllegalStateException("FACTORY_CHECKPOINT_FAILED: "+id,e); }
    }
    /** Only factory-owned assets may participate; external ME/world writes are not transactional. */
    public void atomic(Runnable change) {
        var before=record.copy();var pendingBefore=new IdentityHashMap<>(checkpoints);
        int previousDepth=transactionDepth;transactionDepth++;
        try {
            change.run();
            // Publish the final owned state before returning, not at the end of an unrelated server tick.
            if(transactionDepth==1 && !handoffBatch)flushCheckpoints();
            transactionDepth--;
            if(transactionDepth==0 && !record.equals(before))write(record);
        } catch(RuntimeException|Error failure) {
            transactionDepth=previousDepth;checkpoints.clear();checkpoints.putAll(pendingBefore);
            record=before;throw failure;
        }
    }
    public void state(String next) { var data=read();data.putString("state",next);write(data); }
}
