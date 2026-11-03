package com.syaru.ae2machines.engine;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Write-ahead decision: PREPARED undoes, DECIDED redoes, CLEAN must never replay. */
public final class HandoffLog {
    public enum Phase { CLEAN, PREPARED, DECIDED }
    public record Entry(UUID owner,long generation,Phase phase,byte[] image) {
        public Entry { image=image.clone(); }
        @Override public byte[] image(){return image.clone();}
    }
    private final Path directory;
    private final AtomicSnapshotStore store;
    public HandoffLog(Path directory){this.directory=directory;store=new AtomicSnapshotStore(directory);}
    public Entry read(UUID id) throws IOException {
        var snapshot=store.read(id);
        try(var in=new DataInputStream(new ByteArrayInputStream(snapshot.payload()))) {
            if(in.readInt()!=1)throw new IOException("Unknown handoff schema");
            int phase=in.readUnsignedByte();if(phase>=Phase.values().length)throw new IOException("Unknown handoff phase");
            int size=in.readInt();if(size<0 || size!=in.available())throw new IOException("Handoff image length");
            var image=in.readNBytes(size);
            if((phase==0)!=(size==0))throw new IOException("Handoff image missing/unexpected");
            return new Entry(id,snapshot.generation(),Phase.values()[phase],image);
        }
    }
    public Entry prepare(UUID owner,byte[] before) throws IOException {
        long generation=0;
        if(Files.exists(directory.resolve(owner+".factory"))) {
            var old=read(owner);if(old.phase()!=Phase.CLEAN)throw new IOException("Unresolved external handoff");
            generation=old.generation();
        }
        return write(owner,generation,Phase.PREPARED,before);
    }
    public Entry decide(Entry prepared,byte[] after) throws IOException {
        if(prepared.phase()!=Phase.PREPARED)throw new IllegalArgumentException("Not prepared");
        return write(prepared.owner(),prepared.generation(),Phase.DECIDED,after);
    }
    public void clean(Entry entry) throws IOException { write(entry.owner(),entry.generation(),Phase.CLEAN,new byte[0]); }
    private Entry write(UUID owner,long generation,Phase phase,byte[] image) throws IOException {
        if((phase==Phase.CLEAN)!=(image.length==0))throw new IllegalArgumentException("Invalid handoff image");
        var bytes=new ByteArrayOutputStream();
        try(var out=new DataOutputStream(bytes)){out.writeInt(1);out.writeByte(phase.ordinal());out.writeInt(image.length);out.write(image);}
        return new Entry(owner,store.write(owner,generation,bytes.toByteArray()),phase,image);
    }
    public List<Entry> pending() throws IOException {
        if(!Files.exists(directory))return List.of();
        var result=new ArrayList<Entry>();
        try(var files=Files.newDirectoryStream(directory,"*.factory")) {
            for(var path:files) {
                var name=path.getFileName().toString();
                UUID id;
                try{id=UUID.fromString(name.substring(0,name.length()-8));}
                catch(IllegalArgumentException e){throw new IOException("Invalid handoff file",e);}
                var entry=read(id);if(entry.phase()!=Phase.CLEAN)result.add(entry);
            }
        }
        return List.copyOf(result);
    }
}
