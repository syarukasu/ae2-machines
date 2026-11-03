package com.syaru.ae2machines.platform;

import com.syaru.ae2machines.block.BaseEnergyPort;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraft.core.Direction;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;

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
    private LazyOptional<IEnergyStorage> cap = LazyOptional.of(() -> energy);
    private LazyOptional<?> eu=LazyOptional.empty();
    @Override public <T> LazyOptional<T> getCapability(Capability<T> capability,Direction side) {
        if(com.syaru.ae2machines.runtime.NativeMachines.gt() && GtEnergyRegistration.matches(capability)) {
            if(!eu.isPresent())eu=LazyOptional.of(()->new com.syaru.ae2machines.compat.gt.EuInput(this));
            return eu.cast();
        }
        return capability == ForgeCapabilities.ENERGY ? cap.cast() : super.getCapability(capability,side);
    }
    @Override public void invalidateCaps() { super.invalidateCaps();cap.invalidate();eu.invalidate();eu=LazyOptional.empty(); }
    @Override public void reviveCaps() { super.reviveCaps();cap=LazyOptional.of(() -> energy); }
}
