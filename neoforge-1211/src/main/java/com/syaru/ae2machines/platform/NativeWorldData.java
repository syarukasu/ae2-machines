package com.syaru.ae2machines.platform;

import com.syaru.ae2machines.world.NativeWorldState;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class NativeWorldData extends SavedData {
    private final NativeWorldState state = new NativeWorldState(this::setDirty);
    public static NativeWorldState get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(NativeWorldData::new,
                NativeWorldData::load, null), "ae2machines_native_world").state;
    }
    private static NativeWorldData load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new NativeWorldData(); data.state.load(tag); return data;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) { return state.save(tag); }
}
