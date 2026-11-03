package com.syaru.ae2machines.platform;
import com.syaru.ae2machines.compat.aco.ProcessingWorker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
public final class AcoAcceleratorEntity extends AcceleratorEntity implements ProcessingWorker {
    public AcoAcceleratorEntity(BlockPos pos,BlockState state) { super(pos,state); }
}
