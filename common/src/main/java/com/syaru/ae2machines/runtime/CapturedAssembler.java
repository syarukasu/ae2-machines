package com.syaru.ae2machines.runtime;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.blockentity.crafting.MolecularAssemblerBlockEntity;
import com.syaru.ae2machines.platform.CapturedInventoryAccess;
import com.syaru.ae2machines.platform.PlatformData;
import net.minecraft.nbt.*;
import net.minecraft.core.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import java.util.function.*;
import java.math.BigInteger;
import java.util.*;

/** Only adjacency and persistence are replaced; AE2 owns crafting and AE power extraction. */
public final class CapturedAssembler extends MolecularAssemblerBlockEntity {
    private final Runnable persist;
    private Consumer<Runnable> atomic;
    private Supplier<java.math.BigInteger> speed=()->java.math.BigInteger.ONE;
    private Supplier<BigInteger> parallel=()->BigInteger.ONE;
    private CapturedAssembler parent;
    private final List<CapturedAssembler> workers=new ArrayList<>();
    private final ArrayDeque<CapturedAssembler> availableWorkers=new ArrayDeque<>();
    private int lastNativeSteps;
    private boolean quiet;
    private final BooleanSupplier enabled;
    private final BiFunction<Long,Direction,MEStorage> endpoints;
    private final MEStorage storage;
    private final MEStorage localStorage;
    public CapturedAssembler(BlockEntityType<?> type,BlockPos pos,BlockState state,Runnable persist,
            Consumer<Runnable> atomic,BooleanSupplier enabled,BiFunction<Long,Direction,MEStorage> endpoints) {
        super(type,pos,state);
        this.persist=persist;this.atomic=atomic;this.enabled=enabled;this.endpoints=endpoints;
        localStorage=CapturedInventoryAccess.internal(getExposedInventoryForSide(null),this::saveChanges);
        storage=new MEStorage() {
            @Override public long insert(AEKey key,long amount,Actionable mode,IActionSource source) {
                return transfer(key,amount,mode,source,true);
            }
            @Override public long extract(AEKey key,long amount,Actionable mode,IActionSource source) {
                return transfer(key,amount,mode,source,false);
            }
            @Override public void getAvailableStacks(KeyCounter out) {
                localStorage.getAvailableStacks(out);
                for(var child:workers)child.localStorage.getAvailableStacks(out);
            }
            @Override public net.minecraft.network.chat.Component getDescription(){return localStorage.getDescription();}
        };
        getMainNode().setInWorldNode(false);
    }
    @Override public void saveChanges(){if(!quiet && persist!=null && enabled.getAsBoolean())persist.run();}
    @Override public void markForUpdate(){}
    public void bindClock(Supplier<java.math.BigInteger> speed,Consumer<Runnable> atomic){this.speed=speed;this.atomic=atomic;}
    public void bindParallel(Supplier<BigInteger> parallel) {
        this.parallel=parallel;
        if(BigInteger.valueOf(workers.size()+1L).compareTo(parallel.get())>0)throw new IllegalStateException("SAVED_ASSEMBLER_LANES_EXCEED_CONFIGURATION");
        wakeForWorkers();
    }
    public IGrid powerGrid(){return (parent==null?this:parent).getMainNode().getGrid();}
    public boolean isVirtualWorker(){return parent!=null;}
    private boolean ownsWork() {
        if(acceptsPlans() && getCurrentPattern()!=null || getCraftingProgress()>0)return true;
        for(int slot=0;slot<10;slot++)if(!getInternalInventory().getStackInSlot(slot).isEmpty())return true;
        return false;
    }
    public boolean parallelBusy(){return workers.stream().anyMatch(CapturedAssembler::ownsWork);}
    private void wakeForWorkers() {
        if(parallelBusy())getMainNode().ifPresent((grid,node)->grid.getTickManager().wakeDevice(node));
    }
    private CapturedAssembler worker() {
        var child=new CapturedAssembler(getType(),getBlockPos(),getBlockState(),persist,atomic,enabled,endpoints);
        child.quiet=true;
        child.parent=this;child.setLevel(getLevel());child.speed=()->speed.get();
        // Configuration is not stock. Avoid serializing the whole parent on every admission.
        for(var upgrade:getUpgrades())if(!upgrade.isEmpty())child.getUpgrades().addItems(upgrade.copy());
        if(!acceptsPlans())child.getInternalInventory().setItemDirect(10,getInternalInventory().getStackInSlot(10).copy());
        child.quiet=false;
        return child;
    }
    private long transfer(AEKey key,long amount,Actionable mode,IActionSource source,boolean insert) {
        if(amount<=0)return 0;
        if(mode.isSimulate())return transferNow(key,amount,mode,source,insert);
        var accepted=new long[1];
        atomic.accept(()->{accepted[0]=transferNow(key,amount,mode,source,insert);if(accepted[0]>0){wakeForWorkers();saveChanges();}});
        return accepted[0];
    }
    private long transferNow(AEKey key,long amount,Actionable mode,IActionSource source,boolean insert) {
        long left=amount-(insert?localStorage.insert(key,amount,mode,source):localStorage.extract(key,amount,mode,source));
        for(var child:workers) {
            if(left==0)break;
            left-=insert?child.localStorage.insert(key,left,mode,source):child.localStorage.extract(key,left,mode,source);
        }
        // Empty fixed-pattern lanes are created only as real material arrives. SIMULATE owns no new state.
        if(insert && left>0 && parent==null && !acceptsPlans()) {
            var room=parallel.get().subtract(BigInteger.valueOf(workers.size()+1L));
            if(room.signum()>0) {
                var child=worker();long capacity=child.localStorage.insert(key,left,Actionable.SIMULATE,source);
                if(mode.isSimulate())left-=BigInteger.valueOf(capacity).multiply(room).min(BigInteger.valueOf(left)).longValueExact();
                else while(capacity>0 && left>0 && room.signum()>0) {
                    workers.add(child);
                    long accepted=child.localStorage.insert(key,left,mode,source);
                    if(accepted<=0) {workers.remove(workers.size()-1);break;}
                    left-=accepted;room=room.subtract(BigInteger.ONE);
                    if(left>0 && room.signum()>0){child=worker();capacity=child.localStorage.insert(key,left,Actionable.SIMULATE,source);}
                }
            }
        }
        return amount-left;
    }
    @Override public boolean pushPattern(IPatternDetails pattern,KeyCounter[] table,Direction side) {
        if(super.pushPattern(pattern,table,side))return true;
        if(parent!=null || !acceptsPlans())return false;
        while(!availableWorkers.isEmpty())if(availableWorkers.removeFirst().pushPattern(pattern,table,side)){wakeForWorkers();return true;}
        if(BigInteger.valueOf(workers.size()+1L).compareTo(parallel.get())>=0)return false;
        var child=worker();workers.add(child);
        if(!child.pushPattern(pattern,table,side)){workers.remove(child);return false;}
        wakeForWorkers();saveChanges();return true;
    }
    public CompoundTag saveCaptured() {
        var tag=PlatformData.saveBlock(this);
        if(!workers.isEmpty()){var list=new ListTag();for(var child:workers)if(child.ownsWork())list.add(PlatformData.saveBlock(child));if(!list.isEmpty())tag.put("ae2machines:parallelAssembly",list);}
        return tag;
    }
    public void loadCaptured(CompoundTag tag) {
        PlatformData.loadBlock(this,tag);workers.clear();availableWorkers.clear();
        for(var value:tag.getList("ae2machines:parallelAssembly",Tag.TAG_COMPOUND)) {
            var child=worker();PlatformData.loadBlock(child,(CompoundTag)value);child.getTickingRequest(null);workers.add(child);if(!child.ownsWork())availableWorkers.add(child);
        }
    }
    public void trimIdleWorkers(){if(parallelBusy())throw new IllegalStateException("ASSEMBLER_HAS_PARALLEL_WORK");workers.clear();availableWorkers.clear();}
    public int lastNativeSteps(){return lastNativeSteps;}
    @Override public TickRateModulation tickingRequest(IGridNode node,int ticks) {
        if(!enabled.getAsBoolean())return TickRateModulation.IDLE;
        var result=new TickRateModulation[1];
        atomic.accept(()->{
            // Native progress is integral and completes at 100. Stop on completion, blocking or no paid progress.
            // Never multiply ticks (native power arithmetic is int) or synthesize recipe outputs/remainders.
            result[0]=nativeSteps(node,ticks);
            for(var child:workers)if(child.ownsWork())child.nativeSteps(node,ticks);
            availableWorkers.clear();
            for(var child:workers)if(!child.ownsWork())availableWorkers.add(child);
            if(parallelBusy()) {
                wakeForWorkers();result[0]=TickRateModulation.FASTER;
            }
            saveChanges();
        });
        return result[0];
    }
    private TickRateModulation nativeSteps(IGridNode node,int ticks) {
        int steps=speed.get().min(BigInteger.valueOf(100)).intValueExact();
        if(steps<1)throw new IllegalStateException("INVALID_ASSEMBLER_SPEED");
        lastNativeSteps=0;TickRateModulation result=TickRateModulation.SLEEP;
        for(int i=0;i<steps;i++) {
            int before=getCraftingProgress();result=super.tickingRequest(node,i==0?ticks:1);lastNativeSteps++;
            if(result!=TickRateModulation.FASTER || getCraftingProgress()<=before)break;
        }
        return result;
    }
    public MEStorage storage(){return storage;}
    public ItemStack pushCaptured(ItemStack stack,Direction side) {
        if(stack.isEmpty())return stack;
        var target=endpoints.apply(getBlockPos().relative(side).asLong(),side.getOpposite());
        if(target==null)return stack;
        long accepted=target.insert(AEItemKey.of(stack),stack.getCount(),Actionable.MODULATE,IActionSource.ofMachine(parent==null?this:parent));
        if(accepted<0 || accepted>stack.getCount())throw new IllegalStateException("ASSEMBLER_INVALID_INSERTION");
        var remaining=stack.copy();remaining.shrink((int)accepted);return remaining;
    }
}
