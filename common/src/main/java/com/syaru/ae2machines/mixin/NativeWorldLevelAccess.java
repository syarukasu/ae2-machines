package com.syaru.ae2machines.mixin;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.WritableLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Level.class)
public interface NativeWorldLevelAccess {
    @Mutable @Accessor("levelData")
    void ae2machines$levelData(WritableLevelData data);
}
