package com.syaru.ae2machines.runtime;
import java.util.concurrent.atomic.AtomicLong;
public final class FactoryRecipes {
    private static final AtomicLong REVISION=new AtomicLong();
    private FactoryRecipes() {}
    public static long revision() { return REVISION.get(); }
    public static void changed() { REVISION.incrementAndGet(); }
}
