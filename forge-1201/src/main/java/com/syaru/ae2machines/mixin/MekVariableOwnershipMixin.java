package com.syaru.ae2machines.mixin;
import com.syaru.ae2machines.world.*;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo
@Mixin(targets="mekanism.common.util.MekanismUtils",remap=false)
public abstract class MekVariableOwnershipMixin {
    @Inject(method="canFunction",at=@At("RETURN"),cancellable=true)
    private static void machines$owned(TileEntityMekanism block,CallbackInfoReturnable<Boolean> cir){
        if(block.getLevel() instanceof ServerLevel level && NativeCellWorlds.owns(level) && level.getBlockEntity(block.getBlockPos())==block){
            var requests=NativeWorldRuntime.get(level).variable();if(requests!=null && requests.busy(block.getBlockPos()))cir.setReturnValue(false);
        }
    }
}
