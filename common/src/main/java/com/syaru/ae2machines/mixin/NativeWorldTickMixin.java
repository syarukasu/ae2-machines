package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.world.NativeWorldAccess;
import com.syaru.ae2machines.world.NativeWorldRuntime;
import java.util.function.BooleanSupplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class NativeWorldTickMixin implements NativeWorldAccess {
    @Unique private NativeWorldRuntime ae2machines$world;
    @Inject(method = "<init>", at = @At("RETURN"))
    private void ae2machines$installClock(CallbackInfo ci) {
        var level=(ServerLevel)(Object)this;
        if(!NativeWorldRuntime.owns(level))return;
        var access=(NativeServerWorldAccess)this;
        var data=new com.syaru.ae2machines.world.NativeWorldLevelData(level.getServer().getWorldData(),
                access.ae2machines$serverData(),ae2machines$nativeWorld().state());
        access.ae2machines$serverData(data);
        ((NativeWorldLevelAccess)this).ae2machines$levelData(data);
    }
    @Override public NativeWorldRuntime ae2machines$nativeWorld() {
        if (ae2machines$world == null)
            ae2machines$world = new NativeWorldRuntime((ServerLevel) (Object) this);
        return ae2machines$world;
    }
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void ae2machines$pause(BooleanSupplier time, CallbackInfo ci) {
        var level = (ServerLevel) (Object) this;
        if (!NativeWorldRuntime.owns(level)) return;
        if (!ae2machines$nativeWorld().running()) {
            // Keep chunk IO serviced, but do not consume scheduled events or tick blocks/entities.
            level.getChunkSource().tick(time, false);
            ((NativeServerWorldAccess)this).ae2machines$entities().tick();
            ci.cancel();
        } else {
            level.resetEmptyTime();
        }
    }
    @Inject(method = "tickTime", at = @At("HEAD"), cancellable = true)
    private void ae2machines$clock(CallbackInfo ci) {
        var level = (ServerLevel) (Object) this;
        if (!NativeWorldRuntime.owns(level)) return;
        ae2machines$nativeWorld().state().advance(level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT));
        ci.cancel();
    }
}
