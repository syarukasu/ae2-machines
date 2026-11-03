package com.syaru.ae2machines.platform;

import appeng.api.stacks.*;
import com.syaru.ae2machines.runtime.*;
import mekanism.api.*;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.chemical.gas.IGasTank;
import mekanism.api.chemical.gas.GasStack;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.tile.machine.*;
import mekanism.common.tile.prefab.TileEntityConfigurableMachine;
import mekanism.common.lib.transmitter.TransmissionType;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.*;

public final class MekMaterialAccess {
    private MekMaterialAccess() {}
    public static BufferedMachine create(BlockEntity be,Runnable changed) {
        var m=(TileEntityConfigurableMachine)be;
        var ports=new ArrayList<MaterialBufferedMachine.Port>();
        if(m instanceof TileEntityChemicalOxidizer c) {
            var slot=m.getInventorySlots(null).stream().filter(s->s instanceof InputInventorySlot).findFirst().orElseThrow();
            ports.add(item(m,slot));ports.add(chemical(m,c.gasTank,false));
        } else if(m instanceof TileEntityChemicalInfuser c) {
            ports.add(chemical(m,c.leftTank,true));ports.add(chemical(m,c.rightTank,true));ports.add(chemical(m,c.centerTank,false));
        } else if(m instanceof TileEntityRotaryCondensentrator c) {
            boolean fluidInput=c.mode;
            ports.add(fluid(m,c.fluidTank,fluidInput));ports.add(chemical(m,c.gasTank,!fluidInput));
        } else throw new IllegalArgumentException("UNSUPPORTED_CHEMICAL_BUFFER");
        return new MaterialBufferedMachine(ports,changed,be.getBlockState().getBlock().getName(),
                inputs->MekChemicalAdapter.discover(be,inputs).map(MachineProfile::recipe));
    }
    private static Action action(boolean simulate){return simulate?Action.SIMULATE:Action.EXECUTE;}
    private static AutomationType automation(boolean internal){return internal?AutomationType.INTERNAL:AutomationType.EXTERNAL;}
    private static boolean ejecting(TileEntityConfigurableMachine m,TransmissionType type) {
        var config=m.getConfig().getConfig(type);return config!=null && config.isEjecting();
    }
    private static MaterialBufferedMachine.Port item(TileEntityConfigurableMachine m,IInventorySlot slot) {
        return new MaterialBufferedMachine.Port() {
            public boolean input(){return true;}
            public boolean visible(Direction side){return side!=null && m.getInventorySlots(side).contains(slot);}
            public boolean ejecting(){return false;}
            public AEKey key(){return AEItemKey.of(slot.getStack());}
            public long amount(){return slot.getCount();}
            public long insert(AEKey key,long n,boolean simulate,boolean internal) {
                if(!(key instanceof AEItemKey item))return 0;
                int offer=(int)Math.min(Integer.MAX_VALUE,n);
                return offer-slot.insertItem(item.toStack(offer),action(simulate),automation(internal)).getCount();
            }
            public long extract(AEKey key,long n,boolean simulate,boolean internal) {
                if(!(key instanceof AEItemKey item) || !item.matches(slot.getStack()))return 0;
                return slot.extractItem((int)Math.min(Integer.MAX_VALUE,n),action(simulate),automation(internal)).getCount();
            }
        };
    }
    private static MaterialBufferedMachine.Port fluid(TileEntityConfigurableMachine m,IExtendedFluidTank tank,boolean input) {
        return new MaterialBufferedMachine.Port() {
            public boolean input(){return input;}
            public boolean visible(Direction side){return side!=null && m.getFluidTanks(side).contains(tank);}
            public boolean ejecting(){return MekMaterialAccess.ejecting(m,TransmissionType.FLUID);}
            public AEKey key(){return tank.isEmpty()?null:AEFluidKey.of(tank.getFluid());}
            public long amount(){return tank.getFluidAmount();}
            public long insert(AEKey key,long n,boolean simulate,boolean internal) {
                if(!(key instanceof AEFluidKey fluid))return 0;
                int offer=(int)Math.min(Integer.MAX_VALUE,n);
                var stack=new net.minecraftforge.fluids.FluidStack(fluid.getFluid(),offer,fluid.getTag());
                return offer-tank.insert(stack,action(simulate),automation(internal)).getAmount();
            }
            public long extract(AEKey key,long n,boolean simulate,boolean internal) {
                if(!key.equals(key()))return 0;
                // Reserving the input for owned native work is not external tank extraction.
                if(internal && input)return tank.shrinkStack((int)Math.min(Integer.MAX_VALUE,n),action(simulate));
                return tank.extract((int)Math.min(Integer.MAX_VALUE,n),action(simulate),automation(internal)).getAmount();
            }
        };
    }
    private static MaterialBufferedMachine.Port chemical(TileEntityConfigurableMachine m,IGasTank tank,boolean input) {
        return new MaterialBufferedMachine.Port() {
            public boolean input(){return input;}
            public boolean visible(Direction side){return side!=null && m.getGasTanks(side).contains(tank);}
            public boolean ejecting(){return MekMaterialAccess.ejecting(m,TransmissionType.GAS);}
            public AEKey key(){return tank.isEmpty()?null:MekanismKey.of(tank.getStack());}
            public long amount(){return tank.getStored();}
            public long insert(AEKey key,long n,boolean simulate,boolean internal) {
                if(!(key instanceof MekanismKey chemical) || !(chemical.getStack() instanceof GasStack))return 0;
                return n-tank.insert((GasStack)chemical.withAmount(n),action(simulate),automation(internal)).getAmount();
            }
            public long extract(AEKey key,long n,boolean simulate,boolean internal) {
                if(!key.equals(key()))return 0;
                if(internal && input)return tank.shrinkStack(n,action(simulate));
                return tank.extract(n,action(simulate),automation(internal)).getAmount();
            }
        };
    }
}
