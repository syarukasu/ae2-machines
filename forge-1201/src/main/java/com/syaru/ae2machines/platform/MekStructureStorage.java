package com.syaru.ae2machines.platform;

import java.util.*;
import mekanism.common.lib.multiblock.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import static com.syaru.ae2machines.world.NativeFactoryCompiler.require;

public final class MekStructureStorage {
    public static Object powerIdentity(BlockEntity be) {
        if(be instanceof mekanism.common.tile.transmitter.TileEntityTransmitter transmitter)
            return transmitter.getTransmitter().getTransmitterNetwork();
        return be instanceof IMultiblock<?> tile && tile.getMultiblock().isFormed()?tile.getMultiblock():null;
    }
    public static boolean settlePendingPower(List<BlockEntity> blocks) {
        var seen=Collections.newSetFromMap(new IdentityHashMap<mekanism.common.content.matrix.MatrixEnergyContainer,Boolean>());
        for(var be:blocks)if(be instanceof IMultiblock<?> tile && tile.getMultiblock().isFormed())
            for(var energy:tile.getMultiblock().getEnergyContainers(null))
                if(energy instanceof mekanism.common.content.matrix.MatrixEnergyContainer matrix && seen.add(matrix)) {
                    var before=matrix.getEnergy();
                    matrix.tick();
                    require(Objects.equals(before,matrix.getEnergy()),"MEK_MATRIX_SETTLEMENT_MISMATCH");
                }
        return !seen.isEmpty();
    }
    public static boolean structure(BlockEntity be){return be instanceof IMultiblock<?>;}
    @SuppressWarnings({"rawtypes","unchecked"})
    public static ListTag snapshot(ServerLevel level,List<BlockEntity> blocks,BlockPos min,BlockPos max) {
        return snapshot(level,blocks,min,max,true);
    }
    public static void checkpoint(ServerLevel level,List<BlockEntity> blocks,BlockPos min,BlockPos max){snapshot(level,blocks,min,max,false);}
    @SuppressWarnings({"rawtypes","unchecked"})
    public static void restore(ServerLevel level,ListTag saved,List<BlockEntity> previous) {
        var managers=com.syaru.ae2machines.mixin.MekManagersAccess.ae2machines$managers();
        var cleared=new HashSet<MultiblockManager>();
        for(var value:saved) {
            var tag=(CompoundTag)value;
            MultiblockManager manager=(MultiblockManager)managers.stream().filter(m->((MultiblockManager)m).getName().equals(tag.getString("manager"))).findFirst().orElseThrow(()->new IllegalStateException("MEK_RECOVERY_MANAGER_MISSING"));
            var stale=new HashSet<UUID>();
            if(cleared.add(manager))for(var be:previous)if(be instanceof IMultiblock<?> tile && tile.getManager()==manager && tile.getMultiblock().inventoryID!=null)stale.add(tile.getMultiblock().inventoryID);
            MultiblockCache cache=manager.createCache();cache.load(tag.getCompound("cache"));
            manager.replaceCaches(stale,tag.getUUID("id"),cache);manager.trackCache(tag.getUUID("id"),cache);
        }
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static ListTag snapshot(ServerLevel level,List<BlockEntity> blocks,BlockPos min,BlockPos max,boolean validate) {
        var saved=new ListTag();var seen=new HashSet<UUID>();
        for(var be:blocks)if(be instanceof IMultiblock<?> tile) {
            var data=tile.getMultiblock();if(!data.isFormed()){require(!validate,"MEK_STRUCTURE_NOT_FORMED");continue;}
            require(inside(data.getMinPos(),min,max) && inside(data.getMaxPos(),min,max),"MEK_STRUCTURE_OUTSIDE_CAPTURE");
            if(!seen.add(data.inventoryID))continue;
            MultiblockManager manager=tile.getManager();MultiblockCache cache=manager.getCache(data.inventoryID);
            require(cache!=null,"MEK_STRUCTURE_CACHE_MISSING");com.syaru.ae2machines.compat.mek.MekStructureParallel.enrollExtraFluids(data);cache.sync(data);
            ((com.syaru.ae2machines.world.MekHeatState.Owner)cache).ae2machines$heatState().capture(data);
            manager.trackCache(data.inventoryID,cache);
            var tag=new CompoundTag();tag.putUUID("id",data.inventoryID);tag.putString("manager",manager.getName());
            var contents=new CompoundTag();cache.save(contents);tag.put("cache",contents);saved.add(tag);
        }
        return saved;
    }
    private static boolean inside(BlockPos p,BlockPos min,BlockPos max){return p!=null && p.getX()>=min.getX() && p.getX()<=max.getX() && p.getY()>=min.getY() && p.getY()<=max.getY() && p.getZ()>=min.getZ() && p.getZ()<=max.getZ();}
}
