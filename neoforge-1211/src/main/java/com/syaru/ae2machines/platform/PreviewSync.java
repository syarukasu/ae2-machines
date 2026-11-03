package com.syaru.ae2machines.platform;

import appeng.menu.guisync.PacketWritable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;

public record PreviewSync(CompoundTag payload) implements PacketWritable {
    public PreviewSync { payload=payload.copy(); }
    public PreviewSync(RegistryFriendlyByteBuf buffer){this(java.util.Objects.requireNonNull(buffer.readNbt()));}
    @Override public void writeToPacket(RegistryFriendlyByteBuf buffer){buffer.writeNbt(payload);}
}
