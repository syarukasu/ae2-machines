package com.syaru.ae2machines.block;

import appeng.block.AEBaseEntityBlock;
import com.syaru.ae2machines.platform.EnergyPortEntity;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;

public final class EnergyPortBlock extends AEBaseEntityBlock<EnergyPortEntity> {
    public enum Mode implements StringRepresentable {
        FE, EU;
        @Override public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); }
    }
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Mode> MODE = EnumProperty.create("mode",Mode.class);
    public EnergyPortBlock() {
        super(metalProps().strength(4).noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(MODE,Mode.FE).setValue(ComputationHostBlock.FORMED,false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) {
        super.createBlockStateDefinition(builder);builder.add(FACING,MODE,ComputationHostBlock.FORMED);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());
    }
    @Override public void neighborChanged(BlockState state,net.minecraft.world.level.Level level,net.minecraft.core.BlockPos pos,Block source,net.minecraft.core.BlockPos from,boolean moving) {
        super.neighborChanged(state,level,pos,source,from,moving);ComputationStructure.changed(level,pos);
    }
    @Override public void onRemove(BlockState state,net.minecraft.world.level.Level level,net.minecraft.core.BlockPos pos,BlockState next,boolean moving) {
        if(!state.is(next.getBlock()))ComputationStructure.changed(level,pos);
        super.onRemove(state,level,pos,next,moving);
    }
}
