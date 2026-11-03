package com.syaru.ae2machines.mixin;

import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.helpers.patternprovider.*;
import com.syaru.ae2machines.runtime.AssemblyProvider;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
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
    @Redirect(method="pushPattern",at=@At(value="INVOKE",target="Lappeng/api/implementations/blockentities/ICraftingMachine;of(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Lappeng/api/implementations/blockentities/ICraftingMachine;"))
    private ICraftingMachine machines$adjacent(Level level,BlockPos pos,Direction side) {
        return host instanceof AssemblyProvider provider?provider.adjacent(pos):ICraftingMachine.of(level,pos,side);
    }
}
