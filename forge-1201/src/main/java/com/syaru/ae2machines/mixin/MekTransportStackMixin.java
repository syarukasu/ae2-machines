package com.syaru.ae2machines.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Forge's vanilla byte Count cannot persist a native aggregated transporter packet. */
@Pseudo
@Mixin(targets="mekanism.common.content.transporter.TransporterStack",remap=false)
public abstract class MekTransportStackMixin {
    @Shadow public ItemStack itemStack;
    @Inject(method="write(Lnet/minecraft/nbt/CompoundTag;)V",at=@At("RETURN"))
    private void machines$save(CompoundTag tag,CallbackInfo ci){
        if(itemStack.getCount()>127){tag.put("ae2machines:bulkStack",itemStack.copyWithCount(1).save(new CompoundTag()));tag.putInt("ae2machines:bulkCount",itemStack.getCount());}
        else{tag.remove("ae2machines:bulkStack");tag.remove("ae2machines:bulkCount");}
    }
    @Inject(method="read(Lnet/minecraft/nbt/CompoundTag;)V",at=@At("RETURN"))
    private void machines$load(CompoundTag tag,CallbackInfo ci){
        if(tag.contains("ae2machines:bulkStack")){var saved=ItemStack.of(tag.getCompound("ae2machines:bulkStack"));int count=tag.getInt("ae2machines:bulkCount");if(saved.isEmpty() || count<=0)throw new IllegalStateException("Invalid native bulk transporter packet");saved.setCount(count);itemStack=saved;}
    }
}
