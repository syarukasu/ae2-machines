package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.compat.mek.MekWorldEnergyAccess;
import com.syaru.ae2machines.compat.mek.MekWorldParallel;
import com.syaru.ae2machines.platform.MekanismAdapter;
import java.math.BigDecimal;
import java.math.BigInteger;
import mekanism.api.math.FloatingLong;
import mekanism.common.tile.base.TileEntityMekanism;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="mekanism.common.capabilities.energy.MachineEnergyContainer",remap=false)
public abstract class MekWorldEnergyMixin implements MekWorldEnergyAccess {
    @Shadow @Final protected TileEntityMekanism tile;
    @Shadow private FloatingLong currentMaxEnergy;
    @Unique private FloatingLong machines$base,machines$capacity;
    @Unique private int machines$lanes;
    @Unique private BigInteger machines$loaded;
    @Override public void machines$loadedEnergy(BigInteger value){machines$loaded=tile.getLevel()==null && MekWorldParallel.supportsWorld(tile)?value:null;}
    @Override public void machines$levelAssigned(boolean virtual) {
        var saved=machines$loaded;machines$loaded=null;if(!virtual || saved==null)return;
        var energy=(mekanism.api.energy.IEnergyContainer)(Object)this;
        if(saved.compareTo(MekanismAdapter.units(energy.getMaxEnergy().toString()))>0)throw new IllegalStateException("SAVED_VIRTUAL_ENERGY_EXCEEDS_LANES");
        energy.setEnergy(FloatingLong.parseFloatingLong(new BigDecimal(saved).movePointLeft(4).toPlainString()));
    }
    @Override public BigInteger machines$nativeCapacity(){return MekanismAdapter.units(currentMaxEnergy.toString());}
    @Inject(method="getMaxEnergy",at=@At("RETURN"),cancellable=true)
    private void machines$capacity(CallbackInfoReturnable<FloatingLong> cir) {
        int lanes=MekWorldParallel.lanes(tile);if(lanes==1)return;
        if(machines$lanes!=lanes || !cir.getReturnValue().equals(machines$base)) {
            var capacity=MekanismAdapter.units(cir.getReturnValue().toString()).multiply(BigInteger.valueOf(lanes))
                    .min(MekanismAdapter.units(FloatingLong.MAX_VALUE.toString()));
            machines$base=cir.getReturnValue().copyAsConst();machines$lanes=lanes;
            machines$capacity=FloatingLong.parseFloatingLong(new BigDecimal(capacity).movePointLeft(4).toPlainString()).copyAsConst();
        }
        cir.setReturnValue(machines$capacity);
    }
}
