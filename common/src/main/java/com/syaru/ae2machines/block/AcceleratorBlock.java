package com.syaru.ae2machines.block;

import appeng.block.AEBaseEntityBlock;
import com.syaru.ae2machines.platform.AcceleratorEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;

public class AcceleratorBlock extends AEBaseEntityBlock<AcceleratorEntity> {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public AcceleratorBlock() {
        super(metalProps().strength(4).noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(ACTIVE,false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) {
        super.createBlockStateDefinition(builder); builder.add(FACING,ACTIVE);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());
    }
}
