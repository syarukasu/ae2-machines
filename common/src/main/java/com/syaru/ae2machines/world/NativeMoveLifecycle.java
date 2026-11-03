package com.syaru.ae2machines.world;

import net.minecraft.world.level.block.entity.BlockEntity;

/** Relocation unloads Create controllers; it must not invoke their destructive removal path. */
public final class NativeMoveLifecycle {
    private static final ThreadLocal<Relocation> RELOCATING=new ThreadLocal<>();
    static Relocation begin(net.minecraft.server.level.ServerLevel source,net.minecraft.server.level.ServerLevel target,net.minecraft.core.BlockPos min,net.minecraft.core.BlockPos max){
        var scope=new Relocation(source,target,min.immutable(),max.immutable(),RELOCATING.get());RELOCATING.set(scope);return scope;
    }
    public static boolean relocating(BlockEntity be){
        for(var scope=RELOCATING.get();scope!=null;scope=scope.previous)
            if((be.getLevel()==scope.source || be.getLevel()==scope.target) && NativeRegionMove.contains(be.getBlockPos(),scope.min,scope.max))return true;
        return false;
    }
    static void refresh(net.minecraft.server.level.ServerLevel level,net.minecraft.core.BlockPos min,net.minecraft.core.BlockPos max){
        for(var be:NativeRegionMove.blockEntities(level,min,max))if(be instanceof appeng.blockentity.crafting.MolecularAssemblerBlockEntity assembler)
            assembler.onMainNodeStateChanged(appeng.api.networking.IGridNodeListener.State.POWER);
    }
    static final class Relocation implements AutoCloseable {
        final net.minecraft.server.level.ServerLevel source,target;final net.minecraft.core.BlockPos min,max;final Relocation previous;
        Relocation(net.minecraft.server.level.ServerLevel source,net.minecraft.server.level.ServerLevel target,net.minecraft.core.BlockPos min,net.minecraft.core.BlockPos max,Relocation previous){this.source=source;this.target=target;this.min=min;this.max=max;this.previous=previous;}
        @Override public void close(){if(RELOCATING.get()!=this)throw new IllegalStateException("Native relocation scope changed");if(previous==null)RELOCATING.remove();else RELOCATING.set(previous);}
    }
    private static final ClassValue<Boolean> CREATE_BLOCK = new ClassValue<>() {
        @Override protected Boolean computeValue(Class<?> type) {
            for(var parent=type;parent!=null;parent=parent.getSuperclass())
                if(parent.getName().equals("com.simibubi.create.foundation.blockEntity.SmartBlockEntity"))return true;
            return false;
        }
    };
    private NativeMoveLifecycle() {}
    static void beforeRemoval(BlockEntity be) {
        if(CREATE_BLOCK.get(be.getClass()) || com.syaru.ae2machines.runtime.NativeMachines.mek()
                && be instanceof mekanism.common.tile.transmitter.TileEntityTransmitter)be.onChunkUnloaded();
    }
}
