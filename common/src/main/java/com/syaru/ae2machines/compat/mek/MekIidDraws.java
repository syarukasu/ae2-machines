package com.syaru.ae2machines.compat.mek;

import com.syaru.ae2machines.engine.ExactBinomial;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.server.MinecraftServer;

/** Pure sampling has no world references and never blocks the server tick. */
public final class MekIidDraws {
    private static final Map<MinecraftServer,ExecutorService> WORKERS=new IdentityHashMap<>();
    private MekIidDraws(){}
    public static synchronized Future<BigInteger> submit(MinecraftServer server,BigInteger n,BigInteger p,long seed){
        var worker=WORKERS.computeIfAbsent(server,key->Executors.newSingleThreadExecutor(task->{
            var thread=new Thread(task,"ae2machines-exact-chance");thread.setDaemon(true);thread.setPriority(Thread.MIN_PRIORITY);return thread;
        }));
        return worker.submit(()->ExactBinomial.sample(n,p,BigInteger.ONE.shiftLeft(53),new Random(seed)));
    }
    public static synchronized void stopped(MinecraftServer server){var worker=WORKERS.remove(server);if(worker!=null)worker.shutdownNow();}
}
