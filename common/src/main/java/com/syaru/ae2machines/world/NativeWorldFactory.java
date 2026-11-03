package com.syaru.ae2machines.world;

import com.syaru.ae2machines.platform.*;
import com.syaru.ae2machines.runtime.NativeMachines;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import static com.syaru.ae2machines.world.NativeFactoryCompiler.require;

/** Default cell backend: native block, entity, scheduled, random and network processing. */
public final class NativeWorldFactory {
    private NativeWorldFactory() {}
    /** Retained for callers of the former backend selector; new captures always use a world. */
    public static boolean required(ServerLevel level,BlockPos a,BlockPos b) {
        long volume=Math.multiplyExact(Math.multiplyExact(Math.abs((long)a.getX()-b.getX())+1,Math.abs((long)a.getY()-b.getY())+1),Math.abs((long)a.getZ()-b.getZ())+1);
        require(volume<=262144,"SCAN_LIMIT");
        return true;
    }
    public static CompoundTag inspect(ServerLevel level,Player player,BlockPos a,BlockPos b) {
        var min=new BlockPos(Math.min(a.getX(),b.getX()),Math.min(a.getY(),b.getY()),Math.min(a.getZ(),b.getZ()));
        var max=new BlockPos(Math.max(a.getX(),b.getX()),Math.max(a.getY(),b.getY()),Math.max(a.getZ(),b.getZ()));
        long volume=Math.multiplyExact(Math.multiplyExact((long)max.getX()-min.getX()+1,(long)max.getY()-min.getY()+1),(long)max.getZ()-min.getZ()+1);
        require(volume<=262144,"SCAN_LIMIT");
        require(min.getY()>level.getMinBuildHeight() && max.getY()<level.getMaxBuildHeight()-1,"NATIVE_CELL_VERTICAL_BOUNDARY");
        require((max.getX()>>4)-(min.getX()>>4)<=7 && (max.getZ()>>4)-(min.getZ()>>4)<=7,"NATIVE_CELL_CHUNK_RANGE");
        var positions=new HashSet<BlockPos>();
        for(var p:BlockPos.betweenClosed(min,max)) {
            require(level.hasChunkAt(p),"CHUNK_UNLOADED");require(level.mayInteract(player,p),"PERMISSION_DENIED");positions.add(p.immutable());
            var be=level.getBlockEntity(p);if(be!=null)require(appeng.util.Platform.hasPermissions(new appeng.api.util.DimensionalBlockPos(be),player),"AE2_PERMISSION_DENIED");
        }
        var image=NativeRegionMove.image(level,min,max);var data=new CompoundTag();
        data.putInt("schema",4);data.putString("kind","native-world");data.putUUID("player",player.getUUID());
        data.putString("dimension",level.dimension().location().toString());data.putLong("first",min.asLong());data.putLong("last",max.asLong());
        data.put("blocks",image.getList("blocks",Tag.TAG_COMPOUND));
        var blocks=NativeRegionMove.blockEntities(level,min,max);
        data.put("boundaries",NativeGridBoundary.inspect(level,blocks,positions));
        if(NativeCellEndpoints.present(blocks)){
            data.putInt("endpointVersion",1);var ports=new ListTag();
            for(var block:blocks)if(block instanceof com.syaru.ae2machines.block.BaseEnergyPort && block.getBlockState().is(MachinesMod.ENERGY.get()))ports.add(LongTag.valueOf(block.getBlockPos().asLong()));
            data.put("energyPorts",ports);
        }
        return data;
    }
    public static FactoryJournal capture(ServerLevel source,Player player,BlockPos first,BlockPos last,java.util.function.Consumer<UUID> prepared) {
        var blueprint=inspect(source,player,first,last);var journal=FactoryJournal.create(source,blueprint);
        try {
        prepared.accept(journal.id());if(player instanceof net.minecraft.server.level.ServerPlayer)source.getServer().getPlayerList().saveAll();
        var min=BlockPos.of(blueprint.getLong("first"));var max=BlockPos.of(blueprint.getLong("last"));
        var target=NativeCellWorlds.open(source,journal.id());var state=NativeWorldRuntime.get(target).state();
        state.initialRules(source.getGameRules());state.claim(journal.id(),min.getX()>>4,min.getZ()>>4,max.getX()>>4,max.getZ()>>4,source.getDayTime());state.setTime(source.getGameTime(),source.getDayTime());
        NativeWorldRuntime.get(target).retain();
        for(var pos:NativeRegionMove.chunks(min,max)) {
            var chunk=target.getChunk(pos.x,pos.z);
            chunk.fillBiomesFromNoise((x,y,z,sampler)->source.getNoiseBiome(x,y,z),target.getChunkSource().randomState().sampler());
        }
        border(target,min,max);
        NativeRegionMove.move(source,target,min,max,journal,false);return journal;
        }catch(RuntimeException failure) {
            var record=journal.read();record.putString("worldFailure",Objects.toString(failure.getMessage(),failure.getClass().getName()));journal.write(record);
            org.slf4j.LoggerFactory.getLogger(NativeWorldFactory.class).error("Native cell {} capture did not finish; retained state {}",journal.id(),record.getString("state"),failure);
            throw failure;
        }
    }
    public static void restore(ServerLevel target,Player player,FactoryJournal journal) {
        var record=journal.read();var blueprint=record.getCompound("blueprint");
        require(blueprint.getUUID("player").equals(player.getUUID()),"FACTORY_OWNER_MISMATCH");
        require(blueprint.getString("dimension").equals(target.dimension().location().toString()),"WRONG_DIMENSION");
        require(record.getString("state").equals("CAPTURED"),"FACTORY_MUST_BE_IDLE_AND_DETACHED");
        if(record.contains("worldBatches"))require(com.syaru.ae2machines.runtime.NativeWorldBatches.restorable(record.getCompound("worldBatches"),key->PlatformData.loadKey(key,target)),"RESOLVE_NATIVE_BATCH_RECEIPTS_AND_RETURN_ENERGY_BEFORE_RESTORE");
        if(record.contains("variableMekRequests"))for(var value:record.getCompound("variableMekRequests").getList("entries",net.minecraft.nbt.Tag.TAG_COMPOUND)){
            var entry=(net.minecraft.nbt.CompoundTag)value;
            require(entry.getString("state").equals("ACKNOWLEDGED") && new java.math.BigInteger(entry.getByteArray("credit")).signum()==0,"RESOLVE_VARIABLE_NATIVE_RECEIPTS_BEFORE_RESTORE");
        }
        if(record.hasUUID("worldEpoch"))require(FactoryRecovery.epoch(target.getServer()).permits(record.getUUID("worldEpoch")),"NATIVE_WORLD_REQUIRES_RECONCILIATION");
        var source=NativeCellWorlds.open(target,journal.id());var min=BlockPos.of(blueprint.getLong("first"));var max=BlockPos.of(blueprint.getLong("last"));
        if(record.contains("genericNativeParallel"))require(com.syaru.ae2machines.runtime.NativeParallelRuntime.restorable(source,record.getCompound("genericNativeParallel")),"DRAIN_GENERIC_NATIVE_LANES_BEFORE_RESTORE");
        if(NativeMachines.mek())for(var be:NativeRegionMove.blockEntities(source,min,max)) {
            if(com.syaru.ae2machines.compat.mek.MekWorldParallel.hasParallelWork(be))
                require(false,"FINISH_NATIVE_PARALLEL_GROUP_BEFORE_RESTORE");
        }
        for(var p:BlockPos.betweenClosed(min,max))require(target.hasChunkAt(p) && target.mayInteract(player,p),"RESTORE_CHUNK_OR_PERMISSION");
        NativeRegionMove.move(source,target,min,max,journal,true);
        if(NativeMachines.gt())for(var be:NativeRegionMove.blockEntities(target,min,max))
            if(GtMultiblockAdapter.supports(be))GtMultiblockAdapter.prepareWorld(be);
        NativeCellWorlds.retire(source);
    }
    private static void border(ServerLevel level,BlockPos min,BlockPos max) {
        for(var p:BlockPos.betweenClosed(min.offset(-1,-1,-1),max.offset(1,1,1)))
            if(!NativeRegionMove.contains(p,min,max))level.setBlock(p,Blocks.BARRIER.defaultBlockState(),2);
    }
}
