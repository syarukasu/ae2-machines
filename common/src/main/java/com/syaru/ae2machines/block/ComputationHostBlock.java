package com.syaru.ae2machines.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public final class ComputationHostBlock extends AcceleratorBlock {
    public enum Role { CONTROLLER, BAY, CHANNEL }
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");
    public static final BooleanProperty FILLED = BooleanProperty.create("filled");
    private final Role role;
    public ComputationHostBlock(Role role) {
        this.role = role;
        registerDefaultState(defaultBlockState().setValue(FORMED, false).setValue(FILLED, false));
    }
    public Role role() { return role; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FORMED, FILLED);
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
