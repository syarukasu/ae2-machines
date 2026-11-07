package com.syaru.ae2machines.compat.mek;

import java.math.BigInteger;
import java.util.function.Supplier;

/** Scoped native transfer units. No native stack or callback escapes this scope. */
public final class MekFluidUnits {
    public static final BigInteger INT_MAX=BigInteger.valueOf(Integer.MAX_VALUE);
    private static final ThreadLocal<BigInteger> UNIT=ThreadLocal.withInitial(()->BigInteger.ONE);
    private MekFluidUnits(){}
    public static BigInteger unit(){return UNIT.get();}
    public static int view(BigInteger amount){return amount.max(BigInteger.ZERO).divide(unit()).min(INT_MAX).intValueExact();}
    public static BigInteger exact(int amount){return BigInteger.valueOf(amount).multiply(unit());}
    public static BigInteger covering(BigInteger amount){return amount.add(INT_MAX).subtract(BigInteger.ONE).divide(INT_MAX).max(BigInteger.ONE);}
    public static <T>T call(BigInteger unit,Supplier<T> operation){
        if(unit.signum()<=0)throw new IllegalArgumentException("Invalid native fluid unit");
        var previous=UNIT.get();UNIT.set(unit);
        try{return operation.get();}finally{if(previous.equals(BigInteger.ONE))UNIT.remove();else UNIT.set(previous);}
    }
}
