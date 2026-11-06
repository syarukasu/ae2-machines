package com.syaru.ae2machines.world;

import java.util.function.LongPredicate;

public final class NativeTicketWindow {
    private NativeTicketWindow(){}
    /** Only the native monotone expiry predicate decides the boundary. */
    public static int quiet(long now,int offered,LongPredicate expires){
        if(now<0 || offered<=0 || now>Long.MAX_VALUE-offered)return 0;
        if(!expires.test(now+offered))return offered;
        int lo=0,hi=offered;
        while(hi-lo>1){
            int m=lo+(hi-lo)/2;
            if(expires.test(now+m))hi=m;else lo=m;
        }
        return lo;
    }
}
