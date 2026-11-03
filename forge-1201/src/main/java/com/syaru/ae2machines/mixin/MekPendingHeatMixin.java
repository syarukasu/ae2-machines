package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.compat.mek.MekPendingHeat;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Pseudo
@Mixin(targets="mekanism.common.capabilities.heat.BasicHeatCapacitor",remap=false)
public abstract class MekPendingHeatMixin implements MekPendingHeat.Owner {
    @Unique private boolean machines$enrolled;
    @Override public boolean machines$heatEnrolled(){return machines$enrolled;}
    @Override public void machines$heatEnrolled(boolean value){machines$enrolled=value;}
    @Inject(method="serializeNBT()Lnet/minecraft/nbt/CompoundTag;",at=@At("RETURN"))
    private void machines$save(CallbackInfoReturnable<CompoundTag> cir){
        MekPendingHeat.save(this,cir.getReturnValue());
        if(this instanceof com.syaru.ae2machines.compat.mek.MekStructureHeat heat && heat.machines$scaledHeat())cir.getReturnValue().putDouble(mekanism.api.NBTConstants.HEAT_CAPACITY,heat.machines$baseHeatCapacity());
    }
    @Inject(method="deserializeNBT",at=@At("RETURN"))
    private void machines$load(CompoundTag tag,CallbackInfo ci){MekPendingHeat.load(this,tag);}
}
