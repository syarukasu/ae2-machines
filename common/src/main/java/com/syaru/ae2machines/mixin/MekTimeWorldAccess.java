package com.syaru.ae2machines.mixin;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;
@Pseudo
@Mixin(targets="mekanism.common.CommonWorldTickHandler",remap=false)
public interface MekTimeWorldAccess {
    @Accessor("chunkRegenMap") Map<ResourceLocation,Queue<ChunkPos>> machines$timeRegeneration();
}
