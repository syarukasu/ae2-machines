package com.syaru.ae2machines.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

public final class ComputationPartBlock extends Block {
    public enum Kind { CASING, COOLING, END, PARALLEL, SPEED }
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private final Kind kind;
    private final int tier;
    public ComputationPartBlock(Kind kind, int tier) {
        super(Properties.of().strength(4).sound(SoundType.METAL).noOcclusion());
        this.kind = kind;
        this.tier = tier;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(ComputationHostBlock.FORMED, false));
    }
    public Kind kind() { return kind; }
    public int tier() { return tier; }
    public int maximumPower() {
        if (kind != Kind.PARALLEL && kind != Kind.SPEED) throw new IllegalStateException("Not a processor");
        // Keep the registered SPEED IDs so existing placed cores and item stacks survive.
        return kind==Kind.SPEED?com.syaru.ae2machines.engine.ProcessorTiers.threadPower(tier):com.syaru.ae2machines.engine.ProcessorTiers.parallelPower(tier);
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ComputationHostBlock.FORMED);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
    @Override public void neighborChanged(BlockState state, Level level, BlockPos pos, Block source, BlockPos from, boolean moving) {
        super.neighborChanged(state, level, pos, source, from, moving);
        ComputationStructure.changed(level, pos);
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock())) ComputationStructure.changed(level, pos);
        super.onRemove(state, level, pos, next, moving);
    }
}
