package com.syaru.ae2machines.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PersistentEntitySectionManager.class)
public interface NativeEntityStorageAccess {
    @Accessor("permanentStorage") EntityPersistentStorage<Entity> ae2machines$storage();
    @Accessor("chunkVisibility") Long2ObjectMap<Visibility> ae2machines$visibility();
}
