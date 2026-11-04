package com.syaru.ae2machines.mixin;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.*;
@Mixin(Level.class)
public interface NativeThermalLevelAccess {
    @Accessor("blockEntityTickers") List<TickingBlockEntity> machines$thermalTickers();
    @Accessor("pendingBlockEntityTickers") List<TickingBlockEntity> machines$thermalPending();
    @Invoker("shouldTickBlocksAt") boolean machines$thermalTicking(BlockPos pos);
}
