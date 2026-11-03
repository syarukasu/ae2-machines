package com.syaru.ae2machines.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets="com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableEnergyContainer",remap=false)
public interface GtEnergyInputAccess {
    @Accessor("amps") long ae2machines$amps();
    @Accessor("lastTimeStamp") long ae2machines$lastTimeStamp();
}
