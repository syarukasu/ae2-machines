package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.compat.mek.MekStructureParallel;
import java.lang.ref.WeakReference;
import mekanism.common.lib.multiblock.MultiblockData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="mekanism.common.lib.multiblock.MultiblockData",remap=false)
public abstract class MekStructureParallelMixin implements MekStructureParallel.Bound {
    @Shadow @Final private java.util.function.Supplier<Level> worldSupplier;
    @Override public Level machines$structureWorld(){return worldSupplier.get();}
    @Unique private WeakReference<BlockEntity> machines$owner;
    @Override public BlockEntity machines$parallelOwner(){return machines$owner==null?null:machines$owner.get();}
    @Override public void machines$parallelOwner(BlockEntity owner){machines$owner=new WeakReference<>(owner);}
    @Inject(method="tick",at=@At("HEAD"))
    private void machines$prepare(Level level,CallbackInfoReturnable<Boolean> cir){MekStructureParallel.prepare((MultiblockData)(Object)this,level);}
}
