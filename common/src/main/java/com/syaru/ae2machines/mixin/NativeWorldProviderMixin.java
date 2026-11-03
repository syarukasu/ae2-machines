package com.syaru.ae2machines.mixin;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.*;
import com.syaru.ae2machines.runtime.NativeWorldBatches;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(value=PatternProviderLogic.class,remap=false)
public abstract class NativeWorldProviderMixin implements com.syaru.ae2machines.runtime.NativeWorldProvider {
    @Shadow @Final private PatternProviderLogicHost host;
    @Override public net.minecraft.world.level.block.entity.BlockEntity machines$providerEntity(){return host.getBlockEntity();}
    @Inject(method="isBusy",at=@At("HEAD"),cancellable=true)
    private void machines$busy(CallbackInfoReturnable<Boolean> result){if(NativeWorldBatches.providerBusy(host.getBlockEntity()))result.setReturnValue(true);}
    @Inject(method="pushPattern",at=@At("HEAD"),cancellable=true)
    private void machines$push(IPatternDetails pattern,KeyCounter[] inputs,CallbackInfoReturnable<Boolean> result){if(NativeWorldBatches.providerBusy(host.getBlockEntity()))result.setReturnValue(false);}
    @Inject(method="updatePatterns",at=@At("RETURN"))
    private void machines$patterns(CallbackInfo result){var b=NativeWorldBatches.get(host.getBlockEntity());if(b!=null)b.invalidate();}
}
