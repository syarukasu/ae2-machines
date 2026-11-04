package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.world.NativeWorldRuntime;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="mekanism.common.tile.transmitter.TileEntityTransmitter",remap=false)
public abstract class MekThermalTransportEventMixin {
    @Inject(method={"setForceUpdate","onNeighborTileChange","onNeighborBlockChange"},at=@At("HEAD"))
    private void machines$circuitChanged(CallbackInfo ci){
        if(((BlockEntity)(Object)this).getLevel() instanceof ServerLevel level && NativeWorldRuntime.owns(level))NativeWorldRuntime.get(level).invalidateTemporal();
    }
}
