package com.syaru.ae2machines.world;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Native-order ticking holders, independent of installed machine mods or thermal contracts. */
final class NativeChunkSchedule<T> {
    private final Predicate<T> tickingNow,required;
    private Object source;
    private List<T> ticking;
    private long epoch=Long.MIN_VALUE,revision=Long.MIN_VALUE;
    private int hostTick=Integer.MIN_VALUE;
    private int candidates;
    private long generation;

    NativeChunkSchedule(Predicate<T> tickingNow,Predicate<T> required){this.tickingNow=tickingNow;this.required=required;}

    boolean stable(Object sourceIdentity,long currentEpoch,long currentRevision,int currentHostTick){
        return ticking!=null && source==sourceIdentity && epoch==currentEpoch
                && revision==currentRevision && hostTick==currentHostTick;
    }
    int filteredCandidates(){return ticking==null?0:candidates-ticking.size();}
    int tickingCount(){return ticking==null?0:ticking.size();}
    long generation(){return generation;}

    Iterable<T> select(Object sourceIdentity,long currentEpoch,long currentRevision,int currentHostTick,Iterable<T> original){
        if(!stable(sourceIdentity,currentEpoch,currentRevision,currentHostTick)){
            generation++;
            source=sourceIdentity;epoch=currentEpoch;revision=currentRevision;
            hostTick=currentHostTick;
            var found=new ArrayList<T>();boolean ready=true;candidates=0;
            for(var holder:original){
                candidates++;
                if(tickingNow.test(holder))found.add(holder);
                else if(required.test(holder))ready=false;
            }
            ticking=ready?List.copyOf(found):null;
        }
        if(ticking==null)return original;
        return ticking;
    }
}
