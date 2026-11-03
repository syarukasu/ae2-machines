package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.compat.mek.*;
import java.lang.ref.WeakReference;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Pseudo
@Mixin(targets="mekanism.common.capabilities.heat.BasicHeatCapacitor",remap=false)
public abstract class MekStructureHeatMixin implements MekStructureHeat {
    @Shadow private double heatCapacity;
    @Unique private WeakReference<BlockEntity> machines$reactor;
    @Unique private int machines$rawCapacity;
    @Override public void machines$structureOwner(BlockEntity owner){machines$reactor=new WeakReference<>(owner);}
    @Override public double machines$baseHeatCapacity(){return heatCapacity;}
    @Override public boolean machines$scaledHeat(){return machines$reactor!=null && MekWorldParallel.lanes(machines$reactor.get())>1;}
    @Inject(method="getHeatCapacity",at=@At("RETURN"),cancellable=true)
    private void machines$capacity(CallbackInfoReturnable<Double> cir){
        if(machines$rawCapacity==0 && machines$reactor!=null)cir.setReturnValue(cir.getReturnValueD()*MekWorldParallel.lanes(machines$reactor.get()));
    }
    @Inject(method="setHeatCapacity",at=@At("HEAD"))
    private void machines$beginBase(double capacity,boolean updateHeat,CallbackInfo ci){machines$rawCapacity++;}
    @Inject(method="setHeatCapacity",at=@At("RETURN"))
    private void machines$endBase(double capacity,boolean updateHeat,CallbackInfo ci){machines$rawCapacity--;}
}
