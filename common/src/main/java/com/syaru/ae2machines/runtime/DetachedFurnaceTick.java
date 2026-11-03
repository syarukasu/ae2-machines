package com.syaru.ae2machines.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Only scopes vanilla furnace state writes; it is not a general virtual world. */
public final class DetachedFurnaceTick implements AutoCloseable {
    private static final ThreadLocal<AbstractFurnaceBlockEntity> CURRENT=new ThreadLocal<>();
    private DetachedFurnaceTick(AbstractFurnaceBlockEntity furnace) {
        if(CURRENT.get()!=null)throw new IllegalStateException("Nested detached furnace tick");
        CURRENT.set(furnace);
    }
    public static void tick(AbstractFurnaceBlockEntity furnace) {
        try(var scope=new DetachedFurnaceTick(furnace)) {
            AbstractFurnaceBlockEntity.serverTick(furnace.getLevel(),furnace.getBlockPos(),furnace.getBlockState(),furnace);
        }
    }
    public static void mutate(AbstractFurnaceBlockEntity furnace,Runnable action) {
        try(var scope=new DetachedFurnaceTick(furnace)){action.run();}
    }
    public static boolean owns(Level level,BlockPos pos) {
        var current=CURRENT.get();return current!=null && current.getLevel()==level && current.getBlockPos().equals(pos);
    }
    public static boolean setState(Level level,BlockPos pos,BlockState state) {
        if(!owns(level,pos))return false;
        var current=CURRENT.get();
        if(state.getBlock()!=current.getBlockState().getBlock())throw new IllegalStateException("Unexpected furnace replacement");
        current.setBlockState(state);return true;
    }
    @Override public void close(){CURRENT.remove();}
}
