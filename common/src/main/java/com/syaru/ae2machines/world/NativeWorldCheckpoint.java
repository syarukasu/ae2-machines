package com.syaru.ae2machines.world;

import appeng.api.networking.IGrid;
import com.syaru.ae2machines.block.BaseAccelerator;
import com.syaru.ae2machines.runtime.NativeMachines;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Resume a coordinated native-world/ME save only when every durable participant still matches. */
public final class NativeWorldCheckpoint {
    private record Armed(UUID owner,UUID token){}
    private static final Map<net.minecraft.server.MinecraftServer,Map<UUID,Armed>> ARMED=new WeakHashMap<>();
    private static com.syaru.ae2machines.engine.CheckpointFence fence(net.minecraft.server.MinecraftServer server) {
        return new com.syaru.ae2machines.engine.CheckpointFence(server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/ae2machines/native-checkpoints"));
    }
    private NativeWorldCheckpoint() {}
    private static boolean emptyComputationNode(BlockEntity be){
        return be instanceof BaseAccelerator a && !a.hasFactory()
                && be.getBlockState().getBlock() instanceof com.syaru.ae2machines.block.ComputationHostBlock block
                && block.role()!=com.syaru.ae2machines.block.ComputationHostBlock.Role.BAY;
    }
    private static Set<IGrid> connectedGrids(BaseAccelerator host) {
        var grids=Collections.newSetFromMap(new IdentityHashMap<IGrid,Boolean>());
        if(host.getMainNode().getGrid()!=null)grids.add(host.getMainNode().getGrid());
        if(host.factoryHandle() instanceof com.syaru.ae2machines.runtime.NativeWorldSession session && session.mainGrid()!=null)
            grids.add(session.mainGrid());
        return grids;
    }
    private static final Set<String> EXTERNAL=Set.of(
            "appeng.blockentity.crafting.CraftingBlockEntity","appeng.blockentity.storage.DriveBlockEntity",
            "appeng.blockentity.networking.EnergyCellBlockEntity","appeng.blockentity.networking.CreativeEnergyCellBlockEntity",
            "appeng.blockentity.networking.ControllerBlockEntity");
    /** Output receipt transfers touch external ME state only, never a second native inventory. */
    public static Set<BlockEntity> outputParticipants(BaseAccelerator host,ServerLevel world) {
        var result=Collections.newSetFromMap(new IdentityHashMap<BlockEntity,Boolean>());result.add(host);
        var grids=connectedGrids(host);if(grids.isEmpty())return Set.of();
        for(var grid:grids)for(var node:grid.getNodes()) {
            var owner=node.getOwner();
            var be=owner instanceof appeng.parts.AEBasePart part?part.getBlockEntity():owner instanceof BlockEntity block?block:null;
            if(be==null)return Set.of();
            if(be.getLevel()==world) {
                if(be.getClass()==appeng.blockentity.crafting.PatternProviderBlockEntity.class)continue;
                if(be instanceof appeng.blockentity.networking.CableBusBlockEntity cable) {
                    for(var side:Direction.values())if(cable.getPart(side)!=null
                            && !(cable.getPart(side) instanceof appeng.parts.p2p.MEP2PTunnelPart)
                            && !(cable.getPart(side) instanceof appeng.parts.networking.QuartzFiberPart))return Set.of();
                    continue;
                }
                return Set.of();
            }
            if(be.getLevel()!=host.getLevel() || be.getLevel().getBlockEntity(be.getBlockPos())!=be)return Set.of();
            if(be==host || emptyComputationNode(be)){result.add(be);continue;}
            if(be instanceof appeng.blockentity.networking.CableBusBlockEntity cable) {
                for(var side:Direction.values()) {
                    var part=cable.getPart(side);
                    if(part instanceof appeng.parts.storagebus.StorageBusPart storage){if(!ExternalStorageParticipants.add(storage,result))return Set.of();continue;}
                    if(part!=null && !(part instanceof appeng.parts.p2p.MEP2PTunnelPart)
                            && !(part instanceof appeng.parts.networking.QuartzFiberPart)
                            && part.getClass()!=appeng.parts.reporting.ItemTerminalPart.class)return Set.of();
                }
            }else if(!EXTERNAL.contains(be.getClass().getName()) && !RecoveryParticipants.addonBlock(be))return Set.of();
            if(be instanceof appeng.blockentity.storage.DriveBlockEntity drive)
                for(int i=0;i<drive.getInternalInventory().size();i++)if(!RecoveryParticipants.itemBackedCell(drive,i))return Set.of();
            result.add(be);
        }
        return RecoveryParticipants.linksCovered(result)?result:Set.of();
    }
    private static Set<BlockEntity> participants(BaseAccelerator host,ServerLevel world,BlockPos min,BlockPos max) {
        var result=Collections.newSetFromMap(new IdentityHashMap<BlockEntity,Boolean>());result.add(host);
        var grids=connectedGrids(host);if(grids.isEmpty())return Set.of();
        for(var grid:grids)for(var node:grid.getNodes()) {
            var owner=node.getOwner();
            var be=owner instanceof appeng.parts.AEBasePart part?part.getBlockEntity():owner instanceof BlockEntity block?block:null;
            if(be==null)return Set.of();
            if(be.getLevel()==world)continue;
            if(be.getLevel()!=host.getLevel() || be.getLevel().getBlockEntity(be.getBlockPos())!=be)return Set.of();
            if(be==host || emptyComputationNode(be)){result.add(be);continue;}
            if(be instanceof appeng.blockentity.networking.CableBusBlockEntity cable) {
                for(var side:Direction.values()) {
                    var part=cable.getPart(side);
                    if(part instanceof appeng.parts.storagebus.StorageBusPart storage){if(!ExternalStorageParticipants.add(storage,result))return Set.of();continue;}
                    if(part!=null && !(part instanceof appeng.parts.p2p.MEP2PTunnelPart)
                            && !(part instanceof appeng.parts.networking.QuartzFiberPart)
                            && part.getClass()!=appeng.parts.reporting.ItemTerminalPart.class)return Set.of();
                }
            }else if(!EXTERNAL.contains(be.getClass().getName()) && !RecoveryParticipants.addonBlock(be))return Set.of();
            if(be instanceof appeng.blockentity.storage.DriveBlockEntity drive)
                for(int i=0;i<drive.getInternalInventory().size();i++)if(!RecoveryParticipants.itemBackedCell(drive,i))return Set.of();
            result.add(be);
        }
        if(!RecoveryParticipants.linksCovered(result))return Set.of();
        if(!world.getEntitiesOfClass(net.minecraft.world.entity.Entity.class,NativeRegionMove.box(min,max)).isEmpty())return Set.of();
        var nativeBlocks=NativeRegionMove.blockEntities(world,min,max);
        var structureParts=new HashSet<BlockPos>();
        if(NativeMachines.gt())for(var machine:nativeBlocks)
            if(com.syaru.ae2machines.platform.GtMultiblockAdapter.supports(machine)
                    && com.syaru.ae2machines.platform.GtMultiblockAdapter.worldIdle(machine))
                structureParts.addAll(com.syaru.ae2machines.platform.GtMultiblockAdapter.worldOwnedBlocks(machine));
        for(var be:nativeBlocks) {
            if(structureParts.contains(be.getBlockPos()))continue;
            if(be instanceof com.syaru.ae2machines.block.BaseEnergyPort)continue;
            if(be instanceof com.syaru.ae2machines.api.NativeParallelMachine declared){
                var contract=declared.nativeParallelContract();
                if(contract==null || !contract.localStateOnly() || contract.id()==null || contract.id().isBlank() || contract.revision()<1)return Set.of();
                try{Objects.requireNonNull(com.syaru.ae2machines.runtime.NativeParallelContext.call(be,()->contract.audit(be)));}
                catch(RuntimeException unsupported){return Set.of();}
                var nativeRuntime=com.syaru.ae2machines.runtime.NativeParallelRuntime.get(be);
                if(nativeRuntime==null || nativeRuntime.quarantined())return Set.of();
                continue;
            }
            if(be.getClass()==appeng.blockentity.crafting.PatternProviderBlockEntity.class
                    || be.getClass()==appeng.blockentity.networking.CableBusBlockEntity.class)continue;
            if(com.syaru.ae2machines.runtime.NativeWorldMachine.supports(be)
                    && (!NativeMachines.gt() || !com.syaru.ae2machines.platform.GregTechAdapter.supports(be)
                    || com.syaru.ae2machines.runtime.NativeWorldMachine.idle(be)))continue;
            if(NativeMachines.mek() && be.getClass()==mekanism.common.tile.TileEntityEnergyCube.class)continue;
            return Set.of();
        }
        return result;
    }
    public static void save(BaseAccelerator host,ServerLevel world,FactoryJournal journal) {
        var record=journal.read();var blueprint=record.getCompound("blueprint");
        if(record.getBoolean("unwitnessedPower") || host.getLevel().getServer().getPlayerList().getPlayerCount()!=0){clear(journal);return;}
        var min=BlockPos.of(blueprint.getLong("first"));var max=BlockPos.of(blueprint.getLong("last"));
        var blocks=participants(host,world,min,max);
        if(blocks.isEmpty()){clear(journal);return;}
        var external=(ServerLevel)host.getLevel();
        // No transfer/tick can interleave these synchronous saves on the server thread.
        external.save(null,true,false);world.save(null,true,false);
        var chunks=new ListTag();var positions=new TreeSet<Long>();
        for(var block:blocks)positions.add(new ChunkPos(block.getBlockPos()).toLong());
        for(var pos:positions)chunks.add(chunk(external,new ChunkPos(pos)));
        for(var pos:NativeRegionMove.chunks(min,max))chunks.add(chunk(world,pos));
        var checkpoint=new CompoundTag();checkpoint.putInt("schema",1);checkpoint.putUUID("epoch",record.getUUID("worldEpoch"));
        checkpoint.put("chunks",chunks);checkpoint.putString("clock",NbtWitness.digest(NativeWorldRuntime.get(world).state().save(new CompoundTag())));
        try {
            var token=fence(world.getServer()).arm(journal.id());checkpoint.putUUID("fence",token);
            record.put("nativeCheckpoint",checkpoint);journal.write(record);
            ARMED.computeIfAbsent(world.getServer(),ignored->new HashMap<>()).put(journal.id(),new Armed(journal.id(),token));
        }catch(java.io.IOException error){throw new ExternalHandoffs.PersistenceFailure(error);}
    }
    private static CompoundTag chunk(ServerLevel level,ChunkPos pos) {
        level.getChunkSource().chunkMap.flushWorker();
        var raw=com.syaru.ae2machines.platform.NativeCellPlatform.readDurableChunk(level,pos);
        var tag=new CompoundTag();tag.putString("dimension",level.dimension().location().toString());tag.putLong("chunk",pos.toLong());tag.putString("digest",NbtWitness.digest(raw));return tag;
    }
    public static boolean permits(ServerLevel source,ServerLevel world,CompoundTag record) {
        return failure(source,world,record)==null;
    }
    public static String failure(ServerLevel source,ServerLevel world,CompoundTag record) {
        var checkpoint=record.getCompound("nativeCheckpoint");
        if(record.getCompound("blueprint").getInt("schema")!=4)return "UNKNOWN_SCHEMA";
        try {
            if(!checkpoint.hasUUID("fence") || !fence(source.getServer()).permits(record.getUUID("id"),checkpoint.getUUID("fence")))return "EXPIRED_CHECKPOINT";
        }catch(java.io.IOException error){throw new ExternalHandoffs.PersistenceFailure(error);}
        if(record.getBoolean("unwitnessedPower") || !record.getString("state").equals("ACTIVE"))return "UNWITNESSED_EXTERNAL_POWER_OR_INACTIVE";
        if(checkpoint.getInt("schema")!=1 || !checkpoint.hasUUID("epoch") || !record.hasUUID("worldEpoch")
                || !checkpoint.getUUID("epoch").equals(record.getUUID("worldEpoch")))return "NO_MATCHING_CHECKPOINT";
        var chunks=checkpoint.getList("chunks",Tag.TAG_COMPOUND);if(chunks.isEmpty())return "NO_CHUNKS";
        boolean nativeSide=false,externalSide=false;var seen=new HashSet<String>();
        for(var value:chunks) {
            var saved=(CompoundTag)value;String dimension=saved.getString("dimension");
            ServerLevel level;
            if(dimension.equals(world.dimension().location().toString())){level=world;nativeSide=true;}
            else if(dimension.equals(source.dimension().location().toString())){level=source;externalSide=true;}
            else return "UNKNOWN_DIMENSION";
            if(!seen.add(dimension+"/"+saved.getLong("chunk")))return "DUPLICATE_CHUNK";
            var actual=chunk(level,new ChunkPos(saved.getLong("chunk")));
            if(!actual.equals(saved))return "CHUNK_CHANGED:"+dimension+":"+saved.getLong("chunk");
        }
        if(!nativeSide || !externalSide)return "MISSING_SIDE";
        return checkpoint.getString("clock").equals(NbtWitness.digest(NativeWorldRuntime.get(world).state().save(new CompoundTag())))?null:"NATIVE_CLOCK_CHANGED";
    }
    private static void clear(FactoryJournal journal) {
        var record=journal.read();if(!record.contains("nativeCheckpoint"))return;
        record.remove("nativeCheckpoint");journal.write(record);
    }
    /** No witness survives subsequent world work; arbitrary handoffs still require a transaction. */
    public static void beforeOwnedWork(net.minecraft.server.MinecraftServer server,UUID owner){
        var armed=ARMED.get(server);if(armed==null)return;var entry=armed.remove(owner);if(entry==null)return;
        try{fence(server).revoke(entry.owner(),entry.token());}
        catch(java.io.IOException error){throw new ExternalHandoffs.PersistenceFailure(error);}
    }
    public static void beforeTick(net.minecraft.server.MinecraftServer server) {
        var armed=ARMED.remove(server);if(armed==null)return;
        try {for(var entry:armed.values())fence(server).revoke(entry.owner(),entry.token());}
        catch(java.io.IOException error){throw new ExternalHandoffs.PersistenceFailure(error);}
    }
    public static void recoverBeforeTicks(net.minecraft.server.MinecraftServer server) {
        var directory=server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/ae2machines");
        if(!java.nio.file.Files.isDirectory(directory))return;
        try(var files=java.nio.file.Files.list(directory)) {
            for(var file:files.filter(p->p.getFileName().toString().endsWith(".factory")).toList()) {
                String name=file.getFileName().toString();var id=UUID.fromString(name.substring(0,name.length()-8));
                var journal=FactoryJournal.open(directory,id);var record=journal.read();
                if(!record.contains("nativeCheckpoint"))continue;
                var checkpoint=record.getCompound("nativeCheckpoint");var fences=fence(server);
                if(!checkpoint.hasUUID("fence") || !fences.permits(id,checkpoint.getUUID("fence"))
                        || FactoryRecovery.epoch(server).permits(record.getUUID("worldEpoch"))) {
                    if(checkpoint.hasUUID("fence"))fences.revoke(id,checkpoint.getUUID("fence"));
                    record.remove("nativeCheckpoint");journal.write(record);continue;
                }
                var blueprint=record.getCompound("blueprint");
                var source=server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                        com.syaru.ae2machines.platform.PlatformData.id(blueprint.getString("dimension"))));
                if(source==null)throw new IllegalStateException("Native checkpoint source missing");
                var world=NativeCellWorlds.open(source,id);var reason=failure(source,world,record);
                fences.revoke(id,checkpoint.getUUID("fence"));
                record.remove("nativeCheckpoint");
                if(reason==null){record.putUUID("worldEpoch",FactoryRecovery.epoch(server).current());record.remove("nativeRecoveryFailure");}
                else record.putString("nativeRecoveryFailure",reason);
                journal.write(record);
                if(reason==null)org.slf4j.LoggerFactory.getLogger(NativeWorldCheckpoint.class).info("Native factory {} coordinated ME checkpoint reconciled before world ticks",id);
                else org.slf4j.LoggerFactory.getLogger(NativeWorldCheckpoint.class).warn("Native factory {} checkpoint was not accepted: {}",id,reason);
            }
        }catch(java.io.IOException error){throw new ExternalHandoffs.PersistenceFailure(error);}
    }
}
