package com.syaru.ae2machines.world;

import java.util.function.IntConsumer;

/** A proven event-free prefix; the event itself must still execute its native world tick. */
public record NativeQuietStep(int limit,IntConsumer advance,Runnable rollback) {
    /** A read-only quote expired before commit. Roll back applied steps, then resume native ticking. */
    public static final class StateChanged extends IllegalStateException {
        public StateChanged(String reason){super(reason);}
    }
    public NativeQuietStep {if(limit<0)throw new IllegalArgumentException("Negative quiet span");}
    /** Local independent states share a horizon and one existing owned-work transaction. */
    public static NativeQuietStep batch(int offered,java.util.List<NativeQuietStep> steps,
            java.util.function.ObjIntConsumer<Runnable> transaction,Runnable rollback){
        if(offered<0)throw new IllegalArgumentException("Negative quiet offer");
        var members=java.util.List.copyOf(steps);int limit=offered;
        for(var step:members){
            if(step.limit()>offered)throw new IllegalArgumentException("NATIVE_TIME_INTERVAL_EXCEEDS_OFFER");
            limit=Math.min(limit,step.limit());
        }
        int accepted=limit;
        return new NativeQuietStep(accepted,n->{
            if(n<=0 || n>accepted)throw new IllegalArgumentException("INVALID_NATIVE_TIME_ADVANCE");
            transaction.accept(()->{for(var step:members)step.advance().accept(n);},n);
        },rollback);
    }
}
