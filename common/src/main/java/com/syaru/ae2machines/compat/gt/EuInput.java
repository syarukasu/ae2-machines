package com.syaru.ae2machines.compat.gt;

import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.syaru.ae2machines.block.BaseEnergyPort;
import com.syaru.ae2machines.block.EnergyPortBlock;
import net.minecraft.core.Direction;

/** Input-only demand endpoint, not a battery or an EU/FE converter. */
public final class EuInput implements IEnergyContainer {
    private final BaseEnergyPort port;
    public EuInput(BaseEnergyPort port) { this.port=port; }
    @Override public boolean inputsEnergy(Direction side) {
        return port.euTarget()!=null && (side==null || side==port.getBlockState().getValue(EnergyPortBlock.FACING));
    }
    @Override public long acceptEnergyFromNetwork(Direction side,long voltage,long amperage) {
        var target=port.euTarget();return target==null || !inputsEnergy(side)?0:target.receiveEU(voltage,amperage,false);
    }
    @Override public long changeEnergy(long n) { return 0; }
    @Override public long getEnergyStored() { return 0; }
    @Override public long getEnergyCapacity() { var target=port.euTarget();return target==null?0:target.demandEU(); }
    @Override public long getInputVoltage() { var target=port.euTarget();return target==null?0:target.voltageEU(); }
    @Override public long getInputAmperage() { return getEnergyCapacity()>0?Long.MAX_VALUE:0; }
}
