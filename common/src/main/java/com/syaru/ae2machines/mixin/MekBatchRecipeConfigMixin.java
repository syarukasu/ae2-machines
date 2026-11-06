package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.runtime.NativeWorldBatches;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="mekanism.common.tile.base.TileEntityMekanism",remap=false)
public abstract class MekBatchRecipeConfigMixin {
    @Inject(method={"recalculateUpgrades","setControlType"},at=@At("RETURN"))
    private void machines$recipeChanged(CallbackInfo ci){
        NativeWorldBatches.configurationChanged((BlockEntity)(Object)this);
    }
}
