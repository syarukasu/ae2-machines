package com.syaru.ae2machines.world;

import java.util.function.IntConsumer;

/** A proven event-free prefix; the event itself must still execute its native world tick. */
public record NativeQuietStep(int limit,IntConsumer advance,Runnable rollback) {
    public NativeQuietStep {if(limit<0)throw new IllegalArgumentException("Negative quiet span");}
}
