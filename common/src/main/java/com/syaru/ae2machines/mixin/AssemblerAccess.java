package com.syaru.ae2machines.mixin;

import appeng.blockentity.crafting.MolecularAssemblerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value=MolecularAssemblerBlockEntity.class,remap=false)
public interface AssemblerAccess {
    @Accessor("progress") double machines$progress();
    @Accessor("progress") void machines$progress(double value);
}
