package com.syaru.ae2machines.engine;

import java.io.*;
import java.nio.file.*;
import java.util.UUID;

/** A witness expires independently of the factory journal and cannot invalidate a newer witness. */
public final class CheckpointFence {
    private final Path directory;
    private final AtomicSnapshotStore store;
    private record State(long generation,UUID token,boolean valid){}
    public CheckpointFence(Path directory){this.directory=directory;store=new AtomicSnapshotStore(directory);}
    private State read(UUID owner) throws IOException {
        if(!Files.exists(directory.resolve(owner+".factory")))return new State(0,new UUID(0,0),false);
        var saved=store.read(owner);
        try(var in=new DataInputStream(new ByteArrayInputStream(saved.payload()))) {
            if(in.readInt()!=1)throw new IOException("Unknown native checkpoint fence");
            var result=new State(saved.generation(),new UUID(in.readLong(),in.readLong()),in.readBoolean());
            if(in.available()!=0)throw new IOException("Trailing native checkpoint fence data");return result;
        }
    }
    private void write(UUID owner,State state,UUID token,boolean valid) throws IOException {
        var bytes=new ByteArrayOutputStream();
        try(var out=new DataOutputStream(bytes)){out.writeInt(1);out.writeLong(token.getMostSignificantBits());out.writeLong(token.getLeastSignificantBits());out.writeBoolean(valid);}
        store.write(owner,state.generation(),bytes.toByteArray());
    }
    public UUID arm(UUID owner) throws IOException {
        var state=read(owner);var token=UUID.randomUUID();write(owner,state,token,true);return token;
    }
    public boolean permits(UUID owner,UUID token) throws IOException {
        var state=read(owner);return state.valid() && state.token().equals(token);
    }
    public void revoke(UUID owner,UUID token) throws IOException {
        var state=read(owner);if(state.valid() && state.token().equals(token))write(owner,state,token,false);
    }
}
