package com.syaru.ae2machines.mixin;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo @Mixin(targets={"mekanism.common.tile.prefab.TileEntityElectricMachine","mekanism.common.tile.factory.TileEntityFactory","mekanism.common.tile.machine.TileEntityChemicalOxidizer",
        "mekanism.common.tile.machine.TileEntityChemicalInfuser","mekanism.common.tile.machine.TileEntityRotaryCondensentrator"},remap=false)
public abstract class MekOwnedBatchMixin {
    @Inject(method="onUpdateServer",at=@At("HEAD"),cancellable=true)
    private void machines$owned(CallbackInfoReturnable<Boolean> ci){if(com.syaru.ae2machines.runtime.NativeWorldBatches.owns((net.minecraft.world.level.block.entity.BlockEntity)(Object)this))ci.setReturnValue(false);}
}
