package com.syaru.ae2machines.platform;

import appeng.api.stacks.*;
import com.syaru.ae2machines.runtime.*;
import mekanism.common.content.network.transmitter.*;
import mekanism.common.lib.transmitter.ConnectionType;
import mekanism.common.tile.transmitter.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.*;
import static com.syaru.ae2machines.world.NativeFactoryCompiler.require;

public final class MekPipeAccess {
    private MekPipeAccess() {}
    public static boolean supported(BlockEntity be) {
        return be!=null && (be.getClass()==TileEntityMechanicalPipe.class
                || MekanismAdapter.chemicals() && be.getClass()==TileEntityPressurizedTube.class);
    }
    public static boolean accepts(boolean fluid,AEKey key) {
        return fluid?key instanceof AEFluidKey:MekanismAdapter.chemicals() && MekGasPipeAccess.accepts(key);
    }
    public static ListTag inspect(List<BlockEntity> devices,Set<BlockPos> captured) {
        var groups=new LinkedHashMap<UUID,CompoundTag>();
        for(var be:devices)if(supported(be)) {
            var tile=(TileEntityTransmitter)be;var transmitter=tile.getTransmitter();
            var net=transmitter.getTransmitterNetwork();
            require(net!=null,"MEK_PIPE_NETWORK_NOT_READY");
            require(net.getTransmitters().stream().allMatch(t->captured.contains(t.getTilePos())),"PIPE_NETWORK_OUTSIDE_CAPTURE");
            if(groups.containsKey(net.getUUID()))continue;
            var group=new CompoundTag();group.putString("id",net.getUUID().toString());
            boolean fluid=tile instanceof TileEntityMechanicalPipe;group.putBoolean("fluid",fluid);
            long capacity;AEKey key;long amount;
            if(tile instanceof TileEntityMechanicalPipe p) {
                var n=p.getTransmitter().getTransmitterNetwork();capacity=n.getCapacityAsInt();
                var stack=n.getBuffer();key=stack.isEmpty()?null:AEFluidKey.of(stack);amount=stack.getAmount();
            } else {
                var n=((TileEntityPressurizedTube)tile).getTransmitter().getTransmitterNetwork();capacity=n.getCapacity();
                var stack=MekGasPipeAccess.contents((TileEntityPressurizedTube)tile);
                key=stack==null?null:stack.what();amount=stack==null?0:stack.amount();
                require(key==null || accepts(false,key),"MEK_NON_GAS_PIPE_CONTENT");
            }
            require(capacity>0 && amount>=0 && amount<=capacity,"MEK_PIPE_CAPACITY");
            group.putLong("capacity",capacity);
            var initial=new CompoundTag();initial.putLong("amount",amount);
            if(key!=null)initial.put("key",PlatformData.saveKey(key,be.getLevel()));group.put("initial",initial);
            var nodes=new ListTag();
            for(var t:net.getTransmitters()) {
                var peer=t.getTransmitterTile();
                require(supported(peer) && (peer instanceof TileEntityMechanicalPipe)==fluid,"MEK_PIPE_TYPE_CHANGED");
                var data=t.write(new CompoundTag());
                require(!data.getBoolean("redstone"),"EXTERNAL_PIPE_REDSTONE_CONTROL");
                var node=new CompoundTag();node.putLong("pos",peer.getBlockPos().asLong());
                node.putLong("capacity",((BufferedTransmitter<?,?,?,?>)t).getCapacity());
                node.putLong("pull",peer instanceof TileEntityMechanicalPipe p?p.getTransmitter().tier.getPipePullAmount():((TileEntityPressurizedTube)peer).getTransmitter().tier.getTubePullAmount());
                var faces=new CompoundTag();
                for(var side:Direction.values()) {
                    var mode=t.getConnectionTypeRaw(side);faces.putString(side.getName(),mode.name());
                    var pos=peer.getBlockPos().relative(side);var neighbor=peer.getLevel().getBlockEntity(pos);
                    if(Transmitter.connectionMapContainsSide(t.getAllCurrentConnections(),side))
                        require(captured.contains(pos),"PIPE_CONNECTION_OUTSIDE_CAPTURE");
                    if(mode!=ConnectionType.NONE && captured.contains(pos) && neighbor!=null && !(neighbor instanceof TileEntityTransmitter))
                        require(NativeMachines.buffered(neighbor) || neighbor instanceof appeng.blockentity.networking.CableBusBlockEntity
                                || neighbor.getClass()==appeng.blockentity.misc.InterfaceBlockEntity.class,"UNSUPPORTED_PIPE_ENDPOINT");
                }
                node.put("faces",faces);nodes.add(node);
            }
            group.put("nodes",nodes);groups.put(net.getUUID(),group);
        }
        var result=new ListTag();groups.values().forEach(result::add);return result;
    }
    public static void restoreShare(BlockEntity be,AEKey key,long amount) {
        if(be instanceof TileEntityMechanicalPipe p) {
            var stack=amount==0?net.minecraftforge.fluids.FluidStack.EMPTY:
                    new net.minecraftforge.fluids.FluidStack(((AEFluidKey)key).getFluid(),Math.toIntExact(amount),((AEFluidKey)key).getTag());
            p.getTransmitter().saveShare=stack;p.getTransmitter().buffer.setStack(stack);
        } else if(be instanceof TileEntityPressurizedTube p) {
            MekGasPipeAccess.restoreShare(p,key,amount);
        } else throw new IllegalArgumentException("MEK_PIPE_RESTORE_TYPE");
    }
}
