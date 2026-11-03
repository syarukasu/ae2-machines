package com.syaru.ae2machines.world;

import com.syaru.ae2machines.platform.*;
import com.syaru.ae2machines.runtime.*;
import java.math.BigInteger;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Owns the original sender's complete emission, including its post-receive debit. */
public final class NativePowerPushes {
    private static final ThreadLocal<Scope> CURRENT=new ThreadLocal<>();
    private NativePowerPushes(){}
    public static Scope begin(BlockEntity source){
        if(!(source.getLevel() instanceof ServerLevel level) || NativeWorldRuntime.owns(level))return null;
        var target=NativeEnergySources.exclusiveFactoryOutput(source);
        if(target==null || !(target.factoryHandle() instanceof NativeWorldSession session) || !session.ownedFeOnly())return null;
        if(CURRENT.get()!=null)throw new ExternalHandoffs.PersistenceFailure(new IllegalStateException("Nested native power emitter"));
        var scope=new Scope(source,session);CURRENT.set(scope);return scope;
    }
    public static void beforeCredit(NativeWorldSession session,int offered){
        var scope=CURRENT.get();
        if(scope==null || scope.session!=session || scope.transaction!=null || session.receiveFE(offered,session.world().getGameTime(),true)==0)return;
        scope.transaction=session.beginOwnedPower(List.of(scope.source));
    }
    public static boolean covers(NativeWorldSession session){var scope=CURRENT.get();return scope!=null && scope.session==session && scope.transaction!=null;}
    public static void afterCredit(NativeWorldSession session,int accepted){var scope=CURRENT.get();if(scope!=null && scope.session==session && scope.transaction!=null)scope.accepted=scope.accepted.add(BigInteger.valueOf(accepted));}
    public static final class Scope implements AutoCloseable {
        final BlockEntity source;final NativeWorldSession session;final BigInteger before,unit;
        ExternalHandoffs.Transaction transaction;BigInteger accepted=BigInteger.ZERO;boolean closed;
        Scope(BlockEntity source,NativeWorldSession session){this.source=source;this.session=session;before=NativeMachines.energy(source);unit=MekanismAdapter.idleProfile(source).unitsPerFE();}
        @Override public void close(){
            if(closed || CURRENT.get()!=this)throw new IllegalStateException("Native emitter scope ownership changed");
            try{
                if(transaction!=null){
                    var debit=before.subtract(NativeMachines.energy(source));
                    if(!debit.equals(accepted.multiply(unit)))throw new ExternalHandoffs.PersistenceFailure(new IllegalStateException("Native sender debit differs from receiver credit"));
                    transaction.commit();
                }
            }finally{closed=true;CURRENT.remove();}
        }
    }
}
