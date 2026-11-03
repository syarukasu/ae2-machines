package com.syaru.ae2machines.client;

import com.syaru.ae2machines.block.AcceleratorBlock;
import com.syaru.ae2machines.block.ComputationHostBlock;
import com.syaru.ae2machines.block.ComputationPartBlock;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Resolves only local formed housing tiles during mesh building, never during factory ticks. */
public final class ComputationCasingTiles {
    public static final int COUNT=24;
    private ComputationCasingTiles(){}
    public static String model(int index){
        if(index<0 || index>=COUNT)throw new IllegalArgumentException("Housing tile index");
        return "ae2machines:block/computation/"+(index<18?"head_tile_"+index:"tail_tile_"+(index-18));
    }
    public static int index(BlockState state,BlockPos pos,Function<BlockPos,BlockState> states){
        if(!(state.getBlock() instanceof ComputationPartBlock part) || part.kind()!=ComputationPartBlock.Kind.CASING
                || !state.getValue(ComputationHostBlock.FORMED))return -1;
        var facing=state.getValue(AcceleratorBlock.FACING);
        for(int z=0;z<2;z++)for(int y=0;y<3;y++)for(int x=0;x<3;x++){
            var candidate=states.apply(pos.relative(facing.getClockWise(),1-x).above(1-y).relative(facing,z));
            if(candidate.getBlock() instanceof ComputationHostBlock host && host.role()==ComputationHostBlock.Role.CONTROLLER
                    && candidate.getValue(ComputationHostBlock.FORMED) && candidate.getValue(AcceleratorBlock.FACING)==facing)
                return x+3*y+9*z;
        }
        for(int z=0;z<2;z++)for(int y=0;y<3;y++){
            var candidate=states.apply(pos.above(1-y).relative(facing,z));
            if(candidate.getBlock() instanceof ComputationPartBlock end && end.kind()==ComputationPartBlock.Kind.END
                    && candidate.getValue(ComputationHostBlock.FORMED) && candidate.getValue(AcceleratorBlock.FACING)==facing)
                return 18+y+3*z;
        }
        return -1;
    }
}
