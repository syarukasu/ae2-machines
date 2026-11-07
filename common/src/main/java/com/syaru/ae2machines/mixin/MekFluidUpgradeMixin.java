package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.compat.mek.MekWideFluidPipe;
import java.math.BigInteger;
import org.spongepowered.asm.mixin.*;

@Pseudo
@Mixin(targets="mekanism.common.upgrade.transmitter.MechanicalPipeUpgradeData",remap=false)
public abstract class MekFluidUpgradeMixin implements MekWideFluidPipe {
    @Unique private BigInteger machines$amount=BigInteger.ZERO;
    @Override public BigInteger machines$fluidShare(){return machines$amount;}
    @Override public void machines$fluidShare(BigInteger amount){machines$amount=amount;}
}
