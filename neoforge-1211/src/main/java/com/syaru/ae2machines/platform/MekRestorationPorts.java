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

/** NeoForge chemical codecs preserve native identities independently of optional addons. */
public final class MekRestorationPorts {
    private MekRestorationPorts(){}
    public static void cached(Ports ports,BlockEntity owner,mekanism.common.lib.multiblock.MultiblockCache<?> cache){
        fluids(ports,owner,cache.getFluidTanks(null));chemicals(ports,owner,cache.getChemicalTanks(null));
        energies(ports,owner,cache.getEnergyContainers(null));MekRestorationContainers.slots(ports,owner,cache.getInventorySlots(null));MekRestorationContainers.heat(ports,owner,cache.getHeatCapacitors(null));
    }
    public static void collect(ServerLevel level,BlockPos min,BlockPos max,Ports ports){
        var structures=Collections.newSetFromMap(new IdentityHashMap<>());
        for(var be:NativeRegionMove.blockEntities(level,min,max)){
            if(be instanceof IMultiblock<?> tile && tile.getMultiblock().isFormed() && structures.add(tile.getMultiblock())){
                var data=tile.getMultiblock();MekStructureParallel.enrollExtraFluids(data);
                fluids(ports,be,data.getFluidTanks(null));chemicals(ports,be,data.getChemicalTanks(null));
                energies(ports,be,data.getEnergyContainers(null));MekRestorationContainers.slots(ports,be,data.getInventorySlots(null));
                MekRestorationContainers.heat(ports,be,data.getHeatCapacitors(null));MekRestorationContainers.plasma(ports,be,data);
            }else if(be instanceof TileEntityConfigurableMachine machine && !(be instanceof IMultiblock<?>)){
                fluids(ports,be,machine.getFluidTanks(null));chemicals(ports,be,machine.getChemicalTanks(null));
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
                    if(network instanceof ChemicalNetwork chemical){chemical(ports,be,"network:chemical",0,chemical.chemicalTank,total);ports.alias(be,"buffer:chemical",chemical.chemicalTank);ports.alias(be,"network:chemical",chemical.chemicalTank);}
                    if(network instanceof EnergyNetwork energy){energy(ports,be,"network:energy",0,energy.energyContainer,total);ports.alias(be,"buffer:energy",energy.energyContainer);ports.alias(be,"network:energy",energy.energyContainer);}
                }
                if(!t.hasTransmitterNetwork() && t instanceof MechanicalPipe pipe)fluid(ports,be,"buffer:fluid",0,pipe.buffer,capacity);
                if(!t.hasTransmitterNetwork() && t instanceof UniversalCable cable)energy(ports,be,"buffer:energy",0,cable.buffer,capacity);
                if(!t.hasTransmitterNetwork() && t instanceof PressurizedTube tube)chemical(ports,be,"buffer:chemical",0,tube.chemicalTank,capacity);
                if(t instanceof ThermodynamicConductor heat)MekRestorationContainers.heat(ports,be,List.of(heat.buffer));
            }
        }
    }
    private static void fluids(Ports ports,BlockEntity be,List<IExtendedFluidTank> tanks){for(int i=0;i<tanks.size();i++){
        var tank=tanks.get(i);fluid(ports,be,"fluid",i,tank,BigInteger.valueOf(tank instanceof MekVirtualTank v?v.machines$nativeCapacity():tank.getCapacity()));
    }}
    private static void fluid(Ports ports,BlockEntity be,String kind,int index,IExtendedFluidTank tank,BigInteger capacity){
        ports.amount(be,kind,index,tank,()->MekWideFluidTank.amount(tank),()->capacity,
                ()->PlatformData.saveKey(AEFluidKey.of(tank.getFluid()),be.getLevel()),(payload,amount)->{
                    if(amount.signum()==0)tank.setEmpty();else MekFluidAccess.set(tank,((AEFluidKey)PlatformData.loadKey(payload,be.getLevel())).toStack(1),amount);
                });
    }
    private static void energies(Ports ports,BlockEntity be,List<IEnergyContainer> containers){for(int i=0;i<containers.size();i++){
        var energy=containers.get(i);var capacity=energy instanceof MekWorldEnergyAccess v?v.machines$nativeCapacity():energy instanceof MekStructureEnergy v?v.machines$structureBaseEnergy():BigInteger.valueOf(energy.getMaxEnergy()).multiply(BigInteger.valueOf(10000));
        energy(ports,be,"energy",i,energy,capacity);
    }}
    private static void energy(Ports ports,BlockEntity be,String kind,int index,IEnergyContainer container,BigInteger capacity){
        ports.amount(be,kind,index,container,()->BigInteger.valueOf(container.getEnergy()).multiply(BigInteger.valueOf(10000)),()->capacity,CompoundTag::new,
                (payload,amount)->{var units=amount.divideAndRemainder(BigInteger.valueOf(10000));if(units[1].signum()!=0)throw new IllegalArgumentException("NONINTEGRAL_NATIVE_JOULES");container.setEnergy(units[0].longValueExact());});
    }
    private static void chemicals(Ports ports,BlockEntity be,List<IChemicalTank> tanks){for(int i=0;i<tanks.size();i++){
        var tank=tanks.get(i);chemical(ports,be,"chemical",i,tank,BigInteger.valueOf(tank instanceof MekVirtualTank v?v.machines$nativeCapacity():tank.getCapacity()));
    }}
    private static void chemical(Ports ports,BlockEntity be,String kind,int index,IChemicalTank tank,BigInteger capacity){
        ports.amount(be,kind,index,tank,()->BigInteger.valueOf(tank.getStored()),()->capacity,
                ()->(CompoundTag)tank.getStack().copyWithAmount(1).save(be.getLevel().registryAccess()),(payload,amount)->{
                    if(amount.signum()==0){tank.setEmpty();return;}
                    var stack=ChemicalStack.parse(be.getLevel().registryAccess(),payload).orElseThrow(()->new IllegalArgumentException("RESTORATION_CHEMICAL_UNAVAILABLE"));
                    stack.setAmount(amount.longValueExact());tank.setStackUnchecked(stack);
                });
    }
}
