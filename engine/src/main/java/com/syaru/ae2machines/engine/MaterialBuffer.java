package com.syaru.ae2machines.engine;

import java.util.Objects;

/** A single native-capacity pipe balance. Callers journal this together with both endpoints. */
public final class MaterialBuffer<K> {
    public record Snapshot<K>(K key,long amount) {}
    private final long capacity;
    private K key;
    private long amount;
    public MaterialBuffer(long capacity,Snapshot<K> saved) {
        if(capacity<=0 || saved.amount()<0 || saved.amount()>capacity
                || saved.amount()>0 && saved.key()==null)throw new IllegalArgumentException("Invalid pipe balance");
        this.capacity=capacity;amount=saved.amount();key=amount==0?null:saved.key();
    }
    public Snapshot<K> snapshot(){return new Snapshot<>(key,amount);}
    public long insert(K offered,long count,boolean simulate) {
        Objects.requireNonNull(offered);
        if(count<=0 || key!=null && !key.equals(offered))return 0;
        long accepted=Math.min(count,capacity-amount);
        if(accepted>0 && !simulate){key=offered;amount+=accepted;}
        return accepted;
    }
    public void delivered(K expected,long count) {
        if(count<0 || count>amount || count>0 && !Objects.equals(key,expected))
            throw new IllegalArgumentException("Pipe delivery exceeds owned balance");
        amount-=count;if(amount==0)key=null;
    }
}
