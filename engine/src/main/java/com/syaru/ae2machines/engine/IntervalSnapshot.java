package com.syaru.ae2machines.engine;

import java.util.*;

/** Reuses a native-order work list only within an unchanged, explicitly owned interval. */
public final class IntervalSnapshot<T> {
    private Object source;
    private long revision;
    private List<T> original;
    private ArrayList<T> working;
    private boolean replay;
    private long builds,reuses;

    public ArrayList<T> begin(Object source,long revision,int capacity){
        if(this.source!=source || this.revision!=revision){original=null;this.source=source;this.revision=revision;}
        replay=original!=null;working=new ArrayList<>(replay?original.size():Math.max(0,capacity));
        if(replay){for(int i=0;i<original.size();i++)working.add(original.get(i));reuses++;}
        return working;
    }
    public boolean replay(){return replay;}
    public void beforeNativeOrder(List<T> list){
        if(list!=working)throw new IllegalArgumentException("INTERVAL_LIST_IDENTITY_CHANGED");
        if(original==null){original=List.copyOf(list);builds++;}
    }
    public void invalidate(){source=null;original=null;working=null;replay=false;}
    public long builds(){return builds;}
    public long reuses(){return reuses;}
}
