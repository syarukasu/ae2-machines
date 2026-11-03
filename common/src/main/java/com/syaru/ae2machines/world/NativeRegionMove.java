package com.syaru.ae2machines.world;

import appeng.api.ids.AETags;
import appeng.api.movable.*;
import com.syaru.ae2machines.mixin.*;
import com.syaru.ae2machines.platform.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.Visibility;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.ticks.*;
import static com.syaru.ae2machines.world.NativeFactoryCompiler.require;

/** Same-coordinate moves preserve native absolute links; no recipe outputs or machine inventories are synthesized. */
public final class NativeRegionMove {
    private record Move(BlockEntity be,BlockState state,IBlockEntityMoveStrategy strategy,CompoundTag data){}
    private NativeRegionMove() {}
    public static AABB box(BlockPos min,BlockPos max){return new AABB(min.getX(),min.getY(),min.getZ(),max.getX()+1,max.getY()+1,max.getZ()+1);}
    public static List<BlockEntity> blockEntities(ServerLevel level,BlockPos min,BlockPos max) {
        var result=new ArrayList<BlockEntity>();
        for(var p:BlockPos.betweenClosed(min,max)){var be=level.getBlockEntity(p);if(be!=null)result.add(be);}return result;
    }
    public static CompoundTag image(ServerLevel level,BlockPos min,BlockPos max) {
        var image=new CompoundTag();var blocks=new ListTag();
        var validatedNetworks=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Object,Boolean>());
        for(var p:BlockPos.betweenClosed(min,max)) {
            var state=level.getBlockState(p);if(state.isAir() || state.is(MachinesMod.MARKER.get()))continue;
            boolean knownBounds=com.syaru.ae2machines.runtime.NativeMachines.mek()
                    && com.syaru.ae2machines.compat.mek.MekSpatialBounds.capturable(level,p,min,max,validatedNetworks);
            require(!state.is(AETags.SPATIAL_BLACKLIST) || knownBounds,"SPATIAL_BLACKLIST: "+p);
            var be=level.getBlockEntity(p);require(!com.syaru.ae2machines.runtime.NativeMachines.craftingCpu(be),"CRAFTING_CPU_MUST_REMAIN_OUTSIDE");
            var tag=new CompoundTag();tag.putLong("pos",p.asLong());tag.put("state",NbtUtils.writeBlockState(state));
            if(be!=null)tag.put("data",PlatformData.saveBlock(be));blocks.add(tag);
        }
        image.put("blocks",blocks);image.put("ticks",ticks(level,min,max));image.put("events",events(level,min,max));
        var entities=new ListTag();
        for(var entity:level.getEntitiesOfClass(Entity.class,box(min,max))) {
            require(!(entity instanceof Player),"PLAYER_INSIDE_CAPTURE");
            var b=entity.getBoundingBox();var region=box(min,max);
            require(b.minX>=region.minX && b.maxX<=region.maxX && b.minY>=region.minY && b.maxY<=region.maxY && b.minZ>=region.minZ && b.maxZ<=region.maxZ,"ENTITY_CROSSES_CAPTURE_BOUNDARY");
            if(entity.isPassenger())continue;
            var tag=new CompoundTag();require(entity.save(tag),"ENTITY_CANNOT_BE_STORED");entities.add(tag);
        }
        image.put("entities",entities);
        if(com.syaru.ae2machines.runtime.NativeMachines.mek())image.put("mekCaches",MekStructureStorage.snapshot(level,blockEntities(level,min,max),min,max));
        return image;
    }
    public static void move(ServerLevel source,ServerLevel target,BlockPos min,BlockPos max,FactoryJournal journal,boolean restoring) {
        require(!NativeWorldRuntime.owns(source) || !NativeWorldRuntime.get(source).running(),"MOVING_WORLD_IS_RUNNING");
        try {
            try(var relocation=NativeMoveLifecycle.begin(source,target,min,max)){movePrepared(source,target,min,max,journal,restoring);}
            NativeMoveLifecycle.refresh(target,min,max);
        }
        catch(RuntimeException failure) {
            // A failed force/rename can leave a durable commit despite the exception. Do not undo it
            // from the in-memory generation; startup reads the authoritative journal before ticking.
            for(Throwable cause=failure;cause!=null;cause=cause.getCause())if(cause instanceof java.io.IOException)throw new ExternalHandoffs.PersistenceFailure(failure);
            if(journal.read().getString("state").equals("WORLD_MOVE_PREPARED")) {
                try { rollback(source,target,min,max,journal); }
                catch(Throwable recovery){recovery.addSuppressed(failure);throw new ExternalHandoffs.PersistenceFailure(recovery);}
            }
            throw failure;
        }
    }
    private static void movePrepared(ServerLevel source,ServerLevel target,BlockPos min,BlockPos max,FactoryJournal journal,boolean restoring) {
        try(var sourceEntities=new EntityChunks(source,min,max);var targetEntities=new EntityChunks(target,min,max)) {
            if(com.syaru.ae2machines.runtime.NativeMachines.mek())com.syaru.ae2machines.compat.mek.MekRestorationStorage.refreshSaveShares(blockEntities(source,min,max));
            var before=image(source,min,max);
            for(var p:BlockPos.betweenClosed(min,max))require(target.getBlockState(p).isAir() || restoring && target.getBlockState(p).is(MachinesMod.MARKER.get()),"RESTORE_SPACE_OCCUPIED: "+p);
            require(target.getEntitiesOfClass(Entity.class,box(min,max)).isEmpty(),"DESTINATION_ENTITIES_PRESENT");
            // Matrix intake is accepted before being distributed to its physical cells. Settle it
            // only after move preflight, never while merely inspecting a player's selection.
            if(com.syaru.ae2machines.runtime.NativeMachines.mek() && MekStructureStorage.settlePendingPower(blockEntities(source,min,max)))
                before=image(source,min,max);
            var restorePlan=restoring && com.syaru.ae2machines.runtime.NativeMachines.mek()
                    ?com.syaru.ae2machines.compat.mek.MekRestorationStorage.plan(source,min,max):null;
            var record=journal.read();var transfer=new CompoundTag();transfer.put("image",before);transfer.putBoolean("restoring",restoring);
            var markers=new ListTag();
            for(var p:BlockPos.betweenClosed(min,max))if(target.getBlockState(p).is(MachinesMod.MARKER.get()))markers.add(LongTag.valueOf(p.asLong()));
            transfer.put("destinationMarkers",markers);
            transfer.put("destinationTicks",ticks(target,min,max));transfer.put("destinationEvents",events(target,min,max));
            record.put("worldTransfer",transfer);record.putString("state","WORLD_MOVE_PREPARED");journal.write(record);
            if(restorePlan!=null)restorePlan.verify();
            try(var projection=restorePlan!=null?com.syaru.ae2machines.compat.mek.MekRestorationStorage.project():null){
            if(restorePlan!=null){
                restorePlan.apply();
                com.syaru.ae2machines.compat.mek.MekRestorationStorage.refreshSaveShares(blockEntities(source,min,max));
                for(var be:blockEntities(source,min,max))require(com.syaru.ae2machines.compat.mek.MekWorldParallel.canRestore(be),"UNSUPPORTED_NATIVE_RESTORE_CAPACITY");
                MekStructureStorage.snapshot(source,blockEntities(source,min,max),min,max);
            }
            var moves=new ArrayList<Move>();
            // Prepare every native BE strategy before removing any block from either world.
            for(var be:blockEntities(source,min,max)) {
                var strategy=BlockEntityMoveStrategies.get(be);var data=NativeCellPlatform.beginMove(strategy,be);
                require(data!=null,"NATIVE_BLOCK_REFUSED_MOVE: "+be.getBlockPos());moves.add(new Move(be,be.getBlockState(),strategy,data));
            }
            var area=new BoundingBox(min.getX(),min.getY(),min.getZ(),max.getX(),max.getY(),max.getZ());
            target.getBlockTicks().clearArea(area);target.getFluidTicks().clearArea(area);clearEvents(target,min,max);
            // Clear all source states first: another BE's removal callback must not resurrect a peer.
            for(var p:BlockPos.betweenClosed(min,max)) {
                var state=source.getBlockState(p);if(state.is(MachinesMod.MARKER.get()))continue;
                setRaw(target,p,state);setRaw(source,p,Blocks.AIR.defaultBlockState());
            }
            for(var move:moves){NativeMoveLifecycle.beforeRemoval(move.be);source.removeBlockEntity(move.be.getBlockPos());}
            for(var move:moves)require(move.strategy.completeMove(move.be,move.state,move.data,target,move.be.getBlockPos()),"NATIVE_BLOCK_MOVE_FAILED: "+move.be.getBlockPos());
            source.getBlockTicks().clearArea(area);source.getFluidTicks().clearArea(area);
            clearEvents(source,min,max);restoreEvents(target,before.getList("events",Tag.TAG_COMPOUND));
            restoreTicks(target,before.getList("ticks",Tag.TAG_COMPOUND));
            var roots=source.getEntitiesOfClass(Entity.class,box(min,max)).stream().filter(e->!e.isPassenger()).toList();
            for(var entity:roots)moveEntityResult(entity,target);
            update(source,min,max);update(target,min,max);
            var remaining=blockEntities(source,min,max);
            require(remaining.isEmpty(),"SOURCE_NATIVE_BLOCKS_REMAIN: "+remaining.stream().map(be->be.getBlockPos()+"="+be.getClass().getName()+"/"+source.getBlockState(be.getBlockPos())).toList());
            require(source.getEntitiesOfClass(Entity.class,box(min,max)).isEmpty(),"SOURCE_ENTITIES_REMAIN");
            for(var value:before.getList("blocks",Tag.TAG_COMPOUND)) {
                var saved=(CompoundTag)value;var p=BlockPos.of(saved.getLong("pos"));
                require(NbtUtils.writeBlockState(target.getBlockState(p)).equals(saved.getCompound("state")),"MOVED_BLOCK_STATE_MISMATCH: "+p);
                if(saved.contains("data"))require(target.getBlockEntity(p)!=null,"MOVED_BLOCK_ENTITY_MISSING: "+p);
            }
            // The Mek inventory cache is global SavedData, not part of a casing's block NBT.
            source.getServer().overworld().getDataStorage().save();
            source.save(null,true,false);target.save(null,true,false);
            record=journal.read();record.remove("worldTransfer");record.putString("state",restoring?"RESTORED":"CAPTURED");
            if(restorePlan!=null)record.put("restorationRemainder",restorePlan.ledger());journal.write(record);
            }
        }
    }
    /** Before any world can resume, put every owned asset back on the transaction's original side. */
    public static void rollback(ServerLevel source,ServerLevel target,BlockPos min,BlockPos max,FactoryJournal journal) {
        try(var relocation=NativeMoveLifecycle.begin(source,target,min,max)){rollbackPrepared(source,target,min,max,journal);}
        NativeMoveLifecycle.refresh(source,min,max);
    }
    private static void rollbackPrepared(ServerLevel source,ServerLevel target,BlockPos min,BlockPos max,FactoryJournal journal) {
        var record=journal.read();require(record.getString("state").equals("WORLD_MOVE_PREPARED"),"NO_PREPARED_NATIVE_MOVE");
        var transfer=record.getCompound("worldTransfer");var before=transfer.getCompound("image");
        require(before.contains("blocks") && before.contains("entities") && before.contains("ticks"),"NATIVE_MOVE_IMAGE_INCOMPLETE");
        try(var sourceEntities=new EntityChunks(source,min,max);var targetEntities=new EntityChunks(target,min,max)) {
            var previous=new ArrayList<>(blockEntities(source,min,max));previous.addAll(blockEntities(target,min,max));
            clearRegion(source,min,max);clearRegion(target,min,max);
            for(var marker:transfer.getList("destinationMarkers",Tag.TAG_LONG))setRaw(target,BlockPos.of(((LongTag)marker).getAsLong()),MachinesMod.MARKER.get().defaultBlockState());
            if(com.syaru.ae2machines.runtime.NativeMachines.mek())MekStructureStorage.restore(source,before.getList("mekCaches",Tag.TAG_COMPOUND),previous);
            // Restore all states before loading BEs, just as the successful native move does.
            for(var value:before.getList("blocks",Tag.TAG_COMPOUND)) {
                var saved=(CompoundTag)value;setRaw(source,BlockPos.of(saved.getLong("pos")),NbtUtils.readBlockState(source.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK),saved.getCompound("state")));
            }
            for(var value:before.getList("blocks",Tag.TAG_COMPOUND)) {
                var saved=(CompoundTag)value;if(!saved.contains("data"))continue;
                var p=BlockPos.of(saved.getLong("pos"));var state=source.getBlockState(p);
                var be=state.getBlock() instanceof EntityBlock factory?factory.newBlockEntity(p,state):null;
                require(be!=null,"NATIVE_RECOVERY_BLOCK_ENTITY_MISSING: "+p);source.setBlockEntity(be);PlatformData.loadBlock(be,saved.getCompound("data"));
            }
            restoreTicks(source,before.getList("ticks",Tag.TAG_COMPOUND));restoreEvents(source,before.getList("events",Tag.TAG_COMPOUND));
            restoreTicks(target,transfer.getList("destinationTicks",Tag.TAG_COMPOUND));restoreEvents(target,transfer.getList("destinationEvents",Tag.TAG_COMPOUND));
            for(var value:before.getList("entities",Tag.TAG_COMPOUND)) {
                var entity=EntityType.loadEntityRecursive((CompoundTag)value,source,java.util.function.Function.identity());
                require(entity!=null && source.tryAddFreshEntityWithPassengers(entity),"NATIVE_RECOVERY_ENTITY_MISSING_OR_DUPLICATED");
            }
            update(source,min,max);update(target,min,max);
            source.getServer().overworld().getDataStorage().save();source.save(null,true,false);target.save(null,true,false);
            record.remove("worldTransfer");record.remove("worldFailure");record.putString("state",transfer.getBoolean("restoring")?"CAPTURED":"RESTORED");
            record.putUUID("worldEpoch",FactoryRecovery.epoch(source.getServer()).current());journal.write(record);
        }
        if(!transfer.getBoolean("restoring"))NativeCellWorlds.retire(target);
    }
    private static void clearRegion(ServerLevel level,BlockPos min,BlockPos max) {
        var blocks=blockEntities(level,min,max);
        for(var p:BlockPos.betweenClosed(min,max))if(!level.getBlockState(p).is(MachinesMod.MARKER.get()))setRaw(level,p,Blocks.AIR.defaultBlockState());
        for(var be:blocks){NativeMoveLifecycle.beforeRemoval(be);level.removeBlockEntity(be.getBlockPos());}
        for(var entity:level.getEntitiesOfClass(Entity.class,box(min,max))){require(!(entity instanceof Player),"PLAYER_INSIDE_RECOVERY");entity.discard();}
        var area=new BoundingBox(min.getX(),min.getY(),min.getZ(),max.getX(),max.getY(),max.getZ());
        level.getBlockTicks().clearArea(area);level.getFluidTicks().clearArea(area);clearEvents(level,min,max);
        ((NativeEntityStorageAccess)((NativeServerWorldAccess)level).ae2machines$entities()).ae2machines$storage().flush(false);
    }
    private static ListTag events(ServerLevel level,BlockPos min,BlockPos max) {
        var result=new ListTag();
        for(var event:((NativeServerWorldAccess)level).ae2machines$blockEvents())if(contains(event.pos(),min,max)) {
            var tag=new CompoundTag();tag.putLong("pos",event.pos().asLong());tag.putString("block",BuiltInRegistries.BLOCK.getKey(event.block()).toString());
            tag.putInt("a",event.paramA());tag.putInt("b",event.paramB());result.add(tag);
        }
        return result;
    }
    private static void clearEvents(ServerLevel level,BlockPos min,BlockPos max){((NativeServerWorldAccess)level).ae2machines$blockEvents().removeIf(e->contains(e.pos(),min,max));}
    private static void restoreEvents(ServerLevel level,ListTag events) {
        for(var value:events){var e=(CompoundTag)value;level.blockEvent(BlockPos.of(e.getLong("pos")),BuiltInRegistries.BLOCK.get(PlatformData.id(e.getString("block"))),e.getInt("a"),e.getInt("b"));}
    }
    private static Entity moveEntityResult(Entity entity,ServerLevel target) {
        var passengers=new ArrayList<>(entity.getPassengers());for(var p:passengers)p.stopRiding();
        var moved=NativeEntityPlatform.move(entity,target);require(moved!=null,"NATIVE_ENTITY_MOVE_FAILED");
        for(var passenger:passengers)require(moveEntityResult(passenger,target).startRiding(moved,true),"NATIVE_PASSENGER_MOVE_FAILED");return moved;
    }
    private static void setRaw(ServerLevel level,BlockPos p,BlockState state) {
        var chunk=level.getChunkAt(p);chunk.getSection(chunk.getSectionIndex(p.getY())).setBlockState(p.getX()&15,p.getY()&15,p.getZ()&15,state);chunk.setUnsaved(true);
    }
    private static ListTag ticks(ServerLevel level,BlockPos min,BlockPos max) {
        var out=new ListTag();
        for(var chunk:chunks(min,max)) {
            var c=level.getChunk(chunk.x,chunk.z);
            ((LevelChunkTicks<Block>)c.getBlockTicks()).getAll().filter(t->contains(t.pos(),min,max)).forEach(t->out.add(tick(t,BuiltInRegistries.BLOCK.getKey(t.type()).toString(),false,level.getGameTime())));
            ((LevelChunkTicks<Fluid>)c.getFluidTicks()).getAll().filter(t->contains(t.pos(),min,max)).forEach(t->out.add(tick(t,BuiltInRegistries.FLUID.getKey(t.type()).toString(),true,level.getGameTime())));
        }
        return out;
    }
    private static CompoundTag tick(ScheduledTick<?> tick,String type,boolean fluid,long now) {
        var tag=new CompoundTag();tag.putLong("pos",tick.pos().asLong());tag.putString("type",type);tag.putBoolean("fluid",fluid);
        tag.putLong("delay",Math.max(0,Math.subtractExact(tick.triggerTick(),now)));tag.putInt("priority",tick.priority().getValue());tag.putLong("order",tick.subTickOrder());return tag;
    }
    private static void restoreTicks(ServerLevel level,ListTag ticks) {
        for(var value:ticks) {
            var tag=(CompoundTag)value;var p=BlockPos.of(tag.getLong("pos"));long at=Math.addExact(level.getGameTime(),tag.getLong("delay"));var priority=TickPriority.byValue(tag.getInt("priority"));
            if(tag.getBoolean("fluid"))level.getFluidTicks().schedule(new ScheduledTick<>(BuiltInRegistries.FLUID.get(PlatformData.id(tag.getString("type"))),p,at,priority,tag.getLong("order")));
            else level.getBlockTicks().schedule(new ScheduledTick<>(BuiltInRegistries.BLOCK.get(PlatformData.id(tag.getString("type"))),p,at,priority,tag.getLong("order")));
        }
    }
    public static boolean contains(BlockPos p,BlockPos min,BlockPos max){return p.getX()>=min.getX() && p.getX()<=max.getX() && p.getY()>=min.getY() && p.getY()<=max.getY() && p.getZ()>=min.getZ() && p.getZ()<=max.getZ();}
    public static List<ChunkPos> chunks(BlockPos min,BlockPos max){var out=new ArrayList<ChunkPos>();for(int x=min.getX()>>4;x<=max.getX()>>4;x++)for(int z=min.getZ()>>4;z<=max.getZ()>>4;z++)out.add(new ChunkPos(x,z));return out;}
    private static void update(ServerLevel level,BlockPos min,BlockPos max) {
        for(var pos:chunks(min,max)) {
            var c=level.getChunk(pos.x,pos.z);Heightmap.primeHeightmaps(c,EnumSet.of(Heightmap.Types.WORLD_SURFACE,Heightmap.Types.OCEAN_FLOOR,Heightmap.Types.MOTION_BLOCKING,Heightmap.Types.MOTION_BLOCKING_NO_LEAVES));
            level.getChunkSource().getLightEngine().lightChunk(c,false);
        }
        for(var p:BlockPos.betweenClosed(min,max))level.updateNeighborsAt(p,level.getBlockState(p).getBlock());
    }
    private static final class EntityChunks implements AutoCloseable {
        private final net.minecraft.world.level.entity.PersistentEntitySectionManager<Entity> manager;
        private final ServerLevel level;
        private final Map<ChunkPos,Visibility> previous=new LinkedHashMap<>();
        EntityChunks(ServerLevel level,BlockPos min,BlockPos max) {
            this.level=level;
            manager=((NativeServerWorldAccess)level).ae2machines$entities();var access=(NativeEntityStorageAccess)manager;
            for(var pos:chunks(min,max)) {
                level.getChunk(pos.x,pos.z);
                previous.put(pos,access.ae2machines$visibility().get(pos.toLong()));manager.updateChunkStatus(pos,Visibility.TRACKED);
            }
            access.ae2machines$storage().flush(false);manager.tick();
            for(var pos:previous.keySet())require(manager.areEntitiesLoaded(pos.toLong()),"ENTITY_CHUNK_NOT_LOADED");
        }
        @Override public void close(){previous.forEach((pos,status)->manager.updateChunkStatus(pos,
                NativeCellWorlds.owns(level)?(NativeWorldRuntime.get(level).running()?Visibility.TICKING:Visibility.TRACKED):
                        // Recovery has loaded FULL chunks. Restoring a pre-load HIDDEN status hides
                        // the recovered entities until an unrelated future chunk-status transition.
                        status==null || status==Visibility.HIDDEN?Visibility.TRACKED:status));}
    }
}
