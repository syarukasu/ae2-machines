package com.syaru.ae2machines.compat.mek;

import com.syaru.ae2machines.platform.MekTransportStorage;
import com.syaru.ae2machines.world.*;
import java.math.BigInteger;
import java.util.*;
import mekanism.common.content.network.transmitter.*;
import mekanism.common.lib.transmitter.DynamicBufferedNetwork;
import mekanism.common.tile.transmitter.TileEntityTransmitter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Scales native transfer windows, not contents, paths or recipient acceptance. */
public final class MekTransportParallel {
    private static final ThreadLocal<Integer> ITEM_PULL=ThreadLocal.withInitial(()->1);
    private MekTransportParallel() {}
    public static boolean supports(BlockEntity block){return block instanceof TileEntityTransmitter tile
            && (tile.getTransmitter() instanceof MechanicalPipe || tile.getTransmitter() instanceof UniversalCable
            || tile.getTransmitter() instanceof LogisticalTransporterBase || tile.getTransmitter() instanceof ThermodynamicConductor
            || tile.getClass().getSimpleName().equals("TileEntityPressurizedTube"));}
    public static int lanes(Object transmitter){
        return transmitter instanceof Transmitter<?,?,?> t?MekWorldParallel.lanes(t.getTransmitterTile()):1;
    }
    public static long scale(long value,int q){return value>Long.MAX_VALUE/q?Long.MAX_VALUE:value*q;}
    public static <T> T itemPull(Object transmitter,java.util.function.Supplier<T> operation){
        int q=lanes(transmitter);if(q==1)return operation.get();
        int old=ITEM_PULL.get();ITEM_PULL.set(q);try{return operation.get();}finally{if(old==1)ITEM_PULL.remove();else ITEM_PULL.set(old);}
    }
    public static int itemStackLimit(BlockEntity source,int original){
        return (int)Math.min(Integer.MAX_VALUE,scale(original,Math.min(ITEM_PULL.get(),MekWorldParallel.lanes(source))));
    }
    public static void prepare(BlockEntity block,boolean loaded){
        if(!supports(block) || !(block.getLevel() instanceof ServerLevel level) || !NativeCellWorlds.owns(level))return;
        var tile=(TileEntityTransmitter)block;var t=tile.getTransmitter();
        if(t instanceof MechanicalPipe pipe)((MekVirtualTank)pipe.buffer).machines$owner(tile);
        if(t instanceof ThermodynamicConductor heat)((MekStructureHeat)heat.buffer).machines$structureOwner(tile);
        MekTransportStorage.enroll(tile,loaded);
    }
    public static void refresh(List<BlockEntity> blocks){
        var seen=Collections.newSetFromMap(new IdentityHashMap<DynamicBufferedNetwork<?,?,?,?>,Boolean>());
        for(var block:blocks)if(supports(block)){
            prepare(block,false);var network=((TileEntityTransmitter)block).getTransmitter().getTransmitterNetwork();
            if(network instanceof DynamicBufferedNetwork<?,?,?,?> buffered && seen.add(buffered))buffered.updateCapacity();
        }
    }
    public static boolean fits(BlockEntity block,BigInteger q){
        if(!supports(block))return true;
        var tile=(TileEntityTransmitter)block;
        if(!MekTransportStorage.bufferFits(tile,q))return false;
        var network=tile.getTransmitter().getTransmitterNetwork();
        if(!(network instanceof DynamicBufferedNetwork<?,?,?,?> buffered))return true;
        var capacity=BigInteger.ZERO;
        for(var member:buffered.getTransmitters())capacity=capacity.add(MekTransportStorage.base(member));
        return MekTransportStorage.stored(buffered).compareTo(capacity.multiply(q))<=0;
    }
}
