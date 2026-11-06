package com.syaru.ae2machines.world;

import appeng.blockentity.networking.CableBusBlockEntity;
import com.syaru.ae2machines.compat.mek.MekThermalHooks;
import com.syaru.ae2machines.compat.mek.MekThermalShells;
import com.syaru.ae2machines.runtime.NativeWorldSession;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.*;
import mekanism.common.lib.multiblock.IMultiblock;
import mekanism.common.lib.multiblock.MultiblockData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Resident equipment only. Ticker order, cable parts, master/owner and heat bounds remain live. */
final class NativeThermalLayout {
    final NativeWorldSession session;
    final BlockPos first,last;
    private final Long2ObjectOpenHashMap<BlockEntity> residents;
    private final CableBusBlockEntity[] cables;
    private final Class<?>[] audited;
    private final Class<?>[] nativeWork;
    private final IMultiblock<?>[] structures;
    private final String refusal;

    NativeThermalLayout(NativeWorldSession session) {
        this.session=session;first=session.previewFirst();last=session.previewLast();
        var blocks=session.nativeBlocks();
        residents=new Long2ObjectOpenHashMap<>(blocks.size());
        var cableList=new ArrayList<CableBusBlockEntity>();
        var types=new LinkedHashSet<Class<?>>();
        var retained=new LinkedHashSet<Class<?>>();
        var tiles=new ArrayList<IMultiblock<?>>();
        String unknown=null;
        for(var be:blocks){
            residents.put(be.getBlockPos().asLong(),be);
            if(be instanceof CableBusBlockEntity cable){cableList.add(cable);continue;}
            if(NativeTemporalScheduler.passiveContract(be))continue;
            if(MekThermalShells.nativeWork(be)){retained.add(be.getClass());continue;}
            if(!MekThermalShells.known(be)){
                if(unknown==null)unknown="UNDECLARED_BE:"+be.getClass().getName();
                continue;
            }
            types.add(be.getClass());
            if(be instanceof IMultiblock<?> tile)tiles.add(tile);
        }
        cables=cableList.toArray(CableBusBlockEntity[]::new);
        audited=types.toArray(Class<?>[]::new);
        nativeWork=retained.toArray(Class<?>[]::new);
        structures=tiles.toArray(IMultiblock<?>[]::new);
        refusal=unknown;
    }

    BlockEntity resident(BlockPos pos){return residents.get(pos.asLong());}

    String boundary(int count){
        if(refusal!=null)return refusal;
        for(var cable:cables)if(!NativeTemporalScheduler.passiveContract(cable))return "UNDECLARED_BE:"+cable.getClass().getName();
        for(var type:audited)if(!MekThermalHooks.safe(type))return "UNDECLARED_MIXIN:"+type.getName()+":"+MekThermalHooks.reason(type);
        for(var type:nativeWork)if(!MekThermalHooks.retainedWorkSafe(type))return "UNDECLARED_NATIVE_OBSERVER:"+type.getName()+":"+MekThermalHooks.reason(type);
        if(structures.length==0)return "NO_THERMAL_DATA";
        var seen=new IdentityHashMap<MultiblockData,Boolean>();
        for(var tile:structures){
            var owner=tile.getMultiblock();
            if(owner==null)return "UNSTABLE_DATA:null";
            if(seen.put(owner,Boolean.TRUE)==null && !MekThermalShells.bounded(owner,count))return "UNSTABLE_DATA:"+owner.getClass().getName();
        }
        return null;
    }
}
