package com.syaru.ae2machines.mixin;

import appeng.api.networking.IGrid;
import appeng.me.Grid;
import com.syaru.ae2machines.runtime.NativeGridState;
import com.syaru.ae2machines.world.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=Grid.class,remap=false)
public abstract class NativeGridServicesMixin {
    @Inject(method={"onServerStartTick","onServerEndTick"},at=@At("HEAD"),cancellable=true)
    private void machines$pauseOwnedGrid(CallbackInfo ci){if(((IGrid)this).getService(NativeGridState.class).paused())ci.cancel();}
    @Inject(method={"onLevelStartTick","onLevelEndTick"},at=@At("HEAD"),cancellable=true)
    private void machines$pauseOwnedLevel(Level level,CallbackInfo ci) {
        if(level instanceof ServerLevel server && NativeCellWorlds.owns(level) && !NativeWorldRuntime.get(server).running())ci.cancel();
    }
}
