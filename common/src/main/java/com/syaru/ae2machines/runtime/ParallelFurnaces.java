package com.syaru.ae2machines.runtime;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import com.syaru.ae2machines.engine.Recipe;
import com.syaru.ae2machines.mixin.FurnaceClockAccess;
import com.syaru.ae2machines.platform.*;
import net.minecraft.core.Direction;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.math.BigInteger;
import java.util.*;

/** Equal native states share a representative, not a fabricated recipe or result. */
public final class ParallelFurnaces implements BufferedMachine {
    private static final BigInteger ONE=BigInteger.ONE, ZERO=BigInteger.ZERO;
    private final AbstractFurnaceBlockEntity template;
    private final Runnable changed;
    private List<Group> groups=new ArrayList<>();
    private BigInteger count=ONE;
    private long lastCalls;
    private record Group(AbstractFurnaceBlockEntity furnace,BigInteger count,BigInteger remaining) {}
    private record Identity(BlockState state,CompoundTag data,BigInteger remaining) {}

    public ParallelFurnaces(AbstractFurnaceBlockEntity template,CompoundTag saved,Runnable changed) {
        this.template=template;this.changed=changed;
        if(saved.isEmpty())groups.add(new Group(template,ONE,ZERO));
        else {
            if(saved.getInt("schema")!=1)throw new IllegalArgumentException("Unknown furnace cohort schema");
            count=positive(saved.getByteArray("count"));BigInteger total=ZERO;
            for(var value:saved.getList("groups",Tag.TAG_COMPOUND)) {
                var tag=(CompoundTag)value;var n=positive(tag.getByteArray("count"));
                var remaining=new BigInteger(tag.getByteArray("remaining"));
                if(remaining.signum()<0)throw new IllegalArgumentException("Negative furnace work");
                var state=net.minecraft.nbt.NbtUtils.readBlockState(template.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK),tag.getCompound("state"));
                if(state.getBlock()!=template.getBlockState().getBlock())throw new IllegalArgumentException("Furnace type changed");
                groups.add(new Group(create(state,tag.getCompound("data")),n,remaining));total=total.add(n);
            }
            if(!total.equals(count))throw new IllegalArgumentException("Furnace cohort count mismatch");
        }
    }
    private static BigInteger positive(byte[] value) {
        var n=new BigInteger(value);if(n.signum()<=0)throw new IllegalArgumentException("Nonpositive furnace count");return n;
    }
    private AbstractFurnaceBlockEntity create(BlockState state,CompoundTag data) {
        var be=(AbstractFurnaceBlockEntity)((EntityBlock)state.getBlock()).newBlockEntity(template.getBlockPos(),state);
        be.setLevel(template.getLevel());PlatformData.loadBlock(be,data.copy());return be;
    }
    private AbstractFurnaceBlockEntity copy(AbstractFurnaceBlockEntity be) {return create(be.getBlockState(),PlatformData.saveBlock(be));}
    public BigInteger count(){return count;}
    public long lastCalls(){return lastCalls;}
    public int stateCount(){return groups.size();}
    public boolean pending(){return groups.stream().anyMatch(g->g.remaining.signum()>0);}
    public void configure(BigInteger next) {
        if(next.signum()<=0)throw new IllegalArgumentException("Nonpositive furnace count");
        if(next.equals(count))return;
        var single=restorable();if(single==null)throw new IllegalStateException("FURNACE_PARALLEL_STATE_NOT_DRAINED");
        groups=new ArrayList<>();groups.add(new Group(single,ONE,ZERO));count=next;
        if(next.compareTo(ONE)>0) {
            var blank=(AbstractFurnaceBlockEntity)((EntityBlock)single.getBlockState().getBlock()).newBlockEntity(single.getBlockPos(),single.getBlockState().setValue(net.minecraft.world.level.block.AbstractFurnaceBlock.LIT,false));
            blank.setLevel(single.getLevel());groups.add(new Group(blank,next.subtract(ONE),ZERO));
        }
        merge();changed.run();
    }
    public void advance(BigInteger speed) {
        if(speed.signum()<=0)throw new IllegalArgumentException("Nonpositive furnace speed");
        if(!pending())groups=groups.stream().map(g->new Group(g.furnace,g.count,speed)).toList();
        var work=new ArrayDeque<>(groups);var done=new ArrayList<Group>();lastCalls=0;
        while(!work.isEmpty()) {
            var g=work.removeFirst();
            if(g.remaining.signum()==0 || lastCalls>=512){done.add(g);continue;}
            // Custom recipe classes may have per-call effects. Never multiply their execution.
            if(g.count.compareTo(ONE)>0 && !FurnaceRecipeAccess.parallelSafe(g.furnace)) {
                work.addFirst(new Group(g.furnace,g.count.subtract(ONE),g.remaining));
                g=new Group(copy(g.furnace),ONE,g.remaining);
            }
            var allowance=g.remaining.min(speed);
            var slice=FurnaceClock.advance(g.furnace,allowance,512-lastCalls);lastCalls+=slice.calls();
            done.add(new Group(g.furnace,g.count,g.remaining.subtract(allowance).add(slice.remaining())));
        }
        groups=done;merge();changed.run();
    }
    private void merge() {
        var merged=new LinkedHashMap<Identity,Group>();
        for(var g:groups) {
            var key=new Identity(g.furnace.getBlockState(),PlatformData.saveBlock(g.furnace),g.remaining);
            var old=merged.get(key);merged.put(key,old==null?g:new Group(old.furnace,old.count.add(g.count),old.remaining));
        }
        groups=new ArrayList<>(merged.values());
    }
    @Override public MEStorage storage(Direction side) {
        return new MEStorage() {
            @Override public long insert(AEKey key,long amount,Actionable mode,IActionSource source){return transfer(key,amount,mode,source,side,true);}
            @Override public long extract(AEKey key,long amount,Actionable mode,IActionSource source){return transfer(key,amount,mode,source,side,false);}
            @Override public void getAvailableStacks(KeyCounter out) {
                var totals=new HashMap<AEKey,BigInteger>();
                for(var g:groups) {
                    var local=new KeyCounter();VanillaFurnaces.buffer(g.furnace,()->{}).storage(side).getAvailableStacks(local);
                    for(var e:local)totals.merge(e.getKey(),BigInteger.valueOf(e.getLongValue()).multiply(g.count),BigInteger::add);
                }
                totals.forEach((key,n)->out.add(key,n.min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact()));
            }
            @Override public Component getDescription(){return template.getBlockState().getBlock().getName();}
        };
    }
    private long transfer(AEKey key,long amount,Actionable mode,IActionSource source,Direction side,boolean insert) {
        if(amount<=0)return 0;
        if(insert)return insertDistributed(key,amount,mode,source,side);
        long left=amount;var updated=new ArrayList<Group>();
        var ordered=new ArrayList<>(groups);
        for(var g:ordered) {
            var local=VanillaFurnaces.buffer(g.furnace,()->{}).storage(side);
            long capacity=insert?local.insert(key,Long.MAX_VALUE,Actionable.SIMULATE,source):local.extract(key,Long.MAX_VALUE,Actionable.SIMULATE,source);
            long take=BigInteger.valueOf(capacity).multiply(g.count).min(BigInteger.valueOf(left)).longValueExact();
            if(mode.isSimulate() || take==0)updated.add(g);
            else {
                var split=BigInteger.valueOf(take).divideAndRemainder(g.count);
                addTransfer(updated,g,g.count.subtract(split[1]),split[0].longValueExact(),key,source,side,insert);
                addTransfer(updated,g,split[1],split[0].longValueExact()+1,key,source,side,insert);
            }
            left-=take;
        }
        if(!mode.isSimulate() && left!=amount){groups=updated;merge();changed.run();}
        return amount-left;
    }
    private long insertDistributed(AEKey key,long amount,Actionable mode,IActionSource source,Direction side) {
        BigInteger available=ZERO;
        for(var g:groups)available=available.add(BigInteger.valueOf(VanillaFurnaces.buffer(g.furnace,()->{}).storage(side)
                .insert(key,Long.MAX_VALUE,Actionable.SIMULATE,source)).multiply(g.count));
        long accepted=available.min(BigInteger.valueOf(amount)).longValueExact();
        if(mode.isSimulate() || accepted==0)return accepted;
        long left=accepted;
        while(left>0) {
            var ordered=new ArrayList<>(groups);
            // Prefer useful pairings, but do not put the whole fuel delivery in the first occupied lane.
            ordered.sort(Comparator.comparingInt((Group g)->side==Direction.UP
                    ? (!g.furnace.getItem(1).isEmpty() || ((FurnaceClockAccess)g.furnace).machines$lit()>0?0:1)
                    : (!g.furnace.getItem(0).isEmpty()?0:1))
                    .thenComparingInt(g->g.furnace.getItem(side==Direction.UP?0:1).getCount()));
            var capacity=new IdentityHashMap<Group,Long>();BigInteger eligible=ZERO;
            for(var g:ordered) {
                long n=VanillaFurnaces.buffer(g.furnace,()->{}).storage(side).insert(key,Long.MAX_VALUE,Actionable.SIMULATE,source);
                capacity.put(g,n);if(n>0)eligible=eligible.add(g.count);
            }
            if(eligible.signum()==0)throw new IllegalStateException("Furnace insertion capacity changed");
            long share=BigInteger.valueOf(left).divide(eligible).max(ONE).longValueExact();
            var updated=new ArrayList<Group>();
            for(var g:ordered) {
                long take=BigInteger.valueOf(Math.min(capacity.get(g),share)).multiply(g.count)
                        .min(BigInteger.valueOf(left)).longValueExact();
                if(take==0)updated.add(g);
                else {
                    var split=BigInteger.valueOf(take).divideAndRemainder(g.count);
                    addTransfer(updated,g,g.count.subtract(split[1]),split[0].longValueExact(),key,source,side,true);
                    addTransfer(updated,g,split[1],split[0].longValueExact()+1,key,source,side,true);
                }
                left-=take;
            }
            groups=updated;merge();
        }
        changed.run();return accepted;
    }
    private void addTransfer(List<Group> out,Group g,BigInteger n,long amount,AEKey key,IActionSource source,Direction side,boolean insert) {
        if(n.signum()==0)return;
        if(amount==0){out.add(new Group(g.furnace,n,g.remaining));return;}
        var be=copy(g.furnace);var local=VanillaFurnaces.buffer(be,()->{}).storage(side);
        long actual=insert?local.insert(key,amount,Actionable.MODULATE,source):local.extract(key,amount,Actionable.MODULATE,source);
        if(actual!=amount)throw new IllegalStateException("Furnace cohort transfer changed");
        out.add(new Group(be,n,g.remaining));
    }
    /** Collapse only if all native assets fit one real furnace; otherwise retain the spatial cell. */
    public AbstractFurnaceBlockEntity restorable() {
        if(pending())return null;
        if(count.equals(ONE))return copy(groups.get(0).furnace);
        Group active=null;var xp=new TreeMap<String,BigInteger>();var items=new ItemStack[]{ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY};
        for(var g:groups) {
            var clock=(FurnaceClockAccess)g.furnace;
            if(clock.machines$lit()>0 || clock.machines$progress()>0) {
                if(active!=null || !g.count.equals(ONE))return null;active=g;
            }
            for(int slot=0;slot<3;slot++) {
                var stack=g.furnace.getItem(slot);if(stack.isEmpty())continue;
                if(!items[slot].isEmpty() && !AEItemKey.of(stack).matches(items[slot]))return null;
                var total=BigInteger.valueOf(stack.getCount()).multiply(g.count).add(BigInteger.valueOf(items[slot].getCount()));
                if(total.compareTo(BigInteger.valueOf(Math.min(g.furnace.getMaxStackSize(),stack.getMaxStackSize())))>0)return null;
                items[slot]=stack.copy();items[slot].setCount(total.intValueExact());
            }
            var used=PlatformData.saveBlock(g.furnace).getCompound("RecipesUsed");
            for(var key:used.getAllKeys())xp.merge(key,BigInteger.valueOf(used.getInt(key)).multiply(g.count),BigInteger::add);
        }
        if(xp.values().stream().anyMatch(n->n.signum()<0 || n.compareTo(BigInteger.valueOf(Integer.MAX_VALUE))>0))return null;
        var result=copy(active==null?groups.get(0).furnace:active.furnace);
        for(int i=0;i<3;i++){int slot=i;DetachedFurnaceTick.mutate(result,()->result.setItem(slot,items[slot]));}
        // setItem can reset cooking progress. Restore the native counters after merging inventories.
        var base=(FurnaceClockAccess)(active==null?groups.get(0).furnace:active.furnace);
        ((FurnaceClockAccess)result).machines$progress(base.machines$progress());
        var data=PlatformData.saveBlock(result);
        var counters=PlatformData.saveBlock(active==null?groups.get(0).furnace:active.furnace);
        if(counters.contains("CookTimeTotal"))data.put("CookTimeTotal",counters.get("CookTimeTotal").copy());
        var used=new CompoundTag();xp.forEach((key,n)->used.putInt(key,n.intValueExact()));data.put("RecipesUsed",used);
        PlatformData.loadBlock(result,data);return result;
    }
    public CompoundTag save() {
        var tag=new CompoundTag();tag.putInt("schema",1);tag.putByteArray("count",count.toByteArray());var list=new ListTag();
        for(var g:groups){var entry=new CompoundTag();entry.putByteArray("count",g.count.toByteArray());entry.putByteArray("remaining",g.remaining.toByteArray());entry.put("state",NbtUtils.writeBlockState(g.furnace.getBlockState()));entry.put("data",PlatformData.saveBlock(g.furnace));list.add(entry);}
        tag.put("groups",list);return tag;
    }
    @Override public void tickNative(){advance(ONE);}
    @Override public BigInteger availableExecutions(Recipe<AEKey> recipe){return ZERO;}
    @Override public void takeInputs(Recipe<AEKey> recipe,BigInteger n){throw new IllegalStateException("Vanilla owns furnace inputs");}
    @Override public boolean putOutputs(Map<AEKey,BigInteger> output){throw new IllegalStateException("Vanilla owns furnace outputs");}
    @Override public boolean ejecting(){return false;}
}
