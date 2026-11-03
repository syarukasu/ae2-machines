package com.syaru.ae2machines.world;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;
import net.minecraft.nbt.*;

/** Content witness independent of compound-key insertion order; lists retain their order. */
public final class NbtWitness {
    private NbtWitness() {}
    public static String digest(Tag tag) {
        try {
            var digest=MessageDigest.getInstance("SHA-256");
            try(var out=new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(),digest))){write(tag,out);}
            return HexFormat.of().formatHex(digest.digest());
        }catch(IOException|NoSuchAlgorithmException error){throw new IllegalStateException(error);}
    }
    private static void write(Tag tag,DataOutputStream out) throws IOException {
        out.writeByte(tag.getId());
        if(tag instanceof CompoundTag compound) {
            var keys=compound.getAllKeys().stream().sorted().toList();out.writeInt(keys.size());
            for(var key:keys){string(key,out);write(compound.get(key),out);}
        }else if(tag instanceof ListTag list) {
            out.writeByte(list.getElementType());out.writeInt(list.size());
            for(var value:list)write(value,out);
        }else string(tag.toString(),out);
    }
    private static void string(String value,DataOutputStream out) throws IOException {
        var bytes=value.getBytes(StandardCharsets.UTF_8);out.writeInt(bytes.length);out.write(bytes);
    }
}
