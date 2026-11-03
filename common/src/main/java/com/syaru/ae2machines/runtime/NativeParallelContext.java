package com.syaru.ae2machines.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Local native ticker scope. It never advances the world's clock for a parallel lane. */
public final class NativeParallelContext implements AutoCloseable {
    private static final ThreadLocal<NativeParallelContext> CURRENT=new ThreadLocal<>();
    private final BlockEntity lane;
    private final BlockState original;
    private NativeParallelContext(BlockEntity lane){
        if(CURRENT.get()!=null)throw new IllegalStateException("NESTED_NATIVE_PARALLEL_TICK");
        this.lane=lane;original=lane.getBlockState();CURRENT.set(this);
    }
    public static NativeParallelContext enter(BlockEntity lane){return new NativeParallelContext(lane);}
    public static NativeParallelContext current(Level level){var c=CURRENT.get();return c!=null && c.lane.getLevel()==level?c:null;}
    public static boolean active(){return CURRENT.get()!=null;}
    public static <T>T call(BlockEntity lane,java.util.function.Supplier<T> action){
        var current=CURRENT.get();if(current!=null && current.lane==lane)return action.get();
        try(var ignored=enter(lane)){return action.get();}
    }
    public BlockEntity block(BlockPos pos){own(pos);return lane;}
    public BlockState state(BlockPos pos){return lane.getBlockPos().equals(pos)?lane.getBlockState():null;}
    public boolean set(BlockPos pos,BlockState state){
        own(pos);if(state.getBlock()!=original.getBlock())throw new IllegalStateException("NATIVE_PARALLEL_REPLACES_MACHINE");
        lane.setBlockState(state);return true;
    }
    public void own(BlockPos pos){if(!lane.getBlockPos().equals(pos))throw new IllegalStateException("NATIVE_PARALLEL_FOREIGN_WORLD_WRITE_OR_BE_ACCESS");}
    public static void rejectWorldEffect(){if(active())throw new IllegalStateException("NATIVE_PARALLEL_NONLOCAL_EFFECT");}
    @Override public void close(){if(CURRENT.get()!=this)throw new IllegalStateException("Native lane scope changed");CURRENT.remove();}
}
