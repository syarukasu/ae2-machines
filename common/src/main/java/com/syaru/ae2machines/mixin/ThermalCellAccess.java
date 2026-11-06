package com.syaru.ae2machines.mixin;

import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.*;

@Mixin(ServerLevel.class)
public interface ThermalCellAccess {
    @Invoker("advanceWeatherCycle") void machines$advanceWeather();
    @Invoker("tickTime") void machines$advanceTime();
    @Invoker("runBlockEvents") void machines$runBlockEvents();
    @Invoker("tickBlock") void machines$tickBlock(net.minecraft.core.BlockPos pos,net.minecraft.world.level.block.Block block);
    @Invoker("tickFluid") void machines$tickFluid(net.minecraft.core.BlockPos pos,net.minecraft.world.level.material.Fluid fluid);
    @Accessor("handlingTick") void machines$handling(boolean value);
    @Accessor("emptyTime") void machines$emptyTime(int value);
}
