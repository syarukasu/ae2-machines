package com.syaru.ae2machines.platform;

import com.syaru.ae2machines.compat.mek.*;
import java.math.BigInteger;
import java.util.*;
import mekanism.common.content.network.*;
import mekanism.common.content.network.transmitter.*;
import mekanism.common.lib.transmitter.DynamicBufferedNetwork;
import mekanism.common.tile.transmitter.TileEntityTransmitter;

public final class MekTransportStorage {
    private MekTransportStorage() {}
    public static void refreshOrphanShare(TileEntityTransmitter tile){
        var t=tile.getTransmitter();if(t.hasTransmitterNetwork())return;
        if(t instanceof MechanicalPipe pipe)pipe.saveShare=pipe.buffer.getFluid().copy();
        if(t instanceof BoxedPressurizedTube tube)tube.saveShare=tube.getShare().copy();
        if(t instanceof UniversalCable cable)cable.lastWrite=cable.buffer.getEnergy().copy();
    }
    private static List<mekanism.api.chemical.IChemicalTank<?,?>> chemicals(BoxedPressurizedTube tube){return List.of(tube.chemicalTank.getGasTank(),tube.chemicalTank.getInfusionTank(),tube.chemicalTank.getPigmentTank(),tube.chemicalTank.getSlurryTank());}
    public static void enroll(TileEntityTransmitter tile,boolean loaded){
        var t=tile.getTransmitter();
        if(t instanceof BoxedPressurizedTube tube)for(var tank:chemicals(tube))((MekVirtualTank)tank).machines$owner(tile);
        if(t instanceof UniversalCable cable){
            ((MekTransportEnergy)cable.buffer).machines$transportOwner(tile);
            if(loaded && !cable.hasTransmitterNetwork())cable.buffer.setEnergy(cable.lastWrite);
        }
    }
    public static BigInteger base(Transmitter<?,?,?> t){
        if(t instanceof MechanicalPipe pipe)return BigInteger.valueOf(pipe.tier.getPipeCapacity());
        if(t instanceof BoxedPressurizedTube tube)return BigInteger.valueOf(tube.tier.getTubeCapacity());
        if(t instanceof UniversalCable cable)return MekanismAdapter.units(cable.tier.getCableCapacity().toString());
        return BigInteger.ZERO;
    }
    public static BigInteger stored(DynamicBufferedNetwork<?,?,?,?> network){
        if(network instanceof FluidNetwork fluid)return BigInteger.valueOf(fluid.fluidTank.getFluidAmount());
        if(network instanceof BoxedChemicalNetwork gas)return BigInteger.valueOf(gas.getBuffer().getChemicalStack().getAmount());
        if(network instanceof EnergyNetwork energy)return MekanismAdapter.units(energy.energyContainer.getEnergy().toString());
        throw new IllegalArgumentException("Unknown native buffered network");
    }
    public static boolean bufferFits(TileEntityTransmitter tile,BigInteger q){
        var t=tile.getTransmitter();var capacity=base(t).multiply(q);
        if(t instanceof MechanicalPipe pipe)return BigInteger.valueOf(pipe.buffer.getFluidAmount()).compareTo(capacity)<=0;
        if(t instanceof BoxedPressurizedTube tube)return chemicals(tube).stream().allMatch(tank->BigInteger.valueOf(tank.getStored()).compareTo(capacity)<=0);
        return !(t instanceof UniversalCable cable) || MekanismAdapter.units(cable.buffer.getEnergy().toString()).compareTo(capacity)<=0;
    }
}
