package com.syaru.ae2machines.compat.gt;

import appeng.api.storage.MEStorage;
import com.gregtechceu.gtceu.common.blockentity.ItemPipeBlockEntity;
import com.gregtechceu.gtceu.common.pipelike.item.*;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.ItemPipeProperties;
import com.syaru.ae2machines.platform.*;
import com.syaru.ae2machines.runtime.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.*;
import java.util.function.BiFunction;
import static com.syaru.ae2machines.world.NativeFactoryCompiler.require;

/** Native GT routing decisions are compiled once; native handlers still perform every insertion. */
public final class GtItemPipes implements CapturedPipes {
    private final Map<Long,ItemPipeBlockEntity> pipes=new LinkedHashMap<>();
    private final Map<String,MEStorage> storage=new HashMap<>();
    private final Map<String,List<ItemRoutePath>> routes=new HashMap<>();
    public static boolean supported(BlockEntity be){return be!=null && be.getClass()==ItemPipeBlockEntity.class;}
    public static CompoundTag inspect(List<BlockEntity> devices,Set<BlockPos> positions) {
        var result=new CompoundTag();var list=new ListTag();var initial=new CompoundTag();
        for(var be:devices)if(supported(be)) {
            var pipe=(ItemPipeBlockEntity)be;var net=pipe.getItemPipeNet();
            var activePaths=new ArrayList<ItemRoutePath>();
            require(net!=null && positions.containsAll(net.getAllNodes().keySet()),"PIPE_NETWORK_OUTSIDE_CAPTURE");
            for(var pos:net.getAllNodes().keySet()) {
                require(pipe.getLevel().getBlockEntity(pos) instanceof ItemPipeBlockEntity,"PIPE_TYPE_CHANGED");
                var peer=(ItemPipeBlockEntity)pipe.getLevel().getBlockEntity(pos);
                for(var side:Direction.values())require(peer.getCoverContainer().getCoverAtSide(side)==null,"PIPE_COVER_NOT_SUPPORTED");
            }
            for(var side:Direction.values()) {
                if(!pipe.isConnected(side) || pipe.isBlocked(side))continue;
                var neighbor=pipe.getBlockPos().relative(side);
                require(positions.contains(neighbor),"PIPE_CONNECTION_OUTSIDE_CAPTURE");
                var adjacent=pipe.getLevel().getBlockEntity(neighbor);
                require(supported(adjacent) || CapturedInventories.supported(adjacent) || NativeMachines.buffered(adjacent)
                        || adjacent instanceof appeng.blockentity.networking.CableBusBlockEntity,"UNSUPPORTED_PIPE_ENDPOINT");
                for(var set:ItemRoutePathSet.values()) {
                    var entry=new CompoundTag();entry.putLong("pipe",pipe.getBlockPos().asLong());entry.putString("side",side.getName());entry.putString("set",set.name());
                    var paths=new ListTag();
                    for(var path:net.getNetData(pipe.getBlockPos(),side,set)) {
                        activePaths.add(path);
                        var endpoint=path.getTargetPipePos().relative(path.getTargetFacing());
                        var target=pipe.getLevel().getBlockEntity(endpoint);
                        require(positions.contains(endpoint) && (CapturedInventories.supported(target) || NativeMachines.buffered(target)),"UNSUPPORTED_PIPE_ROUTE_TARGET");
                        var p=new CompoundTag();p.putLong("targetPipe",path.getTargetPipePos().asLong());p.putString("side",path.getTargetFacing().getName());
                        p.putInt("distance",path.getDistance());p.putInt("priority",path.getProperties().getPriority());p.putFloat("rate",path.getProperties().getTransferRate());p.putBoolean("restrictive",path.isRestrictive());paths.add(p);
                    }
                    entry.put("paths",paths);list.add(entry);
                }
            }
            initial.put(Long.toString(pipe.getBlockPos().asLong()),GtPipeAccess.save(pipe,activePaths));
        }
        if(!list.isEmpty()){result.putInt("schema",1);result.put("routes",list);result.put("initial",initial);}
        return result;
    }
    public GtItemPipes(ServerLevel level,CompoundTag blueprint,CompoundTag saved,BiFunction<Long,Direction,MEStorage> target,Runnable changed) {
        require(blueprint.getCompound("pipes").getInt("schema")==1,"UNKNOWN_PIPE_SCHEMA");
        for(var value:blueprint.getList("blocks",Tag.TAG_COMPOUND)) {
            var block=(CompoundTag)value;var pos=BlockPos.of(block.getLong("pos"));
            var state=NbtUtils.readBlockState(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK),block.getCompound("state"));
            if(!(state.getBlock() instanceof EntityBlock factory))continue;
            var be=factory.newBlockEntity(pos,state);if(!supported(be))continue;
            be.setLevel(level);PlatformData.loadBlock(be,block.getCompound("data"));pipes.put(pos.asLong(),(ItemPipeBlockEntity)be);
        }
        var network=new ItemPipeNet(null) {
            @Override public ServerLevel getLevel(){return level;}
            @Override public List<ItemRoutePath> getNetData(BlockPos pos,Direction side,ItemRoutePathSet set){return routes.getOrDefault(key(pos.asLong(),side)+"/"+set.name(),List.of());}
        };
        var canonical=new HashMap<CompoundTag,ItemRoutePath>();
        for(var value:blueprint.getCompound("pipes").getList("routes",Tag.TAG_COMPOUND)) {
            var entry=(CompoundTag)value;long pos=entry.getLong("pipe");var side=Direction.byName(entry.getString("side"));
            var pipe=pipes.get(pos);require(pipe!=null && side!=null,"PIPE_ROUTE_SOURCE_MISSING");
            var paths=new ArrayList<ItemRoutePath>();
            for(var pathValue:entry.getList("paths",Tag.TAG_COMPOUND)) {
                var p=(CompoundTag)pathValue;var destination=pipes.get(p.getLong("targetPipe"));var face=Direction.byName(p.getString("side"));
                require(destination!=null && face!=null,"PIPE_ROUTE_TARGET_MISSING");
                var endpoint=target.apply(destination.getBlockPos().relative(face).asLong(),face.getOpposite());require(endpoint!=null,"PIPE_INVENTORY_MISSING");
                paths.add(canonical.computeIfAbsent(p.copy(),ignored->GtPipeAccess.route(destination,face,p.getInt("distance"),new ItemPipeProperties(p.getInt("priority"),p.getFloat("rate")),p.getBoolean("restrictive"),endpoint)));
            }
            routes.put(key(pos,side)+"/"+entry.getString("set"),List.copyOf(paths));
            storage.computeIfAbsent(key(pos,side),k->GtPipeAccess.storage(new ItemNetHandler(network,pipe,side) {
                @Override public com.gregtechceu.gtceu.api.cover.CoverBehavior getCoverOnNeighbour(BlockPos ignored,Direction direction){return null;}
            },changed));
        }
        var counters=saved.isEmpty()?blueprint.getCompound("pipes").getCompound("initial"):saved;
        pipes.forEach((pos,pipe)->GtPipeAccess.restore(pipe,counters.getCompound(Long.toString(pos)),allRoutes()));
    }
    private List<ItemRoutePath> allRoutes(){return routes.values().stream().flatMap(Collection::stream).toList();}
    private static String key(long pos,Direction side){return pos+"/"+side.getName();}
    @Override public MEStorage at(long pos,Direction side){return side==null?null:storage.get(key(pos,side));}
    @Override public CompoundTag save(){var saved=new CompoundTag();pipes.forEach((pos,pipe)->saved.put(Long.toString(pos),GtPipeAccess.save(pipe,allRoutes())));return saved;}
}
