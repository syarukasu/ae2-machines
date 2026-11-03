package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.compat.mek.*;
import com.syaru.ae2machines.platform.MekanismAdapter;
import java.lang.ref.WeakReference;
import java.math.*;
import mekanism.api.math.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="mekanism.common.capabilities.energy.VariableCapacityEnergyContainer",remap=false)
public abstract class MekStructureEnergyMixin implements MekStructureEnergy {
    @Shadow @Final private FloatingLongSupplier maxEnergy;
    @Unique private WeakReference<BlockEntity> machines$owner;
    @Override public void machines$structureOwner(BlockEntity owner){machines$owner=new WeakReference<>(owner);}
    @Override public BigInteger machines$structureBaseEnergy(){return MekanismAdapter.units(maxEnergy.get().toString());}
    @Inject(method="getMaxEnergy",at=@At("RETURN"),cancellable=true)
    private void machines$capacity(CallbackInfoReturnable<FloatingLong> cir){
        if(machines$owner==null)return;int q=MekWorldParallel.lanes(machines$owner.get());if(q==1)return;
        var amount=MekanismAdapter.units(cir.getReturnValue().toString()).multiply(BigInteger.valueOf(q)).min(MekanismAdapter.units(FloatingLong.MAX_VALUE.toString()));
        cir.setReturnValue(FloatingLong.parseFloatingLong(new BigDecimal(amount).movePointLeft(4).toPlainString()));
    }
}
