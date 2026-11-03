package com.syaru.ae2machines.engine;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;

/** Splits actual owned assets without manufacturing an expected output or crediting it twice. */
public record ReceiptPartition<K>(Map<K,BigInteger> claimed,Map<K,BigInteger> surplus) {
    public ReceiptPartition {claimed=ExactAmounts.freeze(claimed);surplus=ExactAmounts.freeze(surplus);}
    public static <K> ReceiptPartition<K> of(Map<K,BigInteger> actual,Map<K,BigInteger> claimed){
        var left=new LinkedHashMap<>(ExactAmounts.freeze(actual));
        for(var e:ExactAmounts.freeze(claimed).entrySet()){
            var n=left.getOrDefault(e.getKey(),BigInteger.ZERO).subtract(e.getValue());
            if(n.signum()<0)throw new IllegalStateException("Actual receipt is below the claimed output");
            if(n.signum()==0)left.remove(e.getKey());else left.put(e.getKey(),n);
        }
        return new ReceiptPartition<>(claimed,left);
    }
    public Map<K,BigInteger> undeliveredSurplus(Map<K,BigInteger> delivered){return of(surplus,delivered).surplus();}
}
