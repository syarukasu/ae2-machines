package com.syaru.ae2machines.mixin;
import net.minecraft.server.level.DistanceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(DistanceManager.class)
public interface ThermalTicketPurgeAccess {
    @Invoker("purgeStaleTickets") void machines$purgeNative();
}
