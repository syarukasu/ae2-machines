package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.world.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;

/** Keep the entire native ticker wrapper; reuse only its unchanged cell block-state read. */
@Mixin(targets="net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
public abstract class NativeTickerStateMixin {
    @Unique private boolean machines$resolved;
    @Unique private NativeWorldState machines$clock;
    @Unique private NativePaletteState machines$palette;
    @Unique private long machines$revision=Long.MIN_VALUE,machines$hostTick=Long.MIN_VALUE;
    @Unique private BlockState machines$state;
    @Redirect(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/chunk/LevelChunk;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState machines$nativeState(LevelChunk chunk,BlockPos pos){
        if(!machines$resolved){
            if(chunk.getLevel() instanceof ServerLevel level && NativeCellWorlds.owns(level))machines$clock=NativeWorldRuntime.get(level).state();
            machines$resolved=true;
        }
        if(machines$clock==null)return chunk.getBlockState(pos);
        var sections=chunk.getSections();int section=chunk.getSectionIndex(pos.getY());
        if(section<0 || section>=sections.length)return chunk.getBlockState(pos);
        var palette=(NativePaletteState)sections[section].getStates();
        long revision=palette.machines$stateRevision(),hostTick=machines$clock.ticks();
        if(machines$state==null || machines$palette!=palette || machines$revision!=revision || machines$hostTick!=hostTick){
            machines$state=chunk.getBlockState(pos);machines$revision=revision;machines$hostTick=hostTick;
            machines$palette=palette;
        }
        return machines$state;
    }
}
