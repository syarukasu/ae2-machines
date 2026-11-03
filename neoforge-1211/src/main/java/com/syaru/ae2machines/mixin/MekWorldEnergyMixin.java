package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.compat.mek.MekWorldEnergyAccess;
import com.syaru.ae2machines.compat.mek.MekWorldParallel;
import java.math.BigInteger;
import mekanism.common.tile.base.TileEntityMekanism;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="mekanism.common.capabilities.energy.MachineEnergyContainer",remap=false)
public abstract class MekWorldEnergyMixin implements MekWorldEnergyAccess {
    @Shadow @Final protected TileEntityMekanism tile;
    @Shadow private long currentMaxEnergy;
    @Unique private BigInteger machines$loaded;
    @Override public void machines$loadedEnergy(BigInteger value){machines$loaded=tile.getLevel()==null && MekWorldParallel.supportsWorld(tile)?value:null;}
    @Override public void machines$levelAssigned(boolean virtual) {
        var saved=machines$loaded;machines$loaded=null;if(!virtual || saved==null)return;
        var energy=(mekanism.api.energy.IEnergyContainer)(Object)this;
        long amount=saved.divide(BigInteger.valueOf(10000)).longValueExact();
        if(amount>energy.getMaxEnergy())throw new IllegalStateException("SAVED_VIRTUAL_ENERGY_EXCEEDS_LANES");
        energy.setEnergy(amount);
    }
    @Override public BigInteger machines$nativeCapacity(){return BigInteger.valueOf(currentMaxEnergy).multiply(BigInteger.valueOf(10000));}
    @Inject(method="getMaxEnergy",at=@At("RETURN"),cancellable=true)
    private void machines$capacity(CallbackInfoReturnable<Long> cir) {
        int lanes=MekWorldParallel.lanes(tile);if(lanes==1)return;
        long capacity=cir.getReturnValueJ();
        cir.setReturnValue(capacity>Long.MAX_VALUE/lanes?Long.MAX_VALUE:capacity*lanes);
    }
}
