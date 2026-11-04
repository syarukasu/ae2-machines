package com.syaru.ae2machines.mixin;
import com.syaru.ae2machines.world.NativeFurnaceTime;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class NativeTemporalFurnaceMixin implements com.syaru.ae2machines.api.NativeTimeParticipant {
    @Override public Step nativeTimeStep(int maximumTicks){return NativeFurnaceTime.planNative((AbstractFurnaceBlockEntity)(Object)this,maximumTicks);}
    @Inject(method="serverTick",at=@At("HEAD"))
    private static void machines$observe(Level level,BlockPos pos,BlockState state,AbstractFurnaceBlockEntity m,CallbackInfo ci){NativeFurnaceTime.before(m);}
}
