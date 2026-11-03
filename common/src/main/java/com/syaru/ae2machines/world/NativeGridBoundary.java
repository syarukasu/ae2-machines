package com.syaru.ae2machines.world;

import appeng.api.networking.IGridNode;
import appeng.api.parts.IPartHost;
import appeng.me.helpers.IGridConnectedBlockEntity;
import appeng.parts.AEBasePart;
import com.syaru.ae2machines.platform.VirtualP2PPart;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import static com.syaru.ae2machines.world.NativeFactoryCompiler.require;

/** Uses real nodes in the native world; subnet edges and storage logic remain AE2-owned. */
public final class NativeGridBoundary {
    private NativeGridBoundary() {}
    public static Map<String,IGridNode> ports(BlockEntity be) {
        var nodes=new LinkedHashMap<String,IGridNode>();
        if(be instanceof IGridConnectedBlockEntity host)nodes.put("",host.getMainNode().getNode());
        if(be instanceof IPartHost host) {
            if(host.getPart(null)!=null)nodes.put("",host.getPart(null).getGridNode());
            for(var side:Direction.values())if(host.getPart(side) instanceof AEBasePart part) {
                nodes.put(side.getName(),part.getGridNode());
                if(part.getExternalFacingNode()!=null)nodes.put(side.getName()+"/outer",part.getExternalFacingNode());
            }
        }
        nodes.values().removeIf(Objects::isNull);return nodes;
    }
    public static ListTag inspect(ServerLevel level,List<BlockEntity> blocks,Set<BlockPos> positions) {
        if(NativeCellEndpoints.present(blocks))return NativeCellEndpoints.inspect(level,blocks,positions);
        record Port(long pos,String name){}
        var nodes=new IdentityHashMap<IGridNode,Port>();
        for(var be:blocks)for(var entry:ports(be).entrySet()) {
            require(entry.getValue().isActive(),"INTERNAL_NETWORK_OFFLINE");
            nodes.put(entry.getValue(),new Port(be.getBlockPos().asLong(),entry.getKey()));
        }
        var result=new ListTag();
        for(var entry:nodes.entrySet())for(var connection:entry.getKey().getConnections()) {
            var other=connection.getOtherSide(entry.getKey());if(nodes.containsKey(other))continue;
            require(other.getOwner() instanceof VirtualP2PPart,"UNDECLARED_ME_BOUNDARY");
            var tunnel=(VirtualP2PPart)other.getOwner();
            require(tunnel.isOutput() && other==tunnel.getExternalFacingNode() && !positions.contains(tunnel.getBlockEntity().getBlockPos()),"INVALID_P2P_OUTPUT");
            require(entry.getKey().getGrid().getCraftingService().getCpus().stream().noneMatch(c->c.isBusy()),"ACTIVE_CRAFTING_CPU");
            var tag=new CompoundTag();tag.putLong("pos",entry.getValue().pos());tag.putString("port",entry.getValue().name());
            tag.putLong("boundary",tunnel.getBlockEntity().getBlockPos().asLong());tag.putString("side",tunnel.getSide().getName());tag.putShort("frequency",tunnel.getFrequency());result.add(tag);
        }
        require(!result.isEmpty(),"VIRTUAL_P2P_BOUNDARY_REQUIRED");return result;
    }
}
