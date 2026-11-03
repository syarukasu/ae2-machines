package com.syaru.ae2machines.platform;

import java.util.*;
import mekanism.common.block.attribute.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class MekSpatialShape {
    private MekSpatialShape() {}
    public static Level level(mekanism.common.content.evaporation.EvaporationMultiblockData data){return data.getLevel();}
    public static List<BlockPos> members(Level level,BlockPos pos,BlockState state) {
        var shape=Attribute.get(state,AttributeHasBounding.class);var positions=new ArrayList<BlockPos>();
        if(shape!=null)shape.handle(level,pos,state,positions,(world,part,list)->{list.add(part.immutable());return true;});
        return List.copyOf(positions);
    }
}
