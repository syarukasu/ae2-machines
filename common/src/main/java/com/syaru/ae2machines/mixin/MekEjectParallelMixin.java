package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.compat.mek.MekTransportParallel;
import com.syaru.ae2machines.compat.mek.MekWorldParallel;
import java.util.function.LongSupplier;
import mekanism.common.tile.base.TileEntityMekanism;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

/** Aggregate virtual machines' native chemical emission, preserving native sides and receivers. */
@Pseudo
@Mixin(targets="mekanism.common.tile.component.TileComponentEjector",remap=false)
public abstract class MekEjectParallelMixin {
    @Shadow @Final private TileEntityMekanism tile;
    @Redirect(method="eject",at=@At(value="INVOKE",target="Ljava/util/function/LongSupplier;getAsLong()J",ordinal=0))
    private long machines$chemicalWindow(LongSupplier rate){
        return MekTransportParallel.scale(rate.getAsLong(),MekWorldParallel.lanes(tile));
    }
}
