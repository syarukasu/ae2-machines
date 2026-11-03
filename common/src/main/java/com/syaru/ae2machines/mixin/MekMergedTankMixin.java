package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.compat.mek.MekVirtualTank;
import mekanism.api.chemical.IChemicalTank;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;

/** Enroll the native backing tank without bypassing merged-type exclusion. */
@Pseudo
@Mixin(targets={"mekanism.api.chemical.merged.ChemicalTankWrapper","mekanism.common.capabilities.merged.ChemicalTankWrapper"},remap=false)
public abstract class MekMergedTankMixin implements MekVirtualTank {
    @Shadow @Final private IChemicalTank internal;
    @Override public void machines$owner(BlockEntity owner){((MekVirtualTank)internal).machines$owner(owner);}
    @Override public long machines$nativeCapacity(){return ((MekVirtualTank)internal).machines$nativeCapacity();}
}
