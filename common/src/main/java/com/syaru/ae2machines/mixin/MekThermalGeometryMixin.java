package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.compat.mek.MekThermalShells;
import com.syaru.ae2machines.compat.mek.ThermalGeometrySet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import mekanism.common.lib.multiblock.IMultiblock;
import mekanism.common.lib.multiblock.Structure;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Pseudo
@Mixin(targets = "mekanism.common.lib.multiblock.FormationProtocol", remap = false)
public abstract class MekThermalGeometryMixin {
    @Redirect(method = "<init>", at = @At(value = "NEW", target = "it/unimi/dsi/fastutil/objects/ObjectOpenHashSet", ordinal = 0))
    private ObjectOpenHashSet<BlockPos> machines$structuralTerms(IMultiblock<?> owner, Structure structure) {
        return !Boolean.getBoolean("ae2machines.disableThermalGeometry") && owner instanceof BlockEntity block && MekThermalShells.known(block)
                ? new ThermalGeometrySet() : new ObjectOpenHashSet<>();
    }
}
