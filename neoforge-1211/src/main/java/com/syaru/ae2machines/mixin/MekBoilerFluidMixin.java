package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.compat.mek.MekBoilerWater;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Pseudo
@Mixin(targets="mekanism.common.content.boiler.BoilerMultiblockData",remap=false)
public abstract class MekBoilerFluidMixin {
    @ModifyArg(method="tick",at=@At(value="INVOKE",target="Lmekanism/common/registration/impl/DeferredChemical;asStack(J)Lmekanism/api/chemical/ChemicalStack;"),index=0)
    private long machines$newSteam(long original){return ((MekBoilerWater)this).machines$boiledWater().longValueExact();}
    @ModifyArg(method="tick",at=@At(value="INVOKE",target="Lmekanism/api/chemical/IChemicalTank;growStack(JLmekanism/api/Action;)J"),index=0)
    private long machines$moreSteam(long original){return ((MekBoilerWater)this).machines$boiledWater().longValueExact();}
}
