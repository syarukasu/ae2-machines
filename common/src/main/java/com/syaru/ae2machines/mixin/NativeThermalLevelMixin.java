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
    @Redirect(method="tickBlockEntities",at=@At(value="INVOKE",target="Ljava/util/List;iterator()Ljava/util/Iterator;"))
    private Iterator<TickingBlockEntity> machines$thermalTickers(List<TickingBlockEntity> original){
        return (Object)this instanceof ServerLevel level && NativeCellWorlds.owns(level)
                ?NativeWorldRuntime.get(level).thermalTickers(original).iterator():original.iterator();
    }
    @Inject(method="tickBlockEntities",at=@At("RETURN"))
    private void machines$thermalEnd(CallbackInfo ci){if((Object)this instanceof ServerLevel level && NativeCellWorlds.owns(level))NativeWorldRuntime.get(level).thermalTickEnd();}
}
