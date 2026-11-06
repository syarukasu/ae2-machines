package com.syaru.ae2machines.mixin;

import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Level.class)
public interface ThermalCellMachines {
    @Invoker("tickBlockEntities") void machines$runOriginalMachines();
}
