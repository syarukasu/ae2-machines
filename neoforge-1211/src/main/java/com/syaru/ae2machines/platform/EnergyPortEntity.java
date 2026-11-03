package com.syaru.ae2machines.platform;

import com.syaru.ae2machines.block.BaseEnergyPort;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;


public final class EnergyPortEntity extends BaseEnergyPort {
    public final IEnergyStorage energy = new IEnergyStorage() {
        public int receiveEnergy(int n,boolean simulate) { return receive(n,simulate); }
        public int extractEnergy(int n,boolean simulate) { return extract(n,simulate); }
        public int getEnergyStored() { return stored(); }
        public int getMaxEnergyStored() { return capacity(); }
        public boolean canExtract() { return EnergyPortEntity.this.canExtract(); }
        public boolean canReceive() { return true; }
    };
    public EnergyPortEntity(BlockPos pos,BlockState state) { super(MachinesMod.ENERGY_ENTITY.get(),pos,state); }

}
