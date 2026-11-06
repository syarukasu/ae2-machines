package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.compat.mek.MekThermalHooks;
import com.syaru.ae2machines.world.NativeTicketWindow;
import com.syaru.ae2machines.world.ThermalTicketChecks;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.server.level.*;
import net.minecraft.util.SortedArraySet;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(DistanceManager.class)
abstract class ThermalTicketCalendarMixin implements ThermalTicketChecks {
    @Shadow @Final private Long2ObjectOpenHashMap<SortedArraySet<Ticket<?>>> tickets;
    @Shadow private long ticketTickCounter;
    @Unique private Object machines$scope;
    @Unique private long machines$through;

    @Override public boolean machines$skipEmptySweep(Object scope,int offered){
        if(ticketTickCounter<0 || ticketTickCounter>Long.MAX_VALUE-offered)return false;
        if(scope!=machines$scope || ticketTickCounter>=machines$through){
            if(!MekThermalHooks.safe(getClass()) || !MekThermalHooks.safe(Ticket.class) || !MekThermalHooks.safe(TicketType.class))return false;
            int quiet=offered;
            for(var group:tickets.values()){
                if(group.isEmpty())return false; // vanilla removes empty table entries
                for(var ticket:group){
                    if(ticket.getType().getClass()!=TicketType.class)return false;
                    quiet=NativeTicketWindow.quiet(ticketTickCounter,quiet,((ThermalTicketAccess)(Object)ticket)::machines$expiresBy);
                    if(quiet==0){machines$scope=null;return false;}
                }
            }
            machines$scope=scope;machines$through=ticketTickCounter+quiet;
        }
        ++ticketTickCounter;return true;
    }
    @Inject(method={"addTicket(JLnet/minecraft/server/level/Ticket;)V","removeTicket(JLnet/minecraft/server/level/Ticket;)V","removeTicketsOnClosing"},at=@At("HEAD"))
    private void machines$ticketChange(CallbackInfo ci){machines$scope=null;}
    @Inject(method="getTickets",at=@At("RETURN"))
    private void machines$newBucket(long pos,CallbackInfoReturnable<SortedArraySet<Ticket<?>>> ci){
        if(machines$scope!=null && ci.getReturnValue().isEmpty())machines$scope=null;
    }
}
