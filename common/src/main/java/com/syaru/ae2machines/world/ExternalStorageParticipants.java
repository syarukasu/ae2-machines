package com.syaru.ae2machines.world;

import java.util.*;
import appeng.parts.storagebus.StorageBusPart;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Explicit native NBT-backed storage, not an assumption about arbitrary capability backends. */
public final class ExternalStorageParticipants {
    private static final Set<String> SIMPLE=Set.of("net.minecraft.world.level.block.entity.ChestBlockEntity",
            "net.minecraft.world.level.block.entity.BarrelBlockEntity","net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity",
            "mekanism.common.tile.TileEntityBin","mekanism.common.tile.TileEntityFluidTank",
            "mekanism.common.tile.TileEntityChemicalTank","mekanism.common.tile.TileEntityGasTank",
            "mekanism.common.tile.TileEntityEnergyCube");
    private ExternalStorageParticipants(){}
    public static boolean add(StorageBusPart bus,Set<BlockEntity> participants){
        var host=bus.getBlockEntity();var level=host.getLevel();if(level==null)return false;
        var pos=host.getBlockPos().relative(bus.getSide());if(!level.hasChunkAt(pos))return false;
        var target=level.getBlockEntity(pos);if(target==null)return false;
        if(target instanceof com.syaru.ae2machines.api.RecoverableStorage declared){
            var blocks=declared.checkpointParticipants();if(blocks==null || !blocks.contains(target) || blocks.isEmpty())return false;
            for(var block:blocks)if(block==null || block.isRemoved() || block.getLevel()!=level || !level.hasChunkAt(block.getBlockPos()) || level.getBlockEntity(block.getBlockPos())!=block)return false;
            participants.addAll(blocks);return true;
        }
        if(!SIMPLE.contains(target.getClass().getName())
                && !(com.syaru.ae2machines.runtime.NativeMachines.mek() && com.syaru.ae2machines.compat.mek.MekVariableWork.supports(target)))return false;
        // Resolve exactly the paired half; never load or omit an unknown co-owner.
        if(target instanceof net.minecraft.world.level.block.entity.ChestBlockEntity){
            var state=target.getBlockState();var type=state.getValue(net.minecraft.world.level.block.ChestBlock.TYPE);
            if(type!=net.minecraft.world.level.block.state.properties.ChestType.SINGLE){
                var paired=pos.relative(net.minecraft.world.level.block.ChestBlock.getConnectedDirection(state));
                if(!level.hasChunkAt(paired))return false;
                var neighbor=level.getBlockEntity(paired);
                if(!(neighbor instanceof net.minecraft.world.level.block.entity.ChestBlockEntity) || neighbor.getBlockState().getBlock()!=state.getBlock()
                        || neighbor.getBlockState().getValue(net.minecraft.world.level.block.ChestBlock.TYPE)!=type.getOpposite()
                        || !neighbor.getBlockState().getValue(net.minecraft.world.level.block.ChestBlock.FACING).equals(state.getValue(net.minecraft.world.level.block.ChestBlock.FACING)))return false;
                participants.add(neighbor);
            }
        }
        participants.add(target);return true;
    }
}
