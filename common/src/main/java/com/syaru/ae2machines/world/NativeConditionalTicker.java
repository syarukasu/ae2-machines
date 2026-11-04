package com.syaru.ae2machines.world;

import com.syaru.ae2machines.compat.mek.MekThermalShells;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.TickingBlockEntity;

/** Only an unchanged native state is omitted; transitions execute at the original ticker position. */
final class NativeConditionalTicker implements TickingBlockEntity {
    private final TickingBlockEntity source;
    private final MekThermalShells.Shell shell;
    private NativeQuietStep clock;
    private long basedAt;
    private long skipped;
    NativeConditionalTicker(TickingBlockEntity source,MekThermalShells.Shell shell){this.source=source;this.shell=shell;clock=shell.clock();basedAt=shell.owner().getLevel().getGameTime();}
    MekThermalShells.Shell shell(){return shell;}
    boolean quiet(){return clock!=null && clock.limit()>0 && !isRemoved() && shell.quiet().getAsBoolean();}
    long deadline(){return Math.addExact(basedAt,clock.limit());}
    void flush(long through){
        int count=Math.toIntExact(Math.max(0,through-basedAt));
        if(count>0 && clock!=null){clock.advance().accept(count);skipped+=count;basedAt=through;}
    }
    long skipped(){return skipped;}
    void removed(){flush(shell.owner().getLevel().getGameTime()-1);clock=null;}
    @Override public void tick(){
        long now=shell.owner().getLevel().getGameTime();flush(now-1);source.tick();basedAt=now;
        clock=isRemoved()?null:shell.reset().get();
    }
    @Override public boolean isRemoved(){return source.isRemoved();}
    @Override public BlockPos getPos(){return source.getPos();}
    @Override public String getType(){return source.getType();}
}
