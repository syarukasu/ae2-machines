package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.compat.mek.*;
import java.lang.ref.WeakReference;
import java.util.function.LongSupplier;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets={"mekanism.common.capabilities.chemical.variable.VariableCapacityChemicalTank","mekanism.common.capabilities.chemical.VariableCapacityChemicalTank"},remap=false)
public abstract class MekVariableChemicalTankMixin implements MekVirtualTank {
    @Shadow @Final private LongSupplier capacity;
    @Unique private WeakReference<BlockEntity> machines$owner;
    @Override public void machines$owner(BlockEntity owner){machines$owner=new WeakReference<>(owner);}
    @Override public long machines$nativeCapacity(){return capacity.getAsLong();}
    @Inject(method="getCapacity",at=@At("RETURN"),cancellable=true)
    private void machines$capacity(CallbackInfoReturnable<Long> cir){
        if(machines$owner!=null)cir.setReturnValue(MekStructureParallel.capacity(this,machines$owner.get(),cir.getReturnValueJ()));
    }
}
