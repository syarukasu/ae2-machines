package com.syaru.ae2machines.platform;
import com.syaru.ae2machines.compat.aco.ExactNativeWorker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
public final class ExactAcoAcceleratorEntity extends AcceleratorEntity implements ExactNativeWorker {
    public ExactAcoAcceleratorEntity(BlockPos pos,BlockState state){super(pos,state);}
}
