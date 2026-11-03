package com.syaru.ae2machines.platform;

import appeng.menu.guisync.PacketWritable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

public record PreviewSync(CompoundTag payload) implements PacketWritable {
    public PreviewSync { payload=payload.copy(); }
    public PreviewSync(FriendlyByteBuf buffer){this(java.util.Objects.requireNonNull(buffer.readNbt()));}
    @Override public void writeToPacket(FriendlyByteBuf buffer){buffer.writeNbt(payload);}
}
