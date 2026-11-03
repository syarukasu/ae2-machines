package com.syaru.ae2machines.world;

import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.parts.automation.IOBusPart;
import appeng.parts.networking.QuartzFiberPart;
import com.syaru.ae2machines.platform.PlatformData;
import com.syaru.ae2machines.runtime.NativeMachines;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.EntityBlock;
import java.util.*;

/** Recovery requires an inventory domain with no path to an external ME inventory or CPU. */
public final class InternalRecovery {
    private InternalRecovery() {}

    public static boolean permits(ServerLevel level, CompoundTag record) {
        var blueprint=record.getCompound("blueprint");
        if(blueprint.getInt("schema")!=3 || !record.contains("networkCells") || !record.contains("networkNative"))return false;
        var topology=blueprint.getCompound("network");
        if(topology.getInt("version")!=2)return false;
        var mutable=new HashSet<Long>();
        var nativeBlocks=new HashMap<Long,CableBusBlockEntity>();
        for(var value:blueprint.getList("blocks",Tag.TAG_COMPOUND)) {
            var block=(CompoundTag)value;
            var state=NbtUtils.readBlockState(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK),block.getCompound("state"));
            if(!(state.getBlock() instanceof EntityBlock factory))continue;
            var be=factory.newBlockEntity(BlockPos.of(block.getLong("pos")),state);
            if(!(be instanceof CableBusBlockEntity cable))continue;
            cable.setLevel(level);PlatformData.loadBlock(cable,block.getCompound("data"));
            for(var side:Direction.values()) {
                var part=cable.getPart(side);
                // Interface auto-stocking and Storage Bus exposure do not share the local bus transaction.
                if(part!=null && !(part instanceof IOBusPart) && !(part instanceof QuartzFiberPart))return false;
            }
            nativeBlocks.put(block.getLong("pos"),cable);
        }
        for(var value:topology.getList("nodes",Tag.TAG_COMPOUND)) {
            var node=(CompoundTag)value;long id=node.getLong("id");
            switch(node.getString("kind")) {
                case "provider", "drive" -> mutable.add(id);
                case "native" -> {
                    var cable=nativeBlocks.get(node.getLong("pos"));if(cable==null)return false;
                    var side=Direction.byName(node.getString("port"));
                    if(side!=null && cable.getPart(side) instanceof IOBusPart)mutable.add(id);
                }
                default -> {return false;}
            }
        }
        if(!isolated(topology,mutable))return false;
        // Foreign cell implementations can expose assets outside this journal even on an isolated Grid.
        for(var key:record.getCompound("networkCells").getAllKeys())
            for(var value:record.getCompound("networkCells").getList(key,Tag.TAG_COMPOUND)) {
                var item=PlatformData.loadItem(((CompoundTag)value).getCompound("item"),level);
                if(item.getItem().getClass()!=appeng.items.storage.BasicStorageCell.class)return false;
            }
        var lanes=NativeFactoryCompiler.lanes(blueprint);
        for(int i=0;i<lanes.size();i++) {
            var lane=lanes.get(i);
            if(lane.contains("gtStructure"))return false;
            boolean buffered=false;
            for(var value:lane.getList("blocks",Tag.TAG_COMPOUND)) {
                var block=(CompoundTag)value;if(block.getLong("pos")!=lane.getLong("machine"))continue;
                var state=NbtUtils.readBlockState(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK),block.getCompound("state"));
                if(state.getBlock() instanceof EntityBlock factory)buffered=NativeMachines.buffered(factory.newBlockEntity(BlockPos.of(block.getLong("pos")),state));
            }
            if(!buffered || !localLedger(record.getCompound("runtimeLanes").getCompound(Integer.toString(i))))return false;
        }
        return true;
    }

    static boolean localLedger(CompoundTag lane) {
        if(lane.getBoolean("inputIntent") || lane.getBoolean("deliveryIntent") || !lane.getList("delivered",Tag.TAG_COMPOUND).isEmpty())return false;
        var runtime=lane.getCompound("runtime");
        if(!Set.of(3,4,5).contains(runtime.getInt("schema")) || runtime.getBoolean("quarantined") || !runtime.getList("forgotten",Tag.TAG_COMPOUND).isEmpty())return false;
        var jobs=runtime.getList("jobs",Tag.TAG_COMPOUND);
        if(!lane.hasUUID("normalJob"))return !lane.getBoolean("bufferedJob") && jobs.isEmpty();
        if(!lane.getBoolean("bufferedJob") || jobs.size()!=1)return false;
        var job=jobs.getCompound(0);
        return job.hasUUID("id") && job.getUUID("id").equals(lane.getUUID("normalJob"))
                && job.getString("digest").equals("ae2-normal")
                && Set.of("RUNNING","OUTPUT_READY").contains(job.getString("state"));
    }

    static boolean isolated(CompoundTag topology,Set<Long> mutable) {
        var graph=new HashMap<Long,Set<Long>>();
        for(var value:topology.getList("nodes",Tag.TAG_COMPOUND)) {
            var node=(CompoundTag)value;
            if(!node.contains("id",Tag.TAG_LONG) || graph.put(node.getLong("id"),new HashSet<>())!=null)return false;
        }
        for(var value:topology.getList("edges",Tag.TAG_COMPOUND)) {
            var edge=(CompoundTag)value;long a=edge.getLong("a"),b=edge.getLong("b");
            if(!graph.containsKey(a) || !graph.containsKey(b))return false;
            graph.get(a).add(b);graph.get(b).add(a);
        }
        var reachable=new HashSet<Long>();var queue=new ArrayDeque<Long>();
        for(var value:topology.getList("boundaries",Tag.TAG_COMPOUND)) {
            long id=((CompoundTag)value).getLong("node");if(!graph.containsKey(id))return false;
            if(reachable.add(id))queue.add(id);
        }
        if(queue.isEmpty() || mutable.isEmpty() || !graph.keySet().containsAll(mutable))return false;
        // Quartz-fiber overlays share energy, not inventory reachability.
        while(!queue.isEmpty())for(long next:graph.get(queue.remove()))if(reachable.add(next))queue.add(next);
        return Collections.disjoint(reachable,mutable);
    }
}
