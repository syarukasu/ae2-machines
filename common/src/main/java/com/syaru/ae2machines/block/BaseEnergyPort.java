package com.syaru.ae2machines.block;

import appeng.blockentity.AEBaseBlockEntity;
import net.minecraft.core.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public abstract class BaseEnergyPort extends AEBaseBlockEntity {
    protected BaseEnergyPort(BlockEntityType<?> type,BlockPos pos,BlockState state) { super(type,pos,state); }
    private BaseAccelerator target() {
        if(level==null || level.isClientSide || isRemoved())return null;
        var modular=ComputationStructure.energyTarget(level,worldPosition);
        if(modular!=null)return modular;
        if(getBlockState().getValue(ComputationHostBlock.FORMED))return null;
        var pos=worldPosition.relative(getBlockState().getValue(EnergyPortBlock.FACING).getOpposite());
        return level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof BaseAccelerator a && !ComputationStructure.modular(a)?a:null;
    }
    public BaseAccelerator euTarget() {
        if(level==null || level.isClientSide || isRemoved() || getBlockState().getValue(EnergyPortBlock.MODE)!=EnergyPortBlock.Mode.EU)return null;
        return target();
    }
    public static boolean toggle(net.minecraft.world.entity.player.Player player,BlockPos pos) {
        if(!player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()
                || !(player.level().getBlockEntity(pos) instanceof BaseEnergyPort))return false;
        if(!player.level().isClientSide && appeng.util.Platform.hasPermissions(new appeng.api.util.DimensionalBlockPos(player.level(),pos),player)) {
            var state=player.level().getBlockState(pos);player.level().setBlock(pos,state.cycle(EnergyPortBlock.MODE),3);
        }
        return true;
    }
    public int receive(int amount,boolean simulate) {
        if (level == null || level.isClientSide || getBlockState().getValue(EnergyPortBlock.MODE) != EnergyPortBlock.Mode.FE) return 0;
        var host=target();return host==null?0:host.receiveEnergy(amount,simulate);
    }
    public int stored() {
        if(getBlockState().getValue(EnergyPortBlock.MODE)!=EnergyPortBlock.Mode.FE)return 0;
        if (level == null) return 0;
        var host=target();return host==null?0:host.storedFE();
    }
    public int capacity() {
        if(getBlockState().getValue(EnergyPortBlock.MODE)!=EnergyPortBlock.Mode.FE)return 0;
        if(level==null) return 0;
        var host=target();return host==null?0:host.capacityFE();
    }
    public int extract(int amount,boolean simulate) {
        var target=feTarget();return target==null?0:target.extractEnergy(amount,simulate);
    }
    public boolean canExtract(){var target=feTarget();return target!=null && target.energyOutput();}
    public BaseAccelerator feTarget() {
        if(level==null || level.isClientSide || isRemoved() || getBlockState().getValue(EnergyPortBlock.MODE)!=EnergyPortBlock.Mode.FE)return null;
        return target();
    }
}
