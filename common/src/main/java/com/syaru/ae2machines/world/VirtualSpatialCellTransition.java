package com.syaru.ae2machines.world;

import appeng.blockentity.spatial.SpatialIOPortBlockEntity;
import com.syaru.ae2machines.platform.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import static com.syaru.ae2machines.world.NativeFactoryCompiler.require;

/** Only the virtual cell's storage backend differs; AE2 owns acceptance, energy and slot handoff. */
public final class VirtualSpatialCellTransition {
    private VirtualSpatialCellTransition(){}
    public static boolean move(SpatialIOPortBlockEntity port,ItemStack cell,ServerLevel level,BlockPos min,BlockPos max,int playerId){
        if(!level.getServer().isSameThread())throw new IllegalStateException("Spatial transfer requires server thread");
        try{
            var owner=appeng.api.features.IPlayerRegistry.getMapping(level).getProfileId(playerId);
            require(owner!=null,"SPATIAL_PORT_OWNER_REQUIRED");
            var actor=PlatformData.spatialActor(level,owner);
            var data=PlatformData.cellData(cell);
            if(data.hasUUID("factory")){
                var id=data.getUUID("factory");var journal=FactoryJournal.open(level,id);var record=journal.read();
                require(record.getCompound("blueprint").getUUID("player").equals(owner),"FACTORY_OWNER_MISMATCH");
                if(record.getString("state").equals("RESTORED"))RestorationRemainder.recover(level,actor,journal);
                else FactoryWorld.restore(level,actor,id);
                RestorationRemainder.cellData(data,FactoryJournal.open(level,id).read());PlatformData.setCellData(cell,data);port.setChanged();
                FactoryWorld.checkpointCellHolder(level,port.getBlockPos());
            }else{
                var region=VirtualSpatialRegion.fromGrid(level,port.getMainNode().getGrid());
                require(region.first().equals(min.offset(1,1,1)) && region.last().equals(max.offset(-1,-1,-1)),"SPATIAL_REGION_CHANGED");
                require(!NativeRegionMove.contains(port.getBlockPos(),region.first(),region.last()),"ENCODER_MUST_REMAIN_OUTSIDE_CELL");
                FactoryWorld.capture(level,actor,region.first(),region.last(),id->{
                    data.remove("corner");data.putUUID("factory",id);VirtualCellMetadata.describe(data,region.first(),region.last());PlatformData.setCellData(cell,data);port.setChanged();
                    FactoryWorld.checkpointCellHolder(level,port.getBlockPos());
                });
            }
            return true;
        }catch(RuntimeException failure){
            if(!(failure instanceof IllegalArgumentException))org.slf4j.LoggerFactory.getLogger(VirtualSpatialCellTransition.class).error("Virtual spatial transfer failed; cell identity retained",failure);
            var player=appeng.api.features.IPlayerRegistry.getConnected(level.getServer(),playerId);
            if(player!=null)player.displayClientMessage(net.minecraft.network.chat.Component.literal(java.util.Objects.toString(failure.getMessage(),"FACTORY_OPERATION_FAILED")),true);
            return false;
        }
    }
}
