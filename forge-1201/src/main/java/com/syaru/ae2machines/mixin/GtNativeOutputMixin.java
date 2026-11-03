package com.syaru.ae2machines.mixin;

import com.gregtechceu.gtceu.api.capability.*;
import com.syaru.ae2machines.compat.gt.NativeEuOutput;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Pseudo
@Mixin(targets="com.gregtechceu.gtceu.api.machine.trait.NotifiableEnergyContainer",remap=false)
public abstract class GtNativeOutputMixin {
    @Redirect(method="serverTick",at=@At(value="INVOKE",target="Lcom/gregtechceu/gtceu/api/capability/GTCapabilityHelper;getEnergyContainer(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Lcom/gregtechceu/gtceu/api/capability/IEnergyContainer;"))
    private IEnergyContainer machines$externalEu(Level level,BlockPos pos,Direction side) {
        var local=GTCapabilityHelper.getEnergyContainer(level,pos,side);
        return local!=null?local:NativeEuOutput.target(level);
    }
}
