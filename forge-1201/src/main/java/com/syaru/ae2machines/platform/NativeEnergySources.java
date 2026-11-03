package com.syaru.ae2machines.platform;

import com.syaru.ae2machines.api.CheckpointedEnergySource;
import java.math.BigInteger;
import java.util.*;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class NativeEnergySources {
    private NativeEnergySources(){}
    public static com.syaru.ae2machines.block.BaseAccelerator exclusiveFactoryOutput(BlockEntity source){
        if(source==null || !(source.getLevel() instanceof net.minecraft.server.level.ServerLevel level) || !source.getClass().getName().equals("mekanism.common.tile.TileEntityEnergyCube"))return null;
        var cube=(mekanism.common.tile.TileEntityEnergyCube)source;if(cube.getTier()==mekanism.common.tier.EnergyCubeTier.CREATIVE || cube.getEnergyContainer().isEmpty())return null;
        var config=cube.getConfig().getConfig(mekanism.common.lib.transmitter.TransmissionType.ENERGY);if(!config.isEjecting())return null;
        com.syaru.ae2machines.block.BaseAccelerator owner=null;
        for(var side:Direction.values()){
            var relative=mekanism.api.RelativeSide.fromDirections(cube.getDirection(),side);
            if(!config.getDataType(relative).canOutput())continue;
            var pos=source.getBlockPos().relative(side);if(!level.hasChunkAt(pos))return null;
            var target=level.getBlockEntity(pos);
            if(!mekanism.common.integration.energy.EnergyCompatUtils.getLazyStrictEnergyHandler(target,side.getOpposite()).isPresent())continue;
            var host=target instanceof com.syaru.ae2machines.block.BaseAccelerator accelerator?accelerator:target instanceof com.syaru.ae2machines.block.BaseEnergyPort port?port.feTarget():null;
            if(host==null || owner!=null && owner!=host)return null;owner=host;
        }
        return owner;
    }
    public static Optional<CheckpointedEnergySource> mekanismCube(BlockEntity source,Direction output){
        if(source==null || !source.getClass().getName().equals("mekanism.common.tile.TileEntityEnergyCube"))return Optional.empty();
        var energy=source.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY,output).orElse(null);
        if(energy==null || !energy.canExtract())return Optional.empty();
        return Optional.of(new CheckpointedEnergySource(){
            public Collection<BlockEntity> checkpointParticipants(){return List.of(source);}
            public BigInteger extractFE(BigInteger maximum,boolean simulate){
                com.syaru.ae2machines.engine.ExactAmounts.nonnegative(maximum);
                if(source.isRemoved() || source.getLevel()==null || !source.getLevel().hasChunkAt(source.getBlockPos()) || source.getLevel().getBlockEntity(source.getBlockPos())!=source)return BigInteger.ZERO;
                return BigInteger.valueOf(energy.extractEnergy(maximum.min(BigInteger.valueOf(Integer.MAX_VALUE)).intValueExact(),simulate));
            }
        });
    }
}
