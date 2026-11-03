package com.syaru.ae2machines.mixin;

import appeng.blockentity.crafting.MolecularAssemblerBlockEntity;
import com.syaru.ae2machines.runtime.AssemblerPersistence;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=MolecularAssemblerBlockEntity.class,remap=false)
public abstract class AssemblerPersistenceMixin {
    @Unique private final AssemblerPersistence machines$persistence=new AssemblerPersistence();
    @Inject(method="loadTag",at=@At("TAIL"))
    private void machines$load(CompoundTag tag,CallbackInfo ci) {
        machines$persistence.loaded((MolecularAssemblerBlockEntity)(Object)this,tag);
    }
    @Inject(method="saveAdditional",at=@At("TAIL"),remap=true)
    private void machines$save(CompoundTag tag,CallbackInfo ci) {
        machines$persistence.save((MolecularAssemblerBlockEntity)(Object)this,tag);
    }
}
