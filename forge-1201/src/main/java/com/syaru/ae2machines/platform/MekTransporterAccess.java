package com.syaru.ae2machines.platform;

import appeng.api.stacks.AEItemKey;
import com.syaru.ae2machines.engine.ItemTransit;
import com.syaru.ae2machines.runtime.*;
import mekanism.api.RelativeSide;
import mekanism.common.content.network.transmitter.*;
import mekanism.common.content.transporter.TransporterStack;
import mekanism.common.tile.transmitter.*;
import mekanism.common.tile.prefab.TileEntityConfigurableMachine;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.*;
import static com.syaru.ae2machines.world.NativeFactoryCompiler.require;

public final class MekTransporterAccess {
    private MekTransporterAccess() {}
    public static boolean supported(BlockEntity be) {
        return be!=null && (be.getClass()==TileEntityLogisticalTransporter.class
                || be.getClass()==TileEntityRestrictiveTransporter.class || be.getClass()==TileEntityDiversionTransporter.class);
    }
    public static ListTag inspect(List<BlockEntity> devices,Set<BlockPos> captured) {
        var result=new ListTag();
        for(var be:devices)if(supported(be)) {
            var t=(LogisticalTransporterBase)((TileEntityTransmitter)be).getTransmitter();var net=t.getTransmitterNetwork();
            require(net!=null && net.getTransmitters().stream().allMatch(p->captured.contains(p.getTransmitterTile().getBlockPos())),"PIPE_NETWORK_OUTSIDE_CAPTURE");
            // Capture is an idle ownership transition, just like native machine capture.
            require(t.getTransit().isEmpty(),"TRANSPORTER_NOT_IDLE");
            if(t instanceof DiversionTransporter diversion)for(var mode:diversion.modes)
                require(mode==DiversionTransporter.DiversionControl.DISABLED,"EXTERNAL_PIPE_REDSTONE_CONTROL");
            var node=new CompoundTag();node.putLong("pos",be.getBlockPos().asLong());
            node.putInt("speed",t.tier.getSpeed());node.putInt("pull",t.tier.getPullAmount());node.putDouble("cost",t.getCost());
            node.putInt("color",t.getColor()==null?-1:t.getColor().ordinal());
            var neighbors=new ArrayList<Long>();var faces=new CompoundTag();var endpoints=new ListTag();
            for(var side:Direction.values()) {
                long pos=be.getBlockPos().relative(side).asLong();var neighbor=be.getLevel().getBlockEntity(BlockPos.of(pos));
                var face=new CompoundTag();face.putBoolean("insert",t.exposesInsertCap(side));
                face.putBoolean("pull",t.getConnectionTypeRaw(side)==mekanism.common.lib.transmitter.ConnectionType.PULL);
                faces.put(side.getName(),face);
                if(Transmitter.connectionMapContainsSide(t.getAllCurrentConnections(),side))require(captured.contains(BlockPos.of(pos)),"PIPE_CONNECTION_OUTSIDE_CAPTURE");
                if(Transmitter.connectionMapContainsSide(t.currentTransmitterConnections,side)) {
                    require(supported(neighbor),"UNSUPPORTED_TRANSPORTER_NEIGHBOR");neighbors.add(pos);continue;
                }
                if(!captured.contains(BlockPos.of(pos)) || neighbor==null || !t.canEmitTo(side))continue;
                if(neighbor instanceof appeng.blockentity.networking.CableBusBlockEntity)continue; // bus accesses the pipe, not vice versa
                require(CapturedInventories.supported(neighbor) || NativeMachines.buffered(neighbor),"UNSUPPORTED_TRANSPORTER_ENDPOINT");
                var endpoint=new CompoundTag();endpoint.putLong("pipe",be.getBlockPos().asLong());endpoint.putLong("pos",pos);
                endpoint.putInt("face",side.getOpposite().ordinal());int color=-2;
                if(neighbor instanceof TileEntityConfigurableMachine machine && machine.getEjector().hasStrictInput()) {
                    var c=machine.getEjector().getInputColor(RelativeSide.fromDirections(machine.getDirection(),side.getOpposite()));color=c==null?-1:c.ordinal();
                }
                endpoint.putInt("color",color);endpoints.add(endpoint);
            }
            node.putLongArray("neighbors",neighbors.stream().mapToLong(Long::longValue).toArray());
            node.put("faces",faces);node.put("endpoints",endpoints);result.add(node);
        }
        return result;
    }
    public static void restore(BlockEntity be,List<ItemTransit.Packet<AEItemKey>> packets) {
        var t=(LogisticalTransporterBase)((TileEntityTransmitter)be).getTransmitter();t.getTransit().clear();int id=0;
        for(var packet:packets)if(packet.pipe()==be.getBlockPos().asLong()) {
            var stack=new TransporterStack();stack.itemStack=packet.key().toStack(packet.amount());
            stack.progress=packet.progress();stack.homeLocation=BlockPos.of(packet.home());
            stack.originalLocation=BlockPos.of(packet.home());
            stack.color=packet.color()==-1?null:mekanism.api.text.EnumColor.values()[packet.color()];
            stack.initiatedPath=false;t.addStack(id++,stack);
        }
    }
}
