package com.syaru.ae2machines.compat.mek;

import com.syaru.ae2machines.world.NativeCellWorlds;
import com.syaru.ae2machines.world.NativeRegionMove;
import java.util.*;
import mekanism.common.lib.transmitter.DynamicNetwork;
import mekanism.common.tile.transmitter.TileEntityTransmitter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** Topology changes maintain the index; native transport runs once per owned level tick. */
public final class MekNativeNetworks {
    private static final Set<String> TRANSMITTERS=Set.of("TileEntityDiversionTransporter","TileEntityLogisticalTransporter",
            "TileEntityRestrictiveTransporter","TileEntityMechanicalPipe","TileEntityPressurizedTube",
            "TileEntityThermodynamicConductor","TileEntityUniversalCable");
    private static final class Index {
        final Set<DynamicNetwork<?, ?, ?>> members=Collections.newSetFromMap(new IdentityHashMap<>());
        List<DynamicNetwork<?, ?, ?>> snapshot=List.of();
        void changed(){snapshot=List.copyOf(members);}
    }
    private static final Map<Level, Index> NETWORKS = new IdentityHashMap<>();
    private MekNativeNetworks() {}

    public static void committed(DynamicNetwork<?, ?, ?> network) {
        var level = network.getWorld();
        if (level != null && NativeCellWorlds.owns(level) && !level.isClientSide) {
            var index=NETWORKS.computeIfAbsent(level,ignored->new Index());
            if(index.members.add(network))index.changed();
        }
    }
    public static void removed(DynamicNetwork<?, ?, ?> network) {
        var networks = NETWORKS.get(network.getWorld());
        if (networks != null) {
            if(networks.members.remove(network))networks.changed();
            if (networks.members.isEmpty()) NETWORKS.remove(network.getWorld());
        }
    }
    public static void globalTick(DynamicNetwork<?, ?, ?> network) {
        if (network.getWorld() == null || !NativeCellWorlds.owns(network.getWorld())) network.onUpdate();
    }
    public static void tick(ServerLevel world) {
        var networks = NETWORKS.get(world);
        if (networks == null) return;
        // Native callbacks may split/deregister a network while it is updating.
        for (var network : networks.snapshot)
            if (networks.members.contains(network) && !network.isEmpty()) network.onUpdate();
    }
    public static boolean hasNetworks(ServerLevel world){
        var index=NETWORKS.get(world);return index!=null && !index.members.isEmpty();
    }
    public static void unload(Level world) { NETWORKS.remove(world); }
    public static void stopped(net.minecraft.server.MinecraftServer server) {
        NETWORKS.keySet().removeIf(level -> level instanceof ServerLevel world && world.getServer() == server);
    }
    public static boolean capturable(ServerLevel level, BlockPos pos, BlockPos min, BlockPos max,Set<Object> validated) {
        if (!(level.getBlockEntity(pos) instanceof TileEntityTransmitter tile)) return false;
        if(!known(tile))throw new IllegalArgumentException("MEK_TRANSMITTER_TYPE_UNSUPPORTED: "+tile.getClass().getName());
        var transmitter = tile.getTransmitter();
        var network = transmitter.getTransmitterNetwork();
        if (network != null && validated.add(network)) for (var member : network.getTransmitters())
            if (!known(member.getTransmitterTile()) || member.getTransmitterTile().getLevel() != level || !NativeRegionMove.contains(member.getTransmitterTile().getBlockPos(), min, max))
                throw new IllegalArgumentException("MEK_NETWORK_OUTSIDE_CAPTURE: " + member.getTransmitterTile().getBlockPos());
        int connections = Byte.toUnsignedInt(transmitter.getAllCurrentConnections());
        for (var side : Direction.values())
            if ((connections & (1 << side.ordinal())) != 0 && !NativeRegionMove.contains(pos.relative(side), min, max))
                throw new IllegalArgumentException("MEK_NETWORK_ENDPOINT_OUTSIDE_CAPTURE: " + pos.relative(side));
        if (transmitter instanceof mekanism.common.content.network.transmitter.ThermodynamicConductor heat)
            MekPendingHeat.enroll(heat.buffer);
        return true;
    }
    private static boolean known(TileEntityTransmitter tile){return tile.getClass().getPackageName().equals("mekanism.common.tile.transmitter") && TRANSMITTERS.contains(tile.getClass().getSimpleName());}
}
