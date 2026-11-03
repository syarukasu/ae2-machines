package com.syaru.ae2machines.compat.mek;

import mekanism.common.tile.TileEntityBoundingBlock;
import mekanism.common.tile.machine.TileEntityIsotopicCentrifuge;
import mekanism.common.tile.machine.TileEntityPressurizedReactionChamber;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import static com.syaru.ae2machines.world.NativeFactoryCompiler.require;

/** Whole-region relocation keeps coordinates, and accepts only a complete known bounding shape. */
public final class MekSpatialBounds {
    private MekSpatialBounds() {}
    public static boolean capturable(ServerLevel level,BlockPos pos,BlockPos min,BlockPos max) {
        return capturable(level,pos,min,max,java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()));
    }
    public static boolean capturable(ServerLevel level,BlockPos pos,BlockPos min,BlockPos max,java.util.Set<Object> validatedNetworks) {
        var tile=level.getBlockEntity(pos);
        if (MekNativeNetworks.capturable(level,pos,min,max,validatedNetworks)) return true;
        if(tile instanceof TileEntityBoundingBlock bounding && (!inside(bounding.getMainPos(),min,max) || !level.hasChunkAt(bounding.getMainPos())))return false;
        var root=tile instanceof TileEntityBoundingBlock bounding?level.getBlockEntity(bounding.getMainPos()):tile;
        // NativeRegionMove checkpoints the complete BE and removes raw states, not the
        // destructive blockRemoved/drop/radiation path used by ordinary relocation.
        if(tile!=null && tile.getClass()==TileEntityPressurizedReactionChamber.class)return true;
        if(root==null || root.getClass()!=TileEntityIsotopicCentrifuge.class && !MekRecipeMachines.supports(root))return false;
        require(inside(root.getBlockPos(),min,max),"MEK_BOUNDING_MAIN_OUTSIDE_CAPTURE");
        var members=com.syaru.ae2machines.platform.MekSpatialShape.members(level,root.getBlockPos(),root.getBlockState());
        if(root.getClass()==TileEntityIsotopicCentrifuge.class)require(members.size()==1 && members.get(0).equals(root.getBlockPos().above()),"MEK_BOUNDING_SHAPE_CHANGED");
        for(var part:members) {
            require(inside(part,min,max),"MEK_BOUNDING_PART_OUTSIDE_CAPTURE");
            require(level.getBlockEntity(part) instanceof TileEntityBoundingBlock bounding
                    && bounding.getMainPos().equals(root.getBlockPos()),"MEK_BOUNDING_PART_MISSING_OR_WRONG_OWNER");
        }
        return pos.equals(root.getBlockPos()) || members.contains(pos);
    }
    private static boolean inside(BlockPos p,BlockPos min,BlockPos max){return p.getX()>=min.getX() && p.getX()<=max.getX() && p.getY()>=min.getY() && p.getY()<=max.getY() && p.getZ()>=min.getZ() && p.getZ()<=max.getZ();}
}
