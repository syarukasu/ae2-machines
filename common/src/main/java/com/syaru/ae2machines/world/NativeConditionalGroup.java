package com.syaru.ae2machines.world;

import java.util.*;
import java.util.function.BooleanSupplier;
import net.minecraft.world.level.block.entity.TickingBlockEntity;

/** Members remain asleep only while their native shared driver and individual clock deadlines are unchanged. */
final class NativeConditionalGroup {
    static final class Slot {
        final int position;final TickingBlockEntity ticker;boolean removed;
        Slot(int position,TickingBlockEntity ticker){this.position=position;this.ticker=ticker;}
    }
    final List<Slot> members=new ArrayList<>();
    final BooleanSupplier driver;
    boolean dormant;
    private boolean value;
    private long deadline;
    NativeConditionalGroup(BooleanSupplier driver){this.driver=driver;}
    void refresh(){
        dormant=false;long next=Long.MAX_VALUE;
        for(var slot:members){
            var member=(NativeConditionalTicker)slot.ticker;
            if(slot.removed || !member.quiet())return;
            next=Math.min(next,member.deadline());
        }
        value=driver.getAsBoolean();deadline=next;dormant=true;
    }
    void observe(long now,boolean valid){if(dormant && (!valid || now>deadline || driver.getAsBoolean()!=value))dormant=false;}
    int after(int position){
        int lo=0,hi=members.size();
        while(lo<hi){int mid=(lo+hi)>>>1;if(members.get(mid).position<=position)lo=mid+1;else hi=mid;}
        return lo;
    }
}
