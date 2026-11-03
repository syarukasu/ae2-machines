package com.syaru.ae2machines.platform;

import appeng.api.stacks.AEFluidKey;
import com.syaru.ae2machines.compat.mek.*;
import com.syaru.ae2machines.world.NativeRegionMove;
import java.math.*;
import java.util.*;
import mekanism.api.chemical.*;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.common.content.network.*;
import mekanism.common.content.network.transmitter.*;
import mekanism.common.lib.multiblock.IMultiblock;
import mekanism.common.lib.transmitter.DynamicBufferedNetwork;
import mekanism.common.tile.prefab.TileEntityConfigurableMachine;
import mekanism.common.tile.transmitter.TileEntityTransmitter;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import static com.syaru.ae2machines.compat.mek.MekRestorationStorage.*;

/** Native stacks, including chemicals without Applied Mekanistics, retain their own codecs. */
public final class MekRestorationPorts {
    private MekRestorationPorts(){}
    public static void cached(Ports ports,BlockEntity owner,mekanism.common.lib.multiblock.MultiblockCache<?> cache){
        fluids(ports,owner,cache.getFluidTanks(null));chemicals(ports,owner,cache.getGasTanks(null),"gas");
        chemicals(ports,owner,cache.getInfusionTanks(null),"infusion");chemicals(ports,owner,cache.getPigmentTanks(null),"pigment");chemicals(ports,owner,cache.getSlurryTanks(null),"slurry");
        energies(ports,owner,cache.getEnergyContainers(null));MekRestorationContainers.slots(ports,owner,cache.getInventorySlots(null));MekRestorationContainers.heat(ports,owner,cache.getHeatCapacitors(null));
    }
    public static void collect(ServerLevel level,BlockPos min,BlockPos max,Ports ports){
        var structures=Collections.newSetFromMap(new IdentityHashMap<>());
        for(var be:NativeRegionMove.blockEntities(level,min,max)){
            if(be instanceof IMultiblock<?> tile && tile.getMultiblock().isFormed() && structures.add(tile.getMultiblock())){
                var data=tile.getMultiblock();MekStructureParallel.enrollExtraFluids(data);
                fluids(ports,be,data.getFluidTanks(null));chemicals(ports,be,data.getGasTanks(null),"gas");
                chemicals(ports,be,data.getInfusionTanks(null),"infusion");chemicals(ports,be,data.getPigmentTanks(null),"pigment");chemicals(ports,be,data.getSlurryTanks(null),"slurry");
                energies(ports,be,data.getEnergyContainers(null));MekRestorationContainers.slots(ports,be,data.getInventorySlots(null));
                MekRestorationContainers.heat(ports,be,data.getHeatCapacitors(null));MekRestorationContainers.plasma(ports,be,data);
            }else if(be instanceof TileEntityConfigurableMachine machine && !(be instanceof IMultiblock<?>)){
                fluids(ports,be,machine.getFluidTanks(null));chemicals(ports,be,machine.getGasTanks(null),"gas");
                chemicals(ports,be,machine.getInfusionTanks(null),"infusion");chemicals(ports,be,machine.getPigmentTanks(null),"pigment");chemicals(ports,be,machine.getSlurryTanks(null),"slurry");
                energies(ports,be,machine.getEnergyContainers(null));MekRestorationContainers.slots(ports,be,machine.getInventorySlots(null));
                MekRestorationContainers.heat(ports,be,machine.getHeatCapacitors(null));
            }
            if(be instanceof TileEntityTransmitter tile){
                var t=tile.getTransmitter();var capacity=MekTransportStorage.base(t);
                if(t.getTransmitterNetwork() instanceof DynamicBufferedNetwork<?,?,?,?> network){
                    var total=BigInteger.ZERO;
                    for(var member:network.getTransmitters()){
                        if(!NativeRegionMove.contains(member.getTransmitterTile().getBlockPos(),min,max))throw new IllegalArgumentException("RESTORE_TRANSMITTER_NETWORK_CROSSES_REGION");
                        total=total.add(MekTransportStorage.base(member));
                    }
                    if(network instanceof FluidNetwork fluid){fluid(ports,be,"network:fluid",0,fluid.fluidTank,total);ports.alias(be,"buffer:fluid",fluid.fluidTank);ports.alias(be,"network:fluid",fluid.fluidTank);}
                    if(network instanceof BoxedChemicalNetwork chemical){
                        chemical(ports,be,"network:gas",0,chemical.getGasTank(),total);chemical(ports,be,"network:infusion",0,chemical.getInfusionTank(),total);
                        chemical(ports,be,"network:pigment",0,chemical.getPigmentTank(),total);chemical(ports,be,"network:slurry",0,chemical.getSlurryTank(),total);
                        ports.alias(be,"buffer:gas",chemical.getGasTank());ports.alias(be,"network:gas",chemical.getGasTank());
                        ports.alias(be,"buffer:infusion",chemical.getInfusionTank());ports.alias(be,"network:infusion",chemical.getInfusionTank());
                        ports.alias(be,"buffer:pigment",chemical.getPigmentTank());ports.alias(be,"network:pigment",chemical.getPigmentTank());
                        ports.alias(be,"buffer:slurry",chemical.getSlurryTank());ports.alias(be,"network:slurry",chemical.getSlurryTank());
                    }
                    if(network instanceof EnergyNetwork energy){energy(ports,be,"network:energy",0,energy.energyContainer,total);ports.alias(be,"buffer:energy",energy.energyContainer);ports.alias(be,"network:energy",energy.energyContainer);}
                }
                if(!t.hasTransmitterNetwork() && t instanceof MechanicalPipe pipe)fluid(ports,be,"buffer:fluid",0,pipe.buffer,capacity);
                if(!t.hasTransmitterNetwork() && t instanceof UniversalCable cable)energy(ports,be,"buffer:energy",0,cable.buffer,capacity);
                if(!t.hasTransmitterNetwork() && t instanceof BoxedPressurizedTube tube){
                    chemical(ports,be,"buffer:gas",0,tube.chemicalTank.getGasTank(),capacity);chemical(ports,be,"buffer:infusion",0,tube.chemicalTank.getInfusionTank(),capacity);
                    chemical(ports,be,"buffer:pigment",0,tube.chemicalTank.getPigmentTank(),capacity);chemical(ports,be,"buffer:slurry",0,tube.chemicalTank.getSlurryTank(),capacity);
                }
                if(t instanceof ThermodynamicConductor heat)MekRestorationContainers.heat(ports,be,List.of(heat.buffer));
            }
        }
    }
    private static void fluids(Ports ports,BlockEntity be,List<IExtendedFluidTank> tanks){for(int i=0;i<tanks.size();i++){
        var tank=tanks.get(i);fluid(ports,be,"fluid",i,tank,BigInteger.valueOf(tank instanceof MekVirtualTank v?v.machines$nativeCapacity():tank.getCapacity()));
    }}
    private static void fluid(Ports ports,BlockEntity be,String kind,int index,IExtendedFluidTank tank,BigInteger capacity){
        ports.amount(be,kind,index,tank,()->BigInteger.valueOf(tank.getFluidAmount()),()->capacity,
                ()->PlatformData.saveKey(AEFluidKey.of(tank.getFluid()),be.getLevel()),(payload,amount)->{
                    if(amount.signum()==0)tank.setEmpty();else{var key=(AEFluidKey)PlatformData.loadKey(payload,be.getLevel());tank.setStackUnchecked(new net.minecraftforge.fluids.FluidStack(key.getFluid(),amount.intValueExact(),key.getTag()));}
                });
    }
    private static void energies(Ports ports,BlockEntity be,List<IEnergyContainer> containers){for(int i=0;i<containers.size();i++){
        var energy=containers.get(i);var capacity=energy instanceof MekWorldEnergyAccess v?v.machines$nativeCapacity():energy instanceof MekStructureEnergy v?v.machines$structureBaseEnergy():MekanismAdapter.units(energy.getMaxEnergy().toString());
        energy(ports,be,"energy",i,energy,capacity);
    }}
    private static void energy(Ports ports,BlockEntity be,String kind,int index,IEnergyContainer container,BigInteger capacity){
        ports.amount(be,kind,index,container,()->MekanismAdapter.units(container.getEnergy().toString()),()->capacity,CompoundTag::new,
                (payload,amount)->container.setEnergy(mekanism.api.math.FloatingLong.parseFloatingLong(new BigDecimal(amount).movePointLeft(4).toPlainString())));
    }
    private static void chemicals(Ports ports,BlockEntity be,List<? extends IChemicalTank<?,?>> tanks,String kind){for(int i=0;i<tanks.size();i++){
        var tank=tanks.get(i);chemical(ports,be,kind,i,tank,BigInteger.valueOf(tank instanceof MekVirtualTank v?v.machines$nativeCapacity():tank.getCapacity()));
    }}
    @SuppressWarnings({"rawtypes","unchecked"})
    private static void chemical(Ports ports,BlockEntity be,String kind,int index,IChemicalTank tank,BigInteger capacity){
        ports.amount(be,kind,index,tank,()->BigInteger.valueOf(tank.getStored()),()->capacity,
                ()->tank.createStack(tank.getStack(),1).write(new CompoundTag()),(payload,amount)->{
                    if(amount.signum()==0){tank.setEmpty();return;}
                    var tag=payload.copy();tag.putLong("amount",amount.longValueExact());ChemicalStack stack;
                    if(tank.getEmptyStack() instanceof mekanism.api.chemical.gas.GasStack)stack=mekanism.api.chemical.gas.GasStack.readFromNBT(tag);
                    else if(tank.getEmptyStack() instanceof mekanism.api.chemical.infuse.InfusionStack)stack=mekanism.api.chemical.infuse.InfusionStack.readFromNBT(tag);
                    else if(tank.getEmptyStack() instanceof mekanism.api.chemical.pigment.PigmentStack)stack=mekanism.api.chemical.pigment.PigmentStack.readFromNBT(tag);
                    else if(tank.getEmptyStack() instanceof mekanism.api.chemical.slurry.SlurryStack)stack=mekanism.api.chemical.slurry.SlurryStack.readFromNBT(tag);
                    else throw new IllegalArgumentException("UNKNOWN_NATIVE_CHEMICAL_CONTAINER");
                    if(stack.isEmpty())throw new IllegalArgumentException("RESTORATION_CHEMICAL_UNAVAILABLE");tank.setStackUnchecked(stack);
                });
    }
}
