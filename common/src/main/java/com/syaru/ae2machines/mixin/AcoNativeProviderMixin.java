package com.syaru.ae2machines.mixin;
import appeng.helpers.patternprovider.*;
import com.syaru.ae2machines.runtime.NativeWorldBatches;
import com.syaru.ae2craftingoptimizer.api.batch.v2.ProviderOwnedPatternBatchTarget;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
@Mixin(value=PatternProviderLogic.class,remap=false)
public abstract class AcoNativeProviderMixin implements ProviderOwnedPatternBatchTarget {
    @Shadow @Final private PatternProviderLogicHost host;
    @Override public BlockEntity aco$getProviderOwnedBatchTarget(){var b=NativeWorldBatches.get(host.getBlockEntity());return b==null?host.getBlockEntity():b.host();}
}
