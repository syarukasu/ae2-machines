package com.syaru.ae2machines.compat.mek;

import java.math.BigDecimal;
import java.util.*;
import mekanism.common.lib.multiblock.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** One shared native thermal state; Q changes work, never duplicates stored assets. */
public final class FissionParallelState {
    public interface Access {
        FissionParallelState machines$fissionGroups();
        Frame machines$fissionRead(int copies);
        void machines$fissionWrite(Frame frame);
        boolean machines$fissionActive();
        void machines$fissionActive(boolean active);
        double machines$fissionBurned();
        double machines$fissionRate();
        long machines$fissionFuel();
        long machines$fissionBoiled();
        void machines$fissionTotals(double burned,long boiled);
    }
    public record Frame(int copies,double heat,double pending,double fuelRemainder,double wasteRemainder,
                        double damage,boolean disabled) {
        public Frame {
            if(copies<1 || !Double.isFinite(heat) || !Double.isFinite(pending)
                    || !Double.isFinite(fuelRemainder) || !Double.isFinite(wasteRemainder)
                    || !Double.isFinite(damage) || heat<0 || fuelRemainder<0 || wasteRemainder<0 || damage<0)
                throw new IllegalArgumentException("INVALID_FISSION_COHORT");
        }
        static Frame empty(int copies){return new Frame(copies,0,0,0,0,0,false);}
        Frame portion(int count){
            double fraction=(double)count/copies;
            return new Frame(count,heat*fraction,pending*fraction,fuelRemainder*fraction,wasteRemainder*fraction,damage,disabled);
        }
        Frame remainder(Frame part){return new Frame(copies-part.copies,heat-part.heat,pending-part.pending,
                fuelRemainder-part.fuelRemainder,wasteRemainder-part.wasteRemainder,damage,disabled);}
        CompoundTag save(){
            var tag=new CompoundTag();tag.putInt("copies",copies);tag.putDouble("heat",heat);tag.putDouble("pending",pending);
            tag.putDouble("fuel",fuelRemainder);tag.putDouble("waste",wasteRemainder);tag.putDouble("damage",damage);tag.putBoolean("disabled",disabled);return tag;
        }
        static Frame load(CompoundTag tag){return new Frame(tag.getInt("copies"),tag.getDouble("heat"),tag.getDouble("pending"),
                tag.getDouble("fuel"),tag.getDouble("waste"),tag.getDouble("damage"),tag.getBoolean("disabled"));}
    }
    private final List<Frame> groups=new ArrayList<>();
    private Frame current;
    private int currentIndex=-1;
    private boolean restorePrimary;
    private long nativeCalls;
    private int admitted=-1;
    public boolean initialized(){return !groups.isEmpty();}
    public int representatives(){return groups.size();}
    public long nativeCalls(){return nativeCalls;}
    public int heatLanes(int fallback){return current!=null?current.copies:initialized()?groups.get(0).copies:fallback;}
    public int workLanes(int fallback){return admitted>=0?admitted:fallback;}
    public int copies(){int count=0;for(var group:groups)count=Math.addExact(count,group.copies);return count;}
    private void sync(Access data){
        if(initialized() && current==null){
            if(restorePrimary){data.machines$fissionWrite(groups.get(0));restorePrimary=false;}
            groups.set(0,data.machines$fissionRead(groups.get(0).copies));
        }
    }
    public void configure(Access data,int previous,int requested){
        if(current!=null)throw new IllegalStateException("FISSION_CONFIGURED_DURING_NATIVE_STEP");
        if(!initialized())groups.add(data.machines$fissionRead(previous));else sync(data);
        var frame=groups.get(0);
        if(requested>frame.copies)groups.set(0,new Frame(requested,frame.heat,frame.pending,
                frame.fuelRemainder,frame.wasteRemainder,frame.damage,frame.disabled));
        // Park unused capacity on shrink. Neither expanding nor shrinking grants free heat.
    }
    public static void configure(List<BlockEntity> blocks,int previous,int requested){
        var seen=Collections.newSetFromMap(new IdentityHashMap<MultiblockData,Boolean>());
        for(var block:blocks)if(block instanceof IMultiblock<?> tile && tile.getMultiblock() instanceof Access access
                && tile.getMultiblock().isFormed() && seen.add(tile.getMultiblock()))
            access.machines$fissionGroups().configure(access,previous,requested);
    }
    public static int heatLanes(BlockEntity block){
        int fallback=MekWorldParallel.lanes(block);
        if(MekRestorationStorage.projecting())return 1;
        return block instanceof IMultiblock<?> tile && tile.getMultiblock() instanceof Access access
                ?access.machines$fissionGroups().heatLanes(fallback):fallback;
    }
    public static int workLanes(MultiblockData data){
        int fallback=MekStructureParallel.lanes(data);
        return data instanceof Access access?access.machines$fissionGroups().workLanes(fallback):fallback;
    }
    public static boolean tick(MultiblockData data,Level world){
        if(!(data instanceof Access access) || !com.syaru.ae2machines.world.NativeCellWorlds.owns(world))return data.tick(world);
        MekStructureParallel.prepare(data,world);
        return access.machines$fissionGroups().advance(data,access,world,MekStructureParallel.lanes(data));
    }
    private boolean advance(MultiblockData nativeData,Access data,Level world,int limit){
        configure(data,limit,limit);
        boolean enabled=data.machines$fissionActive(),changed=false,disabled=false;
        double burned=0;long boiled=0;
        try{
            var frame=groups.get(0);
            double rate=data.machines$fissionRate();
            double fuel=data.machines$fissionFuel()+frame.fuelRemainder;
            // Complete workers plus at most one partial worker; native burnFuel applies the exact remainder.
            admitted=!enabled || frame.disabled || rate<=0?0:(int)Math.min(limit,Math.ceil(fuel/rate));
            current=frame;currentIndex=0;data.machines$fissionWrite(frame);
            data.machines$fissionActive(enabled && !frame.disabled);
            changed=nativeData.tick(world);nativeCalls++;
            var after=data.machines$fissionRead(frame.copies);groups.set(0,after);disabled=after.disabled;
            burned=data.machines$fissionBurned();boiled=data.machines$fissionBoiled();
        }finally{
            if(currentIndex>=0)groups.set(currentIndex,data.machines$fissionRead(current.copies));
            current=null;currentIndex=-1;admitted=-1;
            data.machines$fissionWrite(groups.get(0));
            data.machines$fissionActive(enabled && !disabled);
            data.machines$fissionTotals(burned,boiled);
        }
        return changed;
    }
    public CompoundTag save(Access data){
        if(!initialized())return new CompoundTag();
        sync(data);var tag=new CompoundTag();tag.putInt("schema",2);
        // Native meltdown/cache callbacks can checkpoint the currently installed representative.
        var list=new ListTag();for(int i=0;i<groups.size();i++)list.add((i==currentIndex?data.machines$fissionRead(current.copies):groups.get(i)).save());
        tag.put("groups",list);return tag;
    }
    public void load(CompoundTag tag){
        if(current!=null)throw new IllegalStateException("FISSION_RESTORE_DURING_NATIVE_STEP");
        var loaded=new ArrayList<Frame>();
        if(!tag.isEmpty()){
            if(tag.getInt("schema")!=1 && tag.getInt("schema")!=2)throw new IllegalArgumentException("UNKNOWN_FISSION_COHORT_SCHEMA");
            for(var value:tag.getList("groups",Tag.TAG_COMPOUND))loaded.add(Frame.load((CompoundTag)value));
            if(loaded.isEmpty())throw new IllegalArgumentException("EMPTY_FISSION_COHORTS");
            int sum=0;double damage=0;boolean disabled=false;
            var heat=BigDecimal.ZERO;var pending=BigDecimal.ZERO;var fuel=BigDecimal.ZERO;var waste=BigDecimal.ZERO;
            for(var frame:loaded){
                sum=Math.addExact(sum,frame.copies);damage=Math.max(damage,frame.damage);disabled|=frame.disabled;
                heat=heat.add(BigDecimal.valueOf(frame.heat));pending=pending.add(BigDecimal.valueOf(frame.pending));
                fuel=fuel.add(BigDecimal.valueOf(frame.fuelRemainder));waste=waste.add(BigDecimal.valueOf(frame.wasteRemainder));
            }
            loaded.clear();loaded.add(new Frame(sum,heat.doubleValue(),pending.doubleValue(),fuel.doubleValue(),waste.doubleValue(),damage,disabled));
        }
        groups.clear();groups.addAll(loaded);restorePrimary=!loaded.isEmpty();
    }
    public List<Frame> frames(Access data){sync(data);return List.copyOf(groups);}
    public BigDecimal heat(Access data){sync(data);var sum=BigDecimal.ZERO;for(var frame:groups)sum=sum.add(BigDecimal.valueOf(frame.heat));return sum;}
    public BigDecimal pending(Access data){sync(data);var sum=BigDecimal.ZERO;for(var frame:groups)sum=sum.add(BigDecimal.valueOf(frame.pending));return sum;}
    public BigDecimal keptHeat(Access data){sync(data);var frame=groups.get(0);return BigDecimal.valueOf(frame.heat/frame.copies);}
    public BigDecimal keptPending(Access data){sync(data);var frame=groups.get(0);return BigDecimal.valueOf(frame.pending/frame.copies);}
    public void project(Access data,double heat,double pending){
        sync(data);double fuel=0,waste=0,damage=0;boolean disabled=false;
        for(var frame:groups){fuel+=frame.fuelRemainder;waste+=frame.wasteRemainder;damage=Math.max(damage,frame.damage);disabled|=frame.disabled;}
        var primary=new Frame(1,heat,pending,fuel,waste,damage,disabled);groups.clear();groups.add(primary);data.machines$fissionWrite(primary);
    }
}
