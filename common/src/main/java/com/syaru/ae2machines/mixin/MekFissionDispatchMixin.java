package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.compat.mek.FissionParallelState;
import mekanism.common.lib.multiblock.MultiblockData;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Pseudo
@Mixin(targets="mekanism.common.tile.prefab.TileEntityMultiblock",remap=false)
public abstract class MekFissionDispatchMixin {
    @Redirect(method="onUpdateServer",at=@At(value="INVOKE",target="Lmekanism/common/lib/multiblock/MultiblockData;tick(Lnet/minecraft/world/level/Level;)Z"))
    private boolean machines$thermalGroups(MultiblockData data,Level world){return FissionParallelState.tick(data,world);}
}
