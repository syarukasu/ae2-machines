package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.world.MekHeatState;
import mekanism.common.lib.multiblock.*;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="mekanism.common.lib.multiblock.MultiblockCache",remap=false)
public abstract class MekHeatCacheMixin implements MekHeatState.Owner {
    @Inject(method="apply",at=@At("HEAD"))
    private void machines$capacityBeforeLoad(MultiblockData data,CallbackInfo ci){
        if(ae2machines$heat.enrolled())com.syaru.ae2machines.compat.mek.MekStructureParallel.enrollExtraFluids(data);
        com.syaru.ae2machines.compat.mek.MekStructureParallel.prepareCache(data);
    }
    @Unique private final MekHeatState ae2machines$heat=new MekHeatState();
    @Override public MekHeatState ae2machines$heatState(){return ae2machines$heat;}
    @Inject(method="sync",at=@At("TAIL"))
    private void ae2machines$sync(MultiblockData data,CallbackInfo ci){
        com.syaru.ae2machines.compat.mek.MekStructureParallel.cacheHeat(data,(MultiblockCache<?>)(Object)this);
        ae2machines$heat.sync(data);
    }
    @Inject(method="apply",at=@At("TAIL"))
    private void ae2machines$apply(MultiblockData data,CallbackInfo ci){ae2machines$heat.apply(data);}
    @Inject(method="save",at=@At("TAIL"))
    private void ae2machines$save(CompoundTag tag,CallbackInfo ci){ae2machines$heat.save(tag);}
    @Inject(method="load",at=@At("TAIL"))
    private void ae2machines$load(CompoundTag tag,CallbackInfo ci){ae2machines$heat.load(tag);}
    @Inject(method="merge",at=@At("TAIL"))
    private void ae2machines$merge(MultiblockCache<?> other,MultiblockCache.RejectContents rejects,CallbackInfo ci){
        ae2machines$heat.merge(((MekHeatState.Owner)other).ae2machines$heatState());
    }
}
