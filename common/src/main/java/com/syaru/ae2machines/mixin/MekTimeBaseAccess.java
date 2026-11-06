package com.syaru.ae2machines.mixin;

import mekanism.common.lib.LastEnergyTracker;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Pseudo
@Mixin(targets="mekanism.common.tile.base.TileEntityMekanism",remap=false)
public interface MekTimeBaseAccess {
    @Invoker("getClientActive") boolean machines$thermalClientActive();
    @Accessor("updateDelay") int machines$timeDelay();
    @Accessor("updateDelay") void machines$timeDelay(int value);
    @Accessor("lastEnergyTracker") LastEnergyTracker machines$timeEnergyTracker();
    @Accessor("currentRedstoneLevel") int machines$timeRedstone();
    @Accessor("currentRedstoneLevel") void machines$timeRedstone(int value);
    @Accessor("updateComparators") boolean machines$timeComparator();
    @Accessor("updateComparators") void machines$timeComparator(boolean value);
    @Invoker("getRedstoneLevel") int machines$timeRedstoneLevel();
}
