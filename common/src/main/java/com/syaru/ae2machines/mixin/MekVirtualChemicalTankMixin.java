package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.compat.mek.*;
import java.lang.ref.WeakReference;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="mekanism.api.chemical.BasicChemicalTank",remap=false)
public abstract class MekVirtualChemicalTankMixin implements MekVirtualTank {
    @Shadow @Final private long capacity;
    @Unique private WeakReference<BlockEntity> machines$owner;
    @Override public void machines$owner(BlockEntity owner){machines$owner=new WeakReference<>(owner);}
    @Override public long machines$nativeCapacity(){return capacity;}
    @Inject(method="getCapacity",at=@At("RETURN"),cancellable=true)
    private void machines$capacity(CallbackInfoReturnable<Long> cir) {
        if(machines$owner==null)return;
        int lanes=MekWorldParallel.lanes(machines$owner.get());long base=cir.getReturnValueJ();
        if(lanes>1)cir.setReturnValue(base>Long.MAX_VALUE/lanes?Long.MAX_VALUE:base*lanes);
    }
}
