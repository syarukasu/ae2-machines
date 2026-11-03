package com.syaru.ae2machines.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.storage.ServerLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerLevel.class)
public interface NativeServerWorldAccess {
    @Accessor("serverLevelData") ServerLevelData ae2machines$serverData();
    @Mutable @Accessor("serverLevelData") void ae2machines$serverData(ServerLevelData data);
    @Accessor("entityManager") PersistentEntitySectionManager<Entity> ae2machines$entities();
    @Accessor("blockEvents") it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet<net.minecraft.world.level.BlockEventData> ae2machines$blockEvents();
}
