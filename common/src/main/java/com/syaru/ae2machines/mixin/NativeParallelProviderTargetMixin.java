package com.syaru.ae2machines.mixin;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.helpers.patternprovider.PatternProviderTarget;
import com.syaru.ae2machines.api.NativeParallelAccess;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(targets="appeng.helpers.patternprovider.PatternProviderTargetCache",remap=false)
public abstract class NativeParallelProviderTargetMixin {
    @Unique private net.minecraft.server.level.ServerLevel machines$level;
    @Unique private BlockPos machines$pos;
    @Unique private Direction machines$side;
    @Shadow @Final private IActionSource src;
    @Inject(method="<init>",at=@At("RETURN"))
    private void machines$locate(net.minecraft.server.level.ServerLevel level,BlockPos pos,Direction side,IActionSource source,org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci){machines$level=level;machines$pos=pos;machines$side=side;}
    @Inject(method="find",at=@At("HEAD"),cancellable=true)
    private void machines$nativeLanes(CallbackInfoReturnable<PatternProviderTarget> ci){
        if(!machines$level.hasChunkAt(machines$pos))return;
        var be=machines$level.getBlockEntity(machines$pos);var side=machines$side;var source=src;
        var storage=NativeParallelAccess.storage(be,side);if(storage==null)return;
        ci.setReturnValue(new PatternProviderTarget(){
            public long insert(AEKey key,long amount,Actionable mode){return storage.insert(key,amount,mode,source);}
            public boolean containsPatternInput(java.util.Set<AEKey> keys){for(var e:storage.getAvailableStacks())if(keys.contains(e.getKey().dropSecondary()))return true;return false;}
        });
    }
}
