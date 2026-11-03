package com.syaru.ae2machines.platform;

import java.util.List;
import mekanism.common.block.attribute.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class MekSpatialShape {
    private MekSpatialShape() {}
    public static Level level(mekanism.common.content.evaporation.EvaporationMultiblockData data){return data.getHandlerWorld();}
    public static List<BlockPos> members(Level level,BlockPos pos,BlockState state) {
        var shape=Attribute.get(state,AttributeHasBounding.class);
        return shape==null?List.of():shape.getPositions(pos,state).map(BlockPos::immutable).toList();
    }
}
