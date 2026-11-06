package com.syaru.ae2machines.mixin;
import net.minecraft.server.level.Ticket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(Ticket.class)
public interface ThermalTicketAccess {
    @Invoker("timedOut") boolean machines$expiresBy(long tick);
}
