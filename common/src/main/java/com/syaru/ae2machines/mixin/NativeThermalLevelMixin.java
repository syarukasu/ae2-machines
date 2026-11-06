package com.syaru.ae2machines.mixin;
import com.syaru.ae2machines.world.*;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Level.class)
public abstract class NativeThermalLevelMixin {
    @Redirect(method="getGameTime",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/storage/WritableLevelData;getGameTime()J"))
    private long machines$localProcessingTime(net.minecraft.world.level.storage.WritableLevelData data){
        return (Object)this instanceof ServerLevel level && NativeCellWorlds.owns(level)
                ?NativeWorldRuntime.get(level).state().visibleTicks():data.getGameTime();
    }
    @Redirect(method="tickBlockEntities",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/entity/TickingBlockEntity;tick()V"))
    private void machines$processingClock(TickingBlockEntity ticker){
        if((Object)this instanceof ServerLevel level && NativeCellWorlds.owns(level)
                && NativeProcessingSchedule.processing(level.getBlockEntity(ticker.getPos()))){
            NativeWorldRuntime.get(level).state().processingClock(()->{ticker.tick();return null;});
        }else ticker.tick();
    }
    @Redirect(method="tickBlockEntities",at=@At(value="INVOKE",target="Ljava/util/List;iterator()Ljava/util/Iterator;"))
    private Iterator<TickingBlockEntity> machines$thermalTickers(List<TickingBlockEntity> original){
        return (Object)this instanceof ServerLevel level && NativeCellWorlds.owns(level)
                ?NativeWorldRuntime.get(level).thermalTickers(original).iterator():original.iterator();
    }
    @Inject(method="tickBlockEntities",at=@At("RETURN"))
    private void machines$thermalEnd(CallbackInfo ci){if((Object)this instanceof ServerLevel level && NativeCellWorlds.owns(level))NativeWorldRuntime.get(level).thermalTickEnd();}
}
