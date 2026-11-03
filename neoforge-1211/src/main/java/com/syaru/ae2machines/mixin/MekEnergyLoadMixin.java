package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.compat.mek.MekWorldEnergyAccess;
import com.syaru.ae2machines.platform.MekanismAdapter;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="mekanism.common.capabilities.energy.BasicEnergyContainer",remap=false)
public abstract class MekEnergyLoadMixin {
    @Unique private boolean machines$loading;
    @Inject(method="deserializeNBT",at=@At("HEAD"))
    private void machines$begin(HolderLookup.Provider provider,CompoundTag tag,CallbackInfo ci){machines$loading=true;}
    @Inject(method="deserializeNBT",at=@At("RETURN"))
    private void machines$end(HolderLookup.Provider provider,CompoundTag tag,CallbackInfo ci){machines$loading=false;}
    @Inject(method="setEnergy",at=@At("HEAD"))
    private void machines$remember(long value,CallbackInfo ci) {
        if(machines$loading && (Object)this instanceof MekWorldEnergyAccess target)target.machines$loadedEnergy(MekanismAdapter.units(value));
    }
}
