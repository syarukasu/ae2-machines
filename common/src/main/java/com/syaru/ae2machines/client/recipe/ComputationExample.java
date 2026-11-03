package com.syaru.ae2machines.client.recipe;

import com.syaru.ae2machines.block.*;
import com.syaru.ae2machines.menu.FactoryPreviewData;
import com.syaru.ae2machines.platform.MachinesMod;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Client-only construction example, never a crafting or world mutation operation. */
public record ComputationExample(int tier) {
    public ComputationExample {
        if(tier<1 || tier>9)throw new IllegalArgumentException("tier");
    }
    public static List<ComputationExample> all(){return java.util.stream.IntStream.rangeClosed(1,9).mapToObj(ComputationExample::new).toList();}
    public FactoryPreviewData.Snapshot snapshot(int units,boolean formed){
        if(units<1 || units>ComputationStructure.MAX_UNITS)throw new IllegalArgumentException("units");
        var blocks=new ArrayList<FactoryPreviewData.Entry>();
        for(int x=0;x<3;x++)for(int y=0;y<3;y++)for(int z=0;z<2;z++){
            Block block=x==1 && y==1 && z==0?MachinesMod.COMPUTATION_CONTROLLER.get()
                    :x==2 && y==1 && z==1?MachinesMod.CHANNEL_PORT.get():MachinesMod.COMPUTATION_CASING.get();
            add(blocks,x,y,z,block,block==MachinesMod.CHANNEL_PORT.get()?Direction.SOUTH:Direction.NORTH,formed);
        }
        for(int unit=1;unit<=units;unit++)for(int y=0;y<3;y++)for(int z=0;z<2;z++){
            Block block=y==1?MachinesMod.COOLING_UNIT.get():z==0?
                    (y==2?MachinesMod.CELL_BAY.get():MachinesMod.PARALLEL_PROCESSORS.get(tier-1).get())
                    :(y==2?MachinesMod.SPEED_PROCESSORS.get(tier-1).get():MachinesMod.COMPUTATION_POWER.get());
            add(blocks,-unit,y,z,block,z==0?Direction.NORTH:Direction.SOUTH,formed);
        }
        for(int y=0;y<3;y++)for(int z=0;z<2;z++)
            add(blocks,-units-1,y,z,y==1 && z==0?MachinesMod.END_CAP.get():MachinesMod.COMPUTATION_CASING.get(),Direction.NORTH,formed);
        return new FactoryPreviewData.Snapshot(0,FactoryPreviewData.STRUCTURE,"example",blocks,0,0);
    }
    private static void add(List<FactoryPreviewData.Entry> entries,int x,int y,int z,Block block,Direction facing,boolean formed){
        BlockState state=block.defaultBlockState();
        if(state.hasProperty(AcceleratorBlock.FACING))state=state.setValue(AcceleratorBlock.FACING,facing);
        if(state.hasProperty(ComputationHostBlock.FORMED))state=state.setValue(ComputationHostBlock.FORMED,formed);
        entries.add(new FactoryPreviewData.Entry(ComputationStructure.position(BlockPos.ZERO,Direction.NORTH,x,y,z),state,new CompoundTag()));
    }
    public List<ItemStack> materials(int units){
        var counts=new LinkedHashMap<Block,Integer>();
        for(var entry:snapshot(units,false).blocks())counts.merge(entry.state().getBlock(),1,Integer::sum);
        return counts.entrySet().stream().map(e->new ItemStack(e.getKey(),e.getValue())).toList();
    }
}
