package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.world.NativeWorldRuntime;
import mekanism.common.tile.transmitter.TileEntityTransmitter;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Pseudo
@Mixin(targets="mekanism.common.content.network.transmitter.Transmitter",remap=false)
public abstract class MekThermalConnectionEventMixin {
    @Shadow public abstract TileEntityTransmitter getTransmitterTile();
    @Unique private void machines$circuitChanged(){
        if(getTransmitterTile().getLevel() instanceof ServerLevel level && NativeWorldRuntime.owns(level))NativeWorldRuntime.get(level).invalidateTemporal();
    }
    @Inject(method="setConnectionTypeRaw",at=@At("HEAD"))
    private void machines$connectionChanged(CallbackInfo ci){machines$circuitChanged();}
    @Inject(method="setTransmitterNetwork(Lmekanism/common/lib/transmitter/DynamicNetwork;Z)Z",at=@At("HEAD"))
    private void machines$networkChanged(CallbackInfoReturnable<Boolean> ci){machines$circuitChanged();}
}
