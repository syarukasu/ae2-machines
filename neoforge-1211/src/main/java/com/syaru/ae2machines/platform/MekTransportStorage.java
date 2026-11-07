package com.syaru.ae2machines.platform;

import com.syaru.ae2machines.compat.mek.*;
import java.math.BigInteger;
import mekanism.common.content.network.*;
import mekanism.common.content.network.transmitter.*;
import mekanism.common.lib.transmitter.DynamicBufferedNetwork;
import mekanism.common.tile.transmitter.TileEntityTransmitter;

public final class MekTransportStorage {
    private MekTransportStorage() {}
    public static void refreshOrphanShare(TileEntityTransmitter tile){
        var t=tile.getTransmitter();if(t.hasTransmitterNetwork())return;
        if(t instanceof MechanicalPipe pipe){pipe.saveShare=pipe.buffer.getFluid().copy();((MekWideFluidPipe)pipe).machines$fluidShare(MekWideFluidTank.amount(pipe.buffer));}
        if(t instanceof PressurizedTube tube)tube.saveShare=tube.chemicalTank.getStack().copy();
        if(t instanceof UniversalCable cable)cable.lastWrite=cable.buffer.getEnergy();
    }
    public static void enroll(TileEntityTransmitter tile,boolean loaded){
        var t=tile.getTransmitter();
        if(t instanceof PressurizedTube tube)((MekVirtualTank)tube.chemicalTank).machines$owner(tile);
        if(t instanceof UniversalCable cable){
            ((MekTransportEnergy)cable.buffer).machines$transportOwner(tile);
            if(loaded && !cable.hasTransmitterNetwork())cable.buffer.setEnergy(cable.lastWrite);
        }
    }
    public static BigInteger base(Transmitter<?,?,?> t){
        if(t instanceof MechanicalPipe pipe)return BigInteger.valueOf(pipe.tier.getPipeCapacity());
        if(t instanceof PressurizedTube tube)return BigInteger.valueOf(tube.tier.getTubeCapacity());
        if(t instanceof UniversalCable cable)return BigInteger.valueOf(cable.tier.getCableCapacity()).multiply(BigInteger.valueOf(10000));
        return BigInteger.ZERO;
    }
    public static BigInteger stored(DynamicBufferedNetwork<?,?,?,?> network){
        if(network instanceof FluidNetwork fluid)return MekWideFluidTank.amount(fluid.fluidTank);
        if(network instanceof ChemicalNetwork gas)return BigInteger.valueOf(gas.chemicalTank.getStored());
        if(network instanceof EnergyNetwork energy)return BigInteger.valueOf(energy.energyContainer.getEnergy()).multiply(BigInteger.valueOf(10000));
        throw new IllegalArgumentException("Unknown native buffered network");
    }
    public static boolean bufferFits(TileEntityTransmitter tile,BigInteger q){
        var t=tile.getTransmitter();var capacity=base(t).multiply(q);
        if(t instanceof MechanicalPipe pipe)return MekWideFluidTank.amount(pipe.buffer).compareTo(capacity)<=0;
        if(t instanceof PressurizedTube tube)return BigInteger.valueOf(tube.chemicalTank.getStored()).compareTo(capacity)<=0;
        return !(t instanceof UniversalCable cable) || BigInteger.valueOf(cable.buffer.getEnergy()).multiply(BigInteger.valueOf(10000)).compareTo(capacity)<=0;
    }
}
