package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.compat.mek.MekBoilerWater;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Pseudo
@Mixin(targets="mekanism.common.content.boiler.BoilerMultiblockData",remap=false)
public abstract class MekBoilerFluidMixin {
    @ModifyArg(method="tick",at=@At(value="INVOKE",target="Lmekanism/common/registration/impl/GasRegistryObject;getStack(J)Lmekanism/api/chemical/gas/GasStack;"),index=0)
    private long machines$newSteam(long original){return ((MekBoilerWater)this).machines$boiledWater().longValueExact();}
    @ModifyArg(method="tick",at=@At(value="INVOKE",target="Lmekanism/api/chemical/gas/IGasTank;growStack(JLmekanism/api/Action;)J"),index=0)
    private long machines$moreSteam(long original){return ((MekBoilerWater)this).machines$boiledWater().longValueExact();}
}
