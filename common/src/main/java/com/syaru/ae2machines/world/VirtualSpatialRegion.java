package com.syaru.ae2machines.world;

import appeng.blockentity.spatial.SpatialPylonBlockEntity;
import com.syaru.ae2machines.platform.MachinesMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import static com.syaru.ae2machines.world.NativeFactoryCompiler.require;

/** AE2 owns region formation; like its spatial cell, capture only the interior. */
public record VirtualSpatialRegion(BlockPos first,BlockPos last) {
    public static VirtualSpatialRegion read(ServerLevel level,BlockPos marker) {
        require(level.getBlockState(marker).is(MachinesMod.MARKER.get())
                && level.getBlockEntity(marker) instanceof SpatialPylonBlockEntity,"VIRTUAL_SPATIAL_PYLON_REQUIRED");
        var pylon=(SpatialPylonBlockEntity)level.getBlockEntity(marker);
        require(pylon.getMainNode().isActive(),"SPATIAL_PYLON_OFFLINE");
        return fromGrid(level,pylon.getMainNode().getGrid());
    }
    public static VirtualSpatialRegion fromGrid(ServerLevel level,appeng.api.networking.IGrid grid) {
        require(grid!=null,"SPATIAL_PYLON_OFFLINE");
        require(grid.getMachines(SpatialPylonBlockEntity.class).stream().anyMatch(p->p.getBlockState().is(MachinesMod.MARKER.get())),"VIRTUAL_SPATIAL_PYLON_REQUIRED");
        var service=grid.getSpatialService();
        require(service.hasRegion() && service.isValidRegion() && service.getLevel()==level,"SPATIAL_REGION_INVALID");
        return new VirtualSpatialRegion(service.getMin().offset(1,1,1),service.getMax().offset(-1,-1,-1));
    }
}
