package com.syaru.ae2machines.mixin;
import com.syaru.ae2machines.world.*;
import net.minecraft.server.level.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(ServerChunkCache.class)
abstract class ThermalTicketSweepMixin {
    @Redirect(method="tick(Ljava/util/function/BooleanSupplier;Z)V",at=@At(value="INVOKE",target="Lnet/minecraft/server/level/DistanceManager;purgeStaleTickets()V"))
    private void machines$purgeDue(DistanceManager manager){
        var world=(ServerLevel)((ServerChunkCache)(Object)this).getLevel();
        if(NativeCellWorlds.owns(world) && NativeWorldRuntime.get(world).skipEmptyTicketSweep((ThermalTicketChecks)manager))return;
        ((ThermalTicketPurgeAccess)manager).machines$purgeNative();
    }
}
