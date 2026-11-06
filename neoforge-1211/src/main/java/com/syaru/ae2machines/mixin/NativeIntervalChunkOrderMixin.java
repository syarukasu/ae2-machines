package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.world.*;
import java.util.*;
import net.minecraft.Util;
import net.minecraft.server.level.*;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(ServerChunkCache.class)
public abstract class NativeIntervalChunkOrderMixin {
    @Redirect(method="tickChunks",at=@At(value="INVOKE",target="Lnet/minecraft/Util;shuffle(Ljava/util/List;Lnet/minecraft/util/RandomSource;)V"))
    private void machines$nativeOrder(List<?> chunks,RandomSource random){
        var level=(ServerLevel)((ServerChunkCache)(Object)this).getLevel();
        if(NativeCellWorlds.owns(level))NativeWorldRuntime.get(level).intervalChunkOrder(chunks);
        Util.shuffle(chunks,random);
    }
}
