package com.syaru.ae2machines.platform;

import java.util.List;
import net.minecraft.Util;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.*;

public final class NativeCellPlatform {
    private NativeCellPlatform() {}
    public static net.minecraft.nbt.CompoundTag readDurableChunk(ServerLevel level,ChunkPos pos) {
        var storage=((com.syaru.ae2machines.mixin.NativeWorldServerAccess)level.getServer()).ae2machines$storage();
        var folder=storage.getDimensionPath(level.dimension()).resolve("region");
        var path=folder.resolve("r."+pos.getRegionX()+"."+pos.getRegionZ()+".mca");
        if(!java.nio.file.Files.isRegularFile(path))throw new IllegalStateException("Native checkpoint region missing");
        var info=new net.minecraft.world.level.chunk.storage.RegionStorageInfo("ae2machines_checkpoint",level.dimension(),"chunk");
        try(var region=new net.minecraft.world.level.chunk.storage.RegionFile(info,path,folder,false);
                var input=region.getChunkDataInputStream(pos)) {
            if(input==null)throw new IllegalStateException("Native checkpoint chunk missing");
            return PlatformData.readNbt(input);
        }catch(java.io.IOException error){throw new java.io.UncheckedIOException(error);}
    }
    public static ServerLevel create(ServerLevel source,ResourceKey<Level> key,ServerLevel template,LevelStorageSource.LevelStorageAccess storage) {
        return new ServerLevel(source.getServer(),Util.backgroundExecutor(),storage,
                new DerivedLevelData(source.getServer().getWorldData(),(ServerLevelData)source.getLevelData()),key,
                new LevelStem(source.dimensionTypeRegistration(),template.getChunkSource().getGenerator()),
                new ChunkProgressListener(){public void updateSpawnPos(ChunkPos p){} public void onStatusChange(ChunkPos p,ChunkStatus s){} public void start(){} public void stop(){}},
                false,source.getSeed(),List.of(),false,null);
    }
    public static void loaded(MinecraftServer server,ServerLevel level) {
        server.markWorldsDirty();
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.LevelEvent.Load(level));
    }
    public static void unloaded(MinecraftServer server,ServerLevel level){net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.LevelEvent.Unload(level));}
    public static void beforeTick(ServerLevel level,java.util.function.BooleanSupplier time){net.neoforged.neoforge.event.EventHooks.fireLevelTickPre(level,time);}
    public static boolean runsNormally(ServerLevel level){return level.tickRateManager().runsNormally();}
    public static void afterTick(ServerLevel level,java.util.function.BooleanSupplier time){net.neoforged.neoforge.event.EventHooks.fireLevelTickPost(level,time);}
    public static net.minecraft.nbt.CompoundTag beginMove(appeng.api.movable.IBlockEntityMoveStrategy strategy,net.minecraft.world.level.block.entity.BlockEntity be){return strategy.beginMove(be,be.getLevel().registryAccess());}
    public static com.syaru.ae2machines.runtime.NativeWorldPower.Fe fePort(ServerLevel level,net.minecraft.world.level.block.entity.BlockEntity be,boolean extraction) {
        var faces=new java.util.ArrayList<com.syaru.ae2machines.runtime.NativeWorldPower.Fe>();
        for(var side:net.minecraft.core.Direction.values()) {
            var cap=level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,be.getBlockPos(),side);
            if(cap!=null && (extraction?cap.canExtract():cap.canReceive()))faces.add(new com.syaru.ae2machines.runtime.NativeWorldPower.Fe(cap,
                    (amount,simulate)->extraction?cap.extractEnergy(Math.toIntExact(amount),simulate):cap.receiveEnergy(Math.toIntExact(amount),simulate),
                    cap::getEnergyStored));
        }
        return com.syaru.ae2machines.runtime.NativeWorldPower.Fe.faces(be,faces);
    }
    public static com.syaru.ae2machines.runtime.NativeWorldPower.Fe fePort(ServerLevel level,net.minecraft.world.level.block.entity.BlockEntity be,boolean extraction,net.minecraft.core.Direction side){
        var cap=level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,be.getBlockPos(),side);
        if(cap==null || !(extraction?cap.canExtract():cap.canReceive()))return null;
        return new com.syaru.ae2machines.runtime.NativeWorldPower.Fe(cap,(n,simulate)->extraction?cap.extractEnergy(Math.toIntExact(n),simulate):cap.receiveEnergy(Math.toIntExact(n),simulate),cap::getEnergyStored);
    }
}
