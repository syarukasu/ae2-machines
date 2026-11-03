package com.syaru.ae2machines.engine;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Normal AE2 handoffs may resume only within the same process or after a coordinated world save. */
public final class RecoveryEpoch {
    private static final UUID RECORD=new UUID(0,1);
    private final AtomicSnapshotStore store;
    private final Set<UUID> saved=new HashSet<>();
    private final UUID current=UUID.randomUUID();
    private long generation;
    private boolean sealed;

    public RecoveryEpoch(Path directory) throws IOException {
        store=new AtomicSnapshotStore(directory);
        if(Files.exists(directory.resolve(RECORD+".factory"))) {
            var snapshot=store.read(RECORD);generation=snapshot.generation();
            try(var in=new DataInputStream(new ByteArrayInputStream(snapshot.payload()))) {
                if(in.readInt()!=1)throw new IOException("Unknown recovery epoch schema");
                var previous=new UUID(in.readLong(),in.readLong());boolean clean=in.readBoolean();
                int count=in.readInt();
                if(count<0 || count>snapshot.payload().length/16)throw new IOException("Invalid recovery epochs");
                for(int i=0;i<count;i++)if(!saved.add(new UUID(in.readLong(),in.readLong())))throw new IOException("Duplicate epoch");
                if(in.available()!=0)throw new IOException("Trailing epoch data");
                if(clean)saved.add(previous);
            }
        }
        write(false);
    }
    public UUID current() { return current; }
    public boolean permits(UUID epoch) { return current.equals(epoch) || saved.contains(epoch); }
    public void requireActive() { if(sealed)throw new IllegalStateException("Server is stopping"); }
    public void seal() { requireActive();sealed=true; }
    public void savedShutdown() throws IOException {
        if(!sealed)throw new IllegalStateException("World must be quiescent before saving");
        write(true);
    }
    private void write(boolean clean) throws IOException {
        var bytes=new ByteArrayOutputStream();
        try(var out=new DataOutputStream(bytes)) {
            out.writeInt(1);out.writeLong(current.getMostSignificantBits());out.writeLong(current.getLeastSignificantBits());
            out.writeBoolean(clean);out.writeInt(saved.size());
            for(var epoch:saved) { out.writeLong(epoch.getMostSignificantBits());out.writeLong(epoch.getLeastSignificantBits()); }
        }
        generation=store.write(RECORD,generation,bytes.toByteArray());
    }
}
