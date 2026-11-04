package com.syaru.ae2machines.compat.mek;

import com.syaru.ae2machines.world.NativeRegionMove;
import java.util.Set;
import mekanism.common.lib.multiblock.*;
import mekanism.common.tile.prefab.*;
import mekanism.common.tile.transmitter.TileEntityTransmitter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Only fully owned, known native thermal structures/networks can join a coordinated save. */
public final class MekThermalRecovery {
    private MekThermalRecovery(){}
    public static boolean participant(BlockEntity be,ServerLevel world,BlockPos first,BlockPos last,Set<Object> validated){
        if(!MekThermalShells.known(be) || !MekThermalHooks.safe(be.getClass()))return false;
        if(be instanceof TileEntityTransmitter){try{return MekNativeNetworks.capturable(world,be.getBlockPos(),first,last,validated);}catch(IllegalArgumentException unsupported){return false;}}
        if(be instanceof IMultiblock<?> tile)return structure(tile.getMultiblock(),first,last,validated);
        if(be instanceof TileEntityInternalMultiblock tile)return structure(tile.getMultiblock(),first,last,validated);
        if(be instanceof TileEntityStructuralMultiblock tile){
            if(tile.getStructureMap().isEmpty())return false;
            for(var s:tile.getStructureMap().values())if(!s.isValid() || !structure(s.getMultiblockData(),first,last,validated))return false;
            return true;
        }
        return false;
    }
    private static boolean structure(MultiblockData data,BlockPos first,BlockPos last,Set<Object> validated){
        if(!MekThermalShells.data(data) || data.inventoryID==null)return false;
        if(validated.contains(data))return true;
        // Native turbine caches do not persist the input-rate history used by dumping.
        if(data.getClass().getName().endsWith("TurbineMultiblockData")){
            try{if(!((Enum<?>)data.getClass().getField("dumpMode").get(data)).name().equals("IDLE"))return false;}
            catch(ReflectiveOperationException changed){return false;}
        }
        if(!NativeRegionMove.contains(data.getMinPos(),first,last) || !NativeRegionMove.contains(data.getMaxPos(),first,last))return false;
        for(var p:data.locations)if(!NativeRegionMove.contains(p,first,last))return false;
        for(var p:data.internalLocations)if(!NativeRegionMove.contains(p,first,last))return false;
        validated.add(data);return true;
    }
}
