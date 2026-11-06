package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.world.*;
import java.util.*;
import net.minecraft.server.level.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(ServerChunkCache.class)
public abstract class NativeIntervalChunkOrderMixin {
    @Redirect(method="tickChunks",at=@At(value="INVOKE",target="Ljava/util/Collections;shuffle(Ljava/util/List;)V",remap=false))
    private void machines$nativeOrder(List<?> chunks){
        var level=(ServerLevel)((ServerChunkCache)(Object)this).getLevel();
        if(NativeCellWorlds.owns(level))NativeWorldRuntime.get(level).intervalChunkOrder(chunks);
        Collections.shuffle(chunks);
    }
}
