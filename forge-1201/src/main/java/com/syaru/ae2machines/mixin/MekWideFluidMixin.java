package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.compat.mek.*;
import java.math.BigInteger;
import java.util.function.*;
import mekanism.api.*;
import mekanism.api.fluid.IExtendedFluidTank;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Pseudo
@Mixin(targets="mekanism.common.capabilities.fluid.BasicFluidTank",remap=false)
public abstract class MekWideFluidMixin implements MekWideFluidTank {
    @Shadow protected FluidStack stored;
    @Shadow @Final protected BiPredicate<FluidStack,AutomationType> canInsert,canExtract;
    @Shadow public abstract boolean isFluidValid(FluidStack stack);
    @Shadow public abstract void onContentsChanged();
    @Shadow protected abstract int getRate(AutomationType automation);
    @Unique private BigInteger machines$extra=BigInteger.ZERO;
    @Unique private Supplier<BigInteger> machines$wideCapacity;
    @Override public BigInteger machines$fluidAmount(){return BigInteger.valueOf(stored.getAmount()).add(machines$extra);}
    @Override public BigInteger machines$fluidCapacity(){return machines$wideCapacity==null?BigInteger.valueOf(((MekVirtualTank)this).machines$nativeCapacity()):machines$wideCapacity.get();}
    @Override public void machines$fluidCapacity(Supplier<BigInteger> capacity){machines$wideCapacity=capacity;}
    @Override public boolean machines$wideFluid(){return machines$wideCapacity!=null || machines$extra.signum()>0;}
    @Override public void machines$fluidAmount(BigInteger amount){
        if(amount.signum()<0 || amount.signum()>0 && stored.isEmpty())throw new IllegalArgumentException("Invalid exact fluid amount/type");
        int window=amount.min(MekFluidUnits.INT_MAX).intValueExact();
        stored=window==0?FluidStack.EMPTY:new FluidStack(stored,window);
        machines$extra=amount.subtract(BigInteger.valueOf(window));onContentsChanged();
    }
    @Unique private void machines$set(FluidStack type,BigInteger amount){
        if(amount.signum()>0 && stored.isEmpty())stored=new FluidStack(type,1);
        machines$fluidAmount(amount);
    }
    @Inject(method="getFluid",at=@At("HEAD"),cancellable=true)
    private void machines$view(CallbackInfoReturnable<FluidStack> ci){
        if(machines$wideFluid() && !MekFluidUnits.unit().equals(BigInteger.ONE))ci.setReturnValue(new FluidStack(stored,MekFluidUnits.view(machines$fluidAmount())));
    }
    @Inject(method="getFluidAmount",at=@At("HEAD"),cancellable=true)
    private void machines$count(CallbackInfoReturnable<Integer> ci){if(machines$wideFluid())ci.setReturnValue(MekFluidUnits.view(machines$fluidAmount()));}
    public int getNeeded(){
        if(!machines$wideFluid())return Math.max(0,((IExtendedFluidTank)this).getCapacity()-stored.getAmount());
        return MekFluidUnits.view(machines$fluidCapacity().subtract(machines$fluidAmount()));
    }
    @Inject(method="setStack(Lnet/minecraftforge/fluids/FluidStack;Z)V",at=@At("HEAD"),cancellable=true)
    private void machines$replace(FluidStack stack,boolean validate,CallbackInfo ci){
        if(!machines$wideFluid())return;
        if(validate && !stack.isEmpty() && !isFluidValid(stack))throw new IllegalArgumentException("Invalid exact fluid type");
        stored=stack.isEmpty()?FluidStack.EMPTY:new FluidStack(stack,1);machines$extra=BigInteger.ZERO;
        machines$set(stack,MekFluidUnits.exact(stack.getAmount()));ci.cancel();
    }
    @Inject(method="insert",at=@At("HEAD"),cancellable=true)
    private void machines$insert(FluidStack stack,Action action,AutomationType automation,CallbackInfoReturnable<FluidStack> ci){
        if(!machines$wideFluid())return;
        int amount=0;
        if(!stack.isEmpty() && isFluidValid(stack) && canInsert.test(stack,automation)
                && (stored.isEmpty() || stored.isFluidEqual(stack)))
            amount=Math.min(stack.getAmount(),Math.min(getRate(automation),getNeeded()));
        if(action.execute() && amount>0)machines$set(stack,machines$fluidAmount().add(MekFluidUnits.exact(amount)));
        ci.setReturnValue(new FluidStack(stack,stack.getAmount()-amount));
    }
    @Inject(method="extract",at=@At("HEAD"),cancellable=true)
    private void machines$extract(int offered,Action action,AutomationType automation,CallbackInfoReturnable<FluidStack> ci){
        if(!machines$wideFluid())return;
        int amount=stored.isEmpty() || !canExtract.test(stored,automation)?0:Math.max(0,Math.min(offered,Math.min(getRate(automation),MekFluidUnits.view(machines$fluidAmount()))));
        var result=new FluidStack(stored,amount);
        if(action.execute() && amount>0)machines$fluidAmount(machines$fluidAmount().subtract(MekFluidUnits.exact(amount)));
        ci.setReturnValue(result);
    }
    @Inject(method="growStack",at=@At("HEAD"),cancellable=true)
    private void machines$grow(int offered,Action action,CallbackInfoReturnable<Integer> ci){
        if(!machines$wideFluid() || stored.isEmpty())return;
        int amount=offered>=0?Math.min(offered,Math.min(getRate(null),getNeeded()))
                :Math.max(offered,-Math.min(getRate(null),MekFluidUnits.view(machines$fluidAmount())));
        if(action.execute() && amount!=0)machines$fluidAmount(machines$fluidAmount().add(MekFluidUnits.exact(amount)));
        ci.setReturnValue(amount);
    }
    @Inject(method="setStackSize",at=@At("HEAD"),cancellable=true)
    private void machines$size(int amount,Action action,CallbackInfoReturnable<Integer> ci){
        if(!machines$wideFluid() || stored.isEmpty())return;
        int accepted=Math.max(0,Math.min(amount,MekFluidUnits.view(machines$fluidCapacity())));
        if(action.execute())machines$fluidAmount(MekFluidUnits.exact(accepted));ci.setReturnValue(accepted);
    }
    @Inject(method="serializeNBT()Lnet/minecraft/nbt/CompoundTag;",at=@At("RETURN"))
    private void machines$save(CallbackInfoReturnable<CompoundTag> ci){if(machines$extra.signum()>0)ci.getReturnValue().putByteArray("ae2machines:fluid_extra",machines$extra.toByteArray());}
    @Inject(method="deserializeNBT(Lnet/minecraft/nbt/CompoundTag;)V",at=@At("HEAD"))
    private void machines$beforeLoad(CompoundTag tag,CallbackInfo ci){machines$extra=BigInteger.ZERO;}
    @Inject(method="deserializeNBT(Lnet/minecraft/nbt/CompoundTag;)V",at=@At("RETURN"))
    private void machines$load(CompoundTag tag,CallbackInfo ci){
        if(!tag.contains("ae2machines:fluid_extra"))return;
        var extra=new BigInteger(tag.getByteArray("ae2machines:fluid_extra"));
        if(extra.signum()<0 || stored.isEmpty())throw new IllegalArgumentException("Invalid saved exact fluid");machines$extra=extra;
    }
}
