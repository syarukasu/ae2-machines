package com.syaru.ae2machines.mixin;
import com.syaru.ae2machines.world.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo
@Mixin(targets="mekanism.common.tile.base.TileEntityMekanism",remap=false)
public abstract class MekVariableOwnershipMixin {
    @Inject(method="canFunction",at=@At("RETURN"),cancellable=true)
    private void machines$owned(CallbackInfoReturnable<Boolean> cir){
        var block=(BlockEntity)(Object)this;
        if(block.getLevel() instanceof ServerLevel level && NativeCellWorlds.owns(level) && level.getBlockEntity(block.getBlockPos())==block){
            var requests=NativeWorldRuntime.get(level).variable();if(requests!=null && requests.busy(block.getBlockPos()))cir.setReturnValue(false);
        }
    }
}
