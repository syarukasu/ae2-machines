package com.syaru.ae2machines.mixin;

import appeng.api.networking.IGridNode;
import appeng.me.service.TickManagerService;
import appeng.me.service.helpers.TickTracker;
import appeng.api.networking.ticking.*;
import com.syaru.ae2machines.world.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import java.util.ArrayDeque;
import java.util.PriorityQueue;

/** Only factory-world queues use the factory clock. Ordinary ME grids retain AE2's original clock. */
@Mixin(value=TickManagerService.class,remap=false)
public abstract class NativeGridTicksMixin {
    @Shadow private long currentTick;
    @Shadow private void tickQueue(PriorityQueue<TickTracker> queue){throw new AssertionError();}
    @Unique private final ArrayDeque<Long> machines$clocks=new ArrayDeque<>();
    @Redirect(method="tickLevelQueue",at=@At(value="INVOKE",target="Lappeng/me/service/TickManagerService;tickQueue(Ljava/util/PriorityQueue;)V"))
    private void machines$nativeQueue(TickManagerService service,PriorityQueue<TickTracker> queue,Level level) {
        boolean nativeWorld=level instanceof ServerLevel && NativeCellWorlds.owns(level);
        if(nativeWorld && !NativeWorldRuntime.get((ServerLevel)level).running())return;
        if(!nativeWorld && machines$clocks.isEmpty()){tickQueue(queue);return;}
        long global=machines$clocks.isEmpty()?currentTick:machines$clocks.getLast();
        machines$clocks.push(currentTick);currentTick=nativeWorld?level.getGameTime():global;
        try{tickQueue(queue);}finally{currentTick=machines$clocks.pop();}
    }
    @Redirect(method="addNode",at=@At(value="NEW",target="appeng/me/service/helpers/TickTracker"))
    private TickTracker machines$nativeRegistration(TickingRequest request,IGridNode node,IGridTickable tickable,long time) {
        var level=node.getLevel();
        long global=machines$clocks.isEmpty()?time:machines$clocks.getLast();
        return new TickTracker(request,node,tickable,level!=null && NativeCellWorlds.owns(level)?level.getGameTime():global);
    }
}
