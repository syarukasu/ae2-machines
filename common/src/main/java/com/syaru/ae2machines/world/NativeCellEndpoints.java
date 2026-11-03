package com.syaru.ae2machines.world;

import appeng.api.networking.*;
import appeng.api.parts.IPartHost;
import com.syaru.ae2machines.block.BaseEnergyPort;
import com.syaru.ae2machines.platform.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import static com.syaru.ae2machines.world.NativeFactoryCompiler.require;

/** Native P2P owns the decoded main grid; the computation port extends only its carrier. */
public final class NativeCellEndpoints {
    private NativeCellEndpoints(){}
    public static List<VirtualP2PPart> outputs(Collection<BlockEntity> blocks){
        var result=new ArrayList<VirtualP2PPart>();
        for(var be:blocks)if(be instanceof IPartHost host)for(var side:Direction.values())
            if(host.getPart(side) instanceof VirtualP2PPart tunnel && tunnel.isOutput())result.add(tunnel);
        return result;
    }
    public static boolean present(Collection<BlockEntity> blocks){
        return blocks.stream().anyMatch(be->be instanceof BaseEnergyPort) || !outputs(blocks).isEmpty();
    }
    public static ListTag inspect(ServerLevel level,List<BlockEntity> blocks,Set<BlockPos> positions){
        var tunnels=outputs(blocks);require(!tunnels.isEmpty(),"INTERNAL_VIRTUAL_P2P_OUTPUT_REQUIRED");
        require(blocks.stream().anyMatch(be->be instanceof BaseEnergyPort && be.getBlockState().is(MachinesMod.ENERGY.get())),"INTERNAL_VIRTUAL_ENERGY_PORT_REQUIRED");
        var carriers=Collections.newSetFromMap(new IdentityHashMap<IGrid,Boolean>());
        var nodes=Collections.newSetFromMap(new IdentityHashMap<IGridNode,Boolean>());
        for(var be:blocks)for(var node:NativeGridBoundary.ports(be).values()){
            require(node.isActive(),"INTERNAL_NETWORK_OFFLINE");nodes.add(node);
        }
        IGrid main=null;var result=new ListTag();
        for(var tunnel:tunnels){
            var input=tunnel.getInput();var carrier=tunnel.getMainNode().getGrid();var outside=input==null?null:input.getExternalFacingNode();
            require(tunnel.getFrequency()!=0 && tunnel.getMainNode().isActive() && outside!=null && input.getMainNode().isActive(),"INTERNAL_P2P_INPUT_MISSING");
            require(input.getBlockEntity().getLevel()==level && !positions.contains(input.getBlockEntity().getBlockPos()),"P2P_INPUT_MUST_REMAIN_OUTSIDE");
            var decoded=tunnel.getExternalFacingNode();
            require(decoded!=null && decoded.isActive() && decoded.getGrid()==outside.getGrid() && decoded.getGrid()!=carrier,"P2P_CARRIER_AND_MAIN_MUST_BE_SEPARATE");
            require(main==null || main==decoded.getGrid(),"MULTIPLE_EXTERNAL_MAIN_GRIDS");main=decoded.getGrid();carriers.add(carrier);
            var tag=new CompoundTag();tag.putBoolean("internal",true);tag.putLong("pos",tunnel.getBlockEntity().getBlockPos().asLong());
            tag.putString("side",tunnel.getSide().getName());tag.putShort("frequency",tunnel.getFrequency());result.add(tag);
        }
        require(main.getCraftingService().getCpus().stream().noneMatch(cpu->cpu.isBusy()),"ACTIVE_CRAFTING_CPU");
        for(var node:nodes)for(var connection:node.getConnections()){
            var other=connection.getOtherSide(node);if(nodes.contains(other))continue;
            // Capture cuts the physical carrier cable. Native P2P's wireless main-grid edge is also expected.
            boolean carrier=carriers.contains(node.getGrid());
            boolean tunnelEdge=tunnels.stream().anyMatch(t->node==t.getExternalFacingNode() && t.getInput()!=null && other==t.getInput().getExternalFacingNode());
            require(carrier || tunnelEdge,"UNDECLARED_ME_BOUNDARY");
        }
        return result;
    }
    public static VirtualP2PPart output(ServerLevel world,CompoundTag endpoint){
        var pos=BlockPos.of(endpoint.getLong("pos"));if(!world.hasChunkAt(pos) || !(world.getBlockEntity(pos) instanceof IPartHost host))return null;
        var side=Direction.byName(endpoint.getString("side"));if(side==null)return null;
        return host.getPart(side) instanceof VirtualP2PPart part && part.isOutput() && part.getFrequency()==endpoint.getShort("frequency")?part:null;
    }
    public static IGrid decodedGrid(ServerLevel world,ListTag endpoints){
        IGrid main=null;
        for(var value:endpoints){
            var tunnel=output(world,(CompoundTag)value);if(tunnel==null || !tunnel.getMainNode().isActive())return null;
            var input=tunnel.getInput();var node=tunnel.getExternalFacingNode();
            if(input==null || input.getBlockEntity().getLevel()==world || !input.getMainNode().isActive() || node==null || !node.isActive()
                    || input.getExternalFacingNode()==null || node.getGrid()!=input.getExternalFacingNode().getGrid() || node.getGrid()==tunnel.getMainNode().getGrid())return null;
            if(main!=null && main!=node.getGrid())return null;main=node.getGrid();
        }
        return main;
    }
}
