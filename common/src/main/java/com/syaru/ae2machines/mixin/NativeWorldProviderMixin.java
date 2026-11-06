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
    @Shadow private java.util.Set<net.minecraft.core.Direction> getActiveSides(){throw new AssertionError();}
    @Shadow private PatternProviderTarget findAdapter(net.minecraft.core.Direction side){throw new AssertionError();}
    @Override public net.minecraft.world.level.block.entity.BlockEntity machines$providerEntity(){return host.getBlockEntity();}
    @Override public boolean machines$countedTarget(IPatternDetails pattern,net.minecraft.core.BlockPos target){
        var logic=(PatternProviderLogic)(Object)this;
        if(!logic.getAvailablePatterns().contains(pattern) || logic.isBlocking()
                || logic.getCraftingLockedReason()!=appeng.api.config.LockCraftingMode.NONE
                || logic.getConfigManager().getSetting(appeng.api.config.Settings.LOCK_CRAFTING_MODE)!=appeng.api.config.LockCraftingMode.NONE)return false;
        var owner=host.getBlockEntity();var level=owner.getLevel();boolean found=false;
        for(var side:getActiveSides()){
            var pos=owner.getBlockPos().relative(side);
            var machine=com.syaru.ae2machines.platform.NativeCraftingLookup.find(level,pos,side.getOpposite());
            // Do not replace native plan receivers or alter the provider's round-robin route.
            if(machine!=null && machine.acceptsPlans())return false;
            if(findAdapter(side)!=null){if(!pos.equals(target))return false;found=true;}
        }
        return found;
    }
    @Inject(method="isBusy",at=@At("HEAD"),cancellable=true)
    private void machines$busy(CallbackInfoReturnable<Boolean> result){if(NativeWorldBatches.providerBusy(host.getBlockEntity()))result.setReturnValue(true);}
    @Inject(method="pushPattern",at=@At("HEAD"),cancellable=true)
    private void machines$push(IPatternDetails pattern,KeyCounter[] inputs,CallbackInfoReturnable<Boolean> result){if(NativeWorldBatches.providerOccupied(host.getBlockEntity()))result.setReturnValue(false);}
    @Inject(method="updatePatterns",at=@At("RETURN"))
    private void machines$patterns(CallbackInfo result){var b=NativeWorldBatches.get(host.getBlockEntity());if(b!=null)b.invalidateProvider(host.getBlockEntity().getBlockPos());}
}
