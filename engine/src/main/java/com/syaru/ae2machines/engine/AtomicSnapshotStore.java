package com.syaru.ae2machines.engine;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.UUID;

/** Single authoritative generation. Never roll back to an older asset ledger on corruption. */
public final class AtomicSnapshotStore {
    private static final int MAGIC=0x41454D31;
    private final Path directory;
    public record Snapshot(long generation,byte[] payload) {
        public Snapshot { payload=payload.clone(); }
        @Override public byte[] payload() { return payload.clone(); }
    }
    public AtomicSnapshotStore(Path directory) { this.directory=directory.toAbsolutePath().normalize(); }
    public synchronized Snapshot read(UUID id) throws IOException {
        byte[] file=Files.readAllBytes(path(id));
        if(file.length<48) throw new IOException("Truncated factory journal");
        var b=ByteBuffer.wrap(file);
        if(b.getInt()!=MAGIC) throw new IOException("Factory journal format");
        long generation=b.getLong();int size=b.getInt();
        if(generation<1 || size!=file.length-48) throw new IOException("Factory journal length/generation");
        byte[] checksum=new byte[32];b.get(checksum);byte[] payload=new byte[size];b.get(payload);
        if(!MessageDigest.isEqual(checksum,hash(generation,payload)))
            throw new IOException("Factory journal checksum");
        return new Snapshot(generation,payload);
    }
    public synchronized long write(UUID id,long expectedGeneration,byte[] payload) throws IOException {
        Files.createDirectories(directory);
        long previous=Files.exists(path(id)) ? read(id).generation() : 0;
        if(previous!=expectedGeneration) throw new IOException("Stale factory journal owner");
        long generation=Math.addExact(previous,1);
        var b=ByteBuffer.allocate(Math.addExact(payload.length,48));
        b.putInt(MAGIC).putLong(generation).putInt(payload.length).put(hash(generation,payload)).put(payload).flip();
        Path pending=directory.resolve(id+".pending");
        try(var channel=FileChannel.open(pending,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE)) {
            while(b.hasRemaining()) channel.write(b);
            channel.force(true);
        }
        // Unsupported atomic replacement is an error, not permission to expose a half-written snapshot.
        Files.move(pending,path(id),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        try(var channel=FileChannel.open(path(id),StandardOpenOption.WRITE)) { channel.force(true); }
        return generation;
    }
    private Path path(UUID id) { return directory.resolve(id+".factory"); }
    private static byte[] hash(long generation,byte[] data) {
        try {
            var digest=MessageDigest.getInstance("SHA-256");
            digest.update(ByteBuffer.allocate(12).putLong(generation).putInt(data.length).array());
            return digest.digest(data);
        }
        catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
