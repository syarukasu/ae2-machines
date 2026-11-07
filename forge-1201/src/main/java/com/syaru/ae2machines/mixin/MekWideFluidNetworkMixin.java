package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.compat.mek.*;
import com.syaru.ae2machines.platform.MekFluidAccess;
import java.math.BigInteger;
import java.util.List;
import mekanism.common.content.network.FluidNetwork;
import mekanism.common.content.network.transmitter.MechanicalPipe;
import mekanism.common.lib.transmitter.acceptor.NetworkAcceptorCache;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Pseudo
@Mixin(targets="mekanism.common.content.network.FluidNetwork",remap=false)
public abstract class MekWideFluidNetworkMixin {
    @SuppressWarnings("unchecked")
    @Unique private NetworkAcceptorCache<IFluidHandler> machines$acceptors(){return (NetworkAcceptorCache<IFluidHandler>)((MekFluidNetworkAccess)this).machines$fluidAcceptors();}
    @Unique private boolean machines$emitting;
    @Unique private BigInteger machines$merged;
    @Unique private FluidStack machines$mergedType;
    @Inject(method="onUpdate",at=@At("HEAD"),cancellable=true)
    private void machines$emit(CallbackInfo ci){
        var self=(FluidNetwork)(Object)this;MekFluidAccess.bind(self);
        if(machines$emitting || !MekFluidAccess.owned(self))return;
        var unit=MekFluidUnits.covering(MekWideFluidTank.amount(self.fluidTank));if(unit.equals(BigInteger.ONE))return;
        for(var entry:machines$acceptors().getAcceptorEntrySet())if(!MekFluidAccess.pooled(self.getWorld().getBlockEntity(entry.getKey())))return;
        machines$emitting=true;
        try{MekFluidUnits.call(unit,()->{self.onUpdate();return null;});}finally{machines$emitting=false;}
        ci.cancel();
    }
    @Inject(method="absorbBuffer(Lmekanism/common/content/network/transmitter/MechanicalPipe;)V",at=@At("HEAD"),cancellable=true)
    private void machines$absorb(MechanicalPipe pipe,CallbackInfo ci){
        var self=(FluidNetwork)(Object)this;MekFluidAccess.bind(self);if(!MekFluidAccess.owned(self))return;
        var amount=MekWideFluidTank.amount(pipe.buffer);
        if(amount.signum()>0){
            var type=pipe.buffer.getFluid().copy();
            if(!self.fluidTank.isEmpty() && !self.fluidTank.isFluidEqual(type))throw new IllegalStateException("Incompatible exact fluid buffers");
            MekFluidAccess.set(self.fluidTank,type,MekWideFluidTank.amount(self.fluidTank).add(amount));pipe.buffer.setEmpty();
        }ci.cancel();
    }
    @Inject(method="clampBuffer",at=@At("HEAD"),cancellable=true)
    private void machines$retain(CallbackInfo ci){if(MekFluidAccess.owned((FluidNetwork)(Object)this))ci.cancel();}
    @Inject(method="updateSaveShares(Lmekanism/common/content/network/transmitter/MechanicalPipe;)V",at=@At("RETURN"))
    private void machines$shares(MechanicalPipe trigger,CallbackInfo ci){MekFluidAccess.saveShares((FluidNetwork)(Object)this);}
    @Inject(method="adoptTransmittersAndAcceptorsFrom(Lmekanism/common/content/network/FluidNetwork;)Ljava/util/List;",at=@At("HEAD"))
    private void machines$beforeMerge(FluidNetwork other,CallbackInfoReturnable<List<MechanicalPipe>> ci){
        var self=(FluidNetwork)(Object)this;machines$merged=null;
        if(!MekFluidAccess.owned(self) && !MekFluidAccess.owned(other))return;
        if(!self.fluidTank.isEmpty() && !other.fluidTank.isEmpty() && !self.fluidTank.isFluidEqual(other.fluidTank.getFluid()))throw new IllegalStateException("Incompatible exact fluid network merge");
        machines$merged=MekWideFluidTank.amount(self.fluidTank).add(MekWideFluidTank.amount(other.fluidTank));
        machines$mergedType=(self.fluidTank.isEmpty()?other.fluidTank:self.fluidTank).getFluid().copy();
    }
    @Inject(method="adoptTransmittersAndAcceptorsFrom(Lmekanism/common/content/network/FluidNetwork;)Ljava/util/List;",at=@At("RETURN"))
    private void machines$afterMerge(FluidNetwork other,CallbackInfoReturnable<List<MechanicalPipe>> ci){
        if(machines$merged==null)return;var self=(FluidNetwork)(Object)this;MekFluidAccess.bind(self);
        MekFluidAccess.set(self.fluidTank,machines$mergedType,machines$merged);machines$merged=null;other.fluidTank.setEmpty();
    }
}
