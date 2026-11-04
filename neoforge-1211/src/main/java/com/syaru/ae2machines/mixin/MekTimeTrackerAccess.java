package com.syaru.ae2machines.mixin;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;
@Pseudo
@Mixin(targets="mekanism.common.lib.LastEnergyTracker",remap=false)
public interface MekTimeTrackerAccess {
    @Accessor("currentGameTime") long machines$time();
    @Accessor("currentGameTime") void machines$time(long n);
    @Accessor("currentEnergyReceived") long machines$current();
    @Accessor("currentEnergyReceived") void machines$current(long n);
    @Accessor("lastEnergyReceived") long machines$last();
    @Accessor("lastEnergyReceived") void machines$last(long n);
}
