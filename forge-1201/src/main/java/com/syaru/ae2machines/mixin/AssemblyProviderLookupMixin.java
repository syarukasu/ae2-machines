package com.syaru.ae2machines.mixin;
import appeng.helpers.patternprovider.*;
import com.syaru.ae2machines.runtime.AssemblyProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(value=PatternProviderLogic.class,remap=false)
public abstract class AssemblyProviderLookupMixin {
    @Shadow @Final private PatternProviderLogicHost host;
    @Inject(method="findAdapter",at=@At("HEAD"),cancellable=true)
    private void machines$inventory(net.minecraft.core.Direction side,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<PatternProviderTarget> cir) {
        if(host instanceof AssemblyProvider provider)cir.setReturnValue(provider.inventory(side));
    }
    @Redirect(method="pushPattern",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;",remap=true))
    private BlockEntity machines$adjacent(Level level,BlockPos pos){
        return host instanceof AssemblyProvider provider?provider.adjacent(pos):level.getBlockEntity(pos);
    }
}
