package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.compat.mek.*;
import com.syaru.ae2machines.platform.MekFluidAccess;
import java.math.BigInteger;
import mekanism.common.content.network.transmitter.MechanicalPipe;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Pseudo
@Mixin(targets="mekanism.common.content.network.transmitter.MechanicalPipe",remap=false)
public abstract class MekWidePipeMixin implements MekWideFluidPipe {
    @Unique private BigInteger machines$share;
    @Unique private boolean machines$pulling;
    @Override public BigInteger machines$fluidShare(){return machines$share==null?BigInteger.valueOf(((MechanicalPipe)(Object)this).saveShare.getAmount()):machines$share;}
    @Override public void machines$fluidShare(BigInteger amount){if(amount.signum()<0)throw new IllegalArgumentException("Negative fluid share");machines$share=amount;}
    @Inject(method="pullFromAcceptors",at=@At("HEAD"),cancellable=true)
    private void machines$pull(CallbackInfo ci){
        if(machines$pulling)return;var self=(MechanicalPipe)(Object)this;var unit=MekFluidAccess.pullUnit(self);
        if(unit.equals(BigInteger.ONE))return;
        machines$pulling=true;
        try{MekFluidUnits.call(unit,()->{self.pullFromAcceptors();return null;});}finally{machines$pulling=false;}
        ci.cancel();
    }
    @Inject(method="write",at=@At("RETURN"))
    private void machines$save(net.minecraft.core.HolderLookup.Provider registry,CompoundTag tag,CallbackInfoReturnable<CompoundTag> ci){
        var amount=machines$fluidShare();
        if(amount.compareTo(MekFluidUnits.INT_MAX)>0)tag.putByteArray("ae2machines:fluid_share",amount.toByteArray());else tag.remove("ae2machines:fluid_share");
    }
    @Inject(method="read",at=@At("RETURN"))
    private void machines$load(net.minecraft.core.HolderLookup.Provider registry,CompoundTag tag,CallbackInfo ci){
        var self=(MechanicalPipe)(Object)this;machines$share=null;
        if(tag.contains("ae2machines:fluid_share")){
            var amount=new BigInteger(tag.getByteArray("ae2machines:fluid_share"));
            if(amount.compareTo(MekFluidUnits.INT_MAX)<=0 || self.saveShare.isEmpty())throw new IllegalArgumentException("Invalid saved pipe share");
            machines$share=amount;MekFluidAccess.set(self.buffer,self.saveShare,amount);
        }
    }
    @Inject(method="takeShare",at=@At("HEAD"),cancellable=true)
    private void machines$take(CallbackInfo ci){
        var self=(MechanicalPipe)(Object)this;
        if(!self.hasTransmitterNetwork() || !MekFluidAccess.owned(self.getTransmitterNetwork()))return;
        var tank=self.getTransmitterNetwork().fluidTank;var share=machines$fluidShare();var total=MekWideFluidTank.amount(tank);
        if(share.compareTo(total)>0)throw new IllegalStateException("Exact pipe share exceeds network inventory");
        if(share.signum()>0){var type=tank.getFluid().copy();((MekWideFluidTank)tank).machines$fluidAmount(total.subtract(share));MekFluidAccess.set(self.buffer,type,share);}
        ci.cancel();
    }
    @Inject(method="getUpgradeData()Lmekanism/common/upgrade/transmitter/MechanicalPipeUpgradeData;",at=@At("RETURN"))
    private void machines$upgrade(CallbackInfoReturnable<mekanism.common.upgrade.transmitter.MechanicalPipeUpgradeData> ci){
        ((MekWideFluidPipe)ci.getReturnValue()).machines$fluidShare(MekWideFluidTank.amount(((MechanicalPipe)(Object)this).buffer));
    }
    @Redirect(method="parseUpgradeData(Lmekanism/common/upgrade/transmitter/MechanicalPipeUpgradeData;)V",
            at=@At(value="INVOKE",target="Lmekanism/common/content/network/transmitter/MechanicalPipe;takeFluid(Lnet/neoforged/neoforge/fluids/FluidStack;Lmekanism/api/Action;)Lnet/neoforged/neoforge/fluids/FluidStack;"))
    private net.neoforged.neoforge.fluids.FluidStack machines$upgradeContents(MechanicalPipe pipe,net.neoforged.neoforge.fluids.FluidStack contents,mekanism.api.Action action,
            mekanism.common.upgrade.transmitter.MechanicalPipeUpgradeData data){
        var amount=((MekWideFluidPipe)data).machines$fluidShare();
        if(amount.compareTo(MekFluidUnits.INT_MAX)<=0)return pipe.takeFluid(contents,action);
        var tank=pipe.hasTransmitterNetwork()?pipe.getTransmitterNetwork().fluidTank:pipe.buffer;
        if(!tank.isEmpty() && !tank.isFluidEqual(contents))throw new IllegalStateException("Incompatible exact pipe upgrade");
        MekFluidAccess.set(tank,contents,MekWideFluidTank.amount(tank).add(amount));
        return net.neoforged.neoforge.fluids.FluidStack.EMPTY;
    }
}
