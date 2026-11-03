package com.syaru.ae2machines.world;

import com.syaru.ae2machines.platform.PlatformData;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/** Descriptive metadata only; ownership and material accounting still live in the journal. */
public final class VirtualCellMetadata {
    private static final String SIZE="cellSize",RETRY="cellSizeRetry";
    public record Size(int x,int y,int z){}
    private VirtualCellMetadata(){}
    public static void describe(CompoundTag data,BlockPos a,BlockPos b){
        data.putIntArray(SIZE,new int[]{Math.toIntExact(Math.abs((long)a.getX()-b.getX())+1),
                Math.toIntExact(Math.abs((long)a.getY()-b.getY())+1),Math.toIntExact(Math.abs((long)a.getZ()-b.getZ())+1)});
        data.remove(RETRY);
    }
    public static Optional<Size> size(CompoundTag data){
        if(!data.hasUUID("factory"))return Optional.empty();var n=data.getIntArray(SIZE);
        return n.length==3 && n[0]>0 && n[1]>0 && n[2]>0?Optional.of(new Size(n[0],n[1],n[2])):Optional.empty();
    }
    public static void clear(CompoundTag data){data.remove("factory");data.remove(SIZE);data.remove(RETRY);data.remove("restorationRemainder");}
    public static boolean populate(ServerLevel level,CompoundTag data){
        if(!data.hasUUID("factory"))return false;if(size(data).isPresent())return true;
        try{
            var blueprint=FactoryJournal.open(level,data.getUUID("factory")).read().getCompound("blueprint");
            if(!blueprint.contains("first") || !blueprint.contains("last"))return false;
            describe(data,BlockPos.of(blueprint.getLong("first")),BlockPos.of(blueprint.getLong("last")));return true;
        }catch(RuntimeException unavailable){return false;}
    }
    public static void refreshLegacy(ItemStack stack,ServerLevel level){
        var data=PlatformData.cellData(stack);
        if(!data.hasUUID("factory") || size(data).isPresent() || data.getLong(RETRY)>level.getGameTime())return;
        if(!populate(level,data))data.putLong(RETRY,level.getGameTime()+200);
        PlatformData.setCellData(stack,data);
    }
    public static void append(CompoundTag data,List<Component> lines){
        if(!data.hasUUID("factory"))return;
        lines.add(size(data).<Component>map(n->Component.translatable("tooltip.ae2machines.virtual_spatial_cell.size",n.x(),n.y(),n.z()))
                .orElseGet(()->Component.translatable("tooltip.ae2machines.virtual_spatial_cell.size_unknown")));
    }
}
