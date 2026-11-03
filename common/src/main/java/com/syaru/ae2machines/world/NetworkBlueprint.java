package com.syaru.ae2machines.world;

import appeng.api.networking.*;
import appeng.api.parts.IPartHost;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.parts.networking.CablePart;
import com.syaru.ae2machines.runtime.CapturedNetworkDevices;
import com.syaru.ae2machines.platform.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.*;
import static com.syaru.ae2machines.world.NativeFactoryCompiler.require;

/** Records actual AE2 edges, not geometrically inferred or quantum-multiplied channels. */
public final class NetworkBlueprint {
    private NetworkBlueprint() {}
    public static boolean passive(BlockEntity be) {
        if(com.syaru.ae2machines.runtime.CapturedPipes.supported(be))return true;
        if(com.syaru.ae2machines.runtime.CapturedInventories.supported(be))return true;
        if(be instanceof DriveBlockEntity)return be.getClass()==DriveBlockEntity.class;
        return CapturedNetworkDevices.supported(be);
    }
    public static IGridNode node(BlockEntity be) {
        if(be instanceof PatternProviderBlockEntity provider)return provider.getMainNode().getNode();
        if(be instanceof DriveBlockEntity drive)return drive.getMainNode().getNode();
        if(be instanceof IPartHost host && host.getPart(null) instanceof CablePart cable)return cable.getGridNode();
        throw new IllegalArgumentException("UNSUPPORTED_INTERNAL_NETWORK_DEVICE");
    }
    public static CompoundTag inspect(List<BlockEntity> devices,Set<BlockPos> captured) {
        var index=new IdentityHashMap<IGridNode,Long>();
        var nodes=new ListTag();var edges=new ListTag();var boundaries=new ListTag();
        var overlays=new ListTag();long nextId=0;
        CapturedNetworkDevices.validate(devices);
        for(var be:devices) {
          if(com.syaru.ae2machines.runtime.CapturedPipes.supported(be) || com.syaru.ae2machines.runtime.CapturedInventories.supported(be) || com.syaru.ae2machines.runtime.NativeMachines.supports(be) && !com.syaru.ae2machines.runtime.NativeAssemblers.supports(be))continue;
          var ports=CapturedNetworkDevices.supported(be)?CapturedNetworkDevices.ports(be):Map.of("",node(be));
          for(var port:ports.entrySet()) {
            var node=port.getValue();require(node!=null && node.isActive(),"INTERNAL_NETWORK_OFFLINE");
            require(node.getGrid().getCraftingService().getCpus().stream().noneMatch(cpu->cpu.isBusy()),"ACTIVE_CRAFTING_CPU");
            long id=nextId++;
            require(index.put(node,id)==null,"DUPLICATE_NETWORK_NODE");
            var data=new CompoundTag();data.putLong("pos",be.getBlockPos().asLong());data.putInt("owner",node.getOwningPlayerId());
            data.putLong("id",id);data.putString("port",port.getKey());
            data.putDouble("idleAE",node.getIdlePowerUsage());data.putString("color",node.getGridColor().name());
            data.putInt("channels",node.getMaxChannels());data.put("flags",flags(node));
            data.putString("kind",be instanceof PatternProviderBlockEntity?"provider":be instanceof DriveBlockEntity?"drive":"native");
            if(be instanceof DriveBlockEntity drive) {
                data.putInt("priority",drive.getPriority());var cells=new ListTag();
                for(int slot=0;slot<drive.getInternalInventory().size();slot++) {
                    var cell=drive.getOriginalCellInventory(slot);if(cell!=null)cell.persist();
                    var stack=drive.getInternalInventory().getStackInSlot(slot);
                    if(stack.isEmpty())continue;
                    require(cell!=null,"UNSUPPORTED_STORAGE_CELL");
                    var item=new CompoundTag();item.putInt("slot",slot);item.put("item",PlatformData.saveItem(stack,be.getLevel()));cells.add(item);
                }
                data.put("cells",cells);
            }
            nodes.add(data);
          }
        }
        for(var be:devices)if(be instanceof CableBusBlockEntity host)for(var side:Direction.values())
            if(host.getPart(side) instanceof appeng.parts.networking.QuartzFiberPart fiber) {
                var edge=new CompoundTag();edge.putLong("a",index.get(fiber.getGridNode()));edge.putLong("b",index.get(fiber.getExternalFacingNode()));overlays.add(edge);
            }
        for(var entry:index.entrySet())for(var connection:entry.getKey().getConnections()) {
            var other=connection.getOtherSide(entry.getKey());var otherPos=index.get(other);
            if(otherPos!=null) {
                if(entry.getValue()<otherPos) { var e=new CompoundTag();e.putLong("a",entry.getValue());e.putLong("b",otherPos);edges.add(e); }
            } else {
                require(other.getOwner() instanceof VirtualP2PPart,"UNDECLARED_ME_BOUNDARY");
                var tunnel=(VirtualP2PPart)other.getOwner();
                require(other==tunnel.getExternalFacingNode() && tunnel.isOutput() && tunnel.getMainNode().isActive(),"INVALID_P2P_OUTPUT");
                require(!captured.contains(tunnel.getBlockEntity().getBlockPos()),"P2P_BOUNDARY_MUST_REMAIN_OUTSIDE");
                var e=new CompoundTag();e.putLong("node",entry.getValue());e.putLong("boundary",tunnel.getBlockEntity().getBlockPos().asLong());
                e.putString("side",tunnel.getSide().getName());e.putShort("frequency",tunnel.getFrequency());boundaries.add(e);
            }
        }
        require(!boundaries.isEmpty(),"VIRTUAL_P2P_BOUNDARY_REQUIRED");
        var result=new CompoundTag();result.putInt("version",2);result.put("nodes",nodes);result.put("edges",edges);result.put("boundaries",boundaries);result.put("overlays",overlays);return result;
    }
    public static ListTag flags(IGridNode node) {
        var result=new ListTag();for(var flag:GridFlags.values())if(node.hasFlag(flag))result.add(StringTag.valueOf(flag.name()));return result;
    }
}
