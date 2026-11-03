package com.syaru.ae2machines.mixin;

import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MinecraftServer.class)
public interface NativeWorldServerAccess {
    @Accessor("storageSource") LevelStorageSource.LevelStorageAccess ae2machines$storage();
    @Accessor("levels") Map<ResourceKey<Level>,ServerLevel> ae2machines$levels();
    @Mutable @Accessor("levels") void ae2machines$levels(Map<ResourceKey<Level>,ServerLevel> levels);
}
