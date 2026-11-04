package com.syaru.ae2machines.mixin;

import java.util.function.IntConsumer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets="mekanism.api.recipes.cache.CachedRecipe",remap=false)
public interface MekTimeCacheAccess {
    @Accessor("operatingTicksChanged") IntConsumer machines$timeProgressChanged();
}
