package com.syaru.ae2machines.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.*;
import appeng.crafting.inv.ICraftingInventory;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import com.syaru.ae2machines.runtime.CountedCraftingProvider;
import com.syaru.ae2machines.runtime.FixedBatchPattern;
import com.syaru.ae2machines.world.ExternalHandoffs;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.*;

/** Keeps AE2 extraction/accounting and protects count-scaled factory admissions with durable recovery. */
@Mixin(value=CraftingCpuLogic.class,remap=false)
public abstract class CraftingCpuHandoffMixin {
    @Shadow @Final private CraftingCPUCluster cluster;
    @Shadow private ExecutingCraftingJob job;
    @Unique private Map<Long,CompoundTag> machines$before=Map.of();
    @Unique private ExternalHandoffs.Transaction machines$pending;
    @Unique private boolean machines$ticking;
    @Unique private FixedBatchPattern machines$batch;
    @Unique private TaskProgressAccess machines$task;
    @Unique private Set<CountedCraftingProvider> machines$receivers=Set.of();
    @Unique private int machines$share,machines$completed,machines$extra;
    @Unique private boolean machines$scaled;

    @Inject(method="executeCrafting",at=@At("HEAD"),require=1)
    private void machines$offer(int maxPatterns,CraftingService service,IEnergyService energy,Level level,CallbackInfoReturnable<Integer> callback) {
        machines$batch=null;machines$task=null;machines$receivers=Set.of();
        machines$share=0;machines$completed=0;machines$extra=0;machines$scaled=false;
    }

    @Inject(method="tickCraftingLogic",at=@At("HEAD"),require=1)
    private void machines$beginTick(IEnergyService energy,CraftingService service,CallbackInfo callback) {
        if(machines$ticking || machines$pending!=null)throw new ExternalHandoffs.PersistenceFailure(new IllegalStateException("Unresolved CPU handoff"));
        machines$ticking=true;
    }
    @Inject(method="tickCraftingLogic",at=@At("RETURN"),require=1)
    private void machines$endTick(IEnergyService energy,CraftingService service,CallbackInfo callback) {
        machines$finish();machines$before=Map.of();machines$ticking=false;
    }

    @Unique private void machines$finish() {
        if(machines$pending!=null){machines$pending.commit();machines$pending=null;}
    }
    @Redirect(method="executeCrafting",at=@At(value="INVOKE",target="Lappeng/crafting/execution/CraftingCpuHelper;extractPatternInputs(Lappeng/api/crafting/IPatternDetails;Lappeng/crafting/inv/ICraftingInventory;Lnet/minecraft/world/level/Level;Lappeng/api/stacks/KeyCounter;Lappeng/api/stacks/KeyCounter;)[Lappeng/api/stacks/KeyCounter;",ordinal=0),require=1)
    private KeyCounter[] machines$firstInputs(IPatternDetails pattern,ICraftingInventory inventory,Level world,KeyCounter outputs,KeyCounter containers,
                                         int maxPatterns,CraftingService service,IEnergyService energy,Level level) {
        machines$share=0;machines$receivers=Set.of();
        int limit=machines$remaining(pattern,maxPatterns);
        if(limit>=2) {
            var receivers=new LinkedHashSet<CountedCraftingProvider>();int room=limit;boolean supported=true;
            for(var provider:service.getProviders(pattern)) {
                var factory=CountedCraftingProvider.resolve(provider,pattern);
                if(factory==null){supported=false;break;}
                if(factory.isBusy())continue;
                int capacity=factory.normalBatchLimit(pattern,limit);
                if(capacity<2){supported=false;break;}
                receivers.add(factory);room=Math.min(room,capacity);
            }
            var cpu=(CraftingCpuLogic)(Object)this;
            var available=supported && !receivers.isEmpty()?FixedBatchPattern.offer(pattern,limit,inventory,energy,cpu::getWaitingFor):null;
            if(available!=null) {
                int share=Math.min(room,available.count()/receivers.size());
                if(share>=2 && receivers.stream().map(CountedCraftingProvider::handoffOwner).distinct().allMatch(ExternalHandoffs::supports)) {
                    machines$share=share;machines$receivers=receivers;
                }
            }
        }
        return machines$inputs(pattern,inventory,world,outputs,containers,maxPatterns,service,energy);
    }
    @Redirect(method="executeCrafting",at=@At(value="INVOKE",target="Lappeng/crafting/execution/CraftingCpuHelper;extractPatternInputs(Lappeng/api/crafting/IPatternDetails;Lappeng/crafting/inv/ICraftingInventory;Lnet/minecraft/world/level/Level;Lappeng/api/stacks/KeyCounter;Lappeng/api/stacks/KeyCounter;)[Lappeng/api/stacks/KeyCounter;",ordinal=1),require=1)
    private KeyCounter[] machines$nextInputs(IPatternDetails pattern,ICraftingInventory inventory,Level world,KeyCounter outputs,KeyCounter containers,
                                         int maxPatterns,CraftingService service,IEnergyService energy,Level level) {
        return machines$inputs(pattern,inventory,world,outputs,containers,maxPatterns,service,energy);
    }
    @Unique private int machines$remaining(IPatternDetails pattern,int maxPatterns) {
        var value=job==null?null:((ExecutingJobAccess)job).machines$tasks().get(pattern);
        machines$task=value==null?null:(TaskProgressAccess)value;
        return machines$task==null?0:(int)Math.max(0,Math.min(maxPatterns-machines$completed,machines$task.machines$count()));
    }
    @Unique private KeyCounter[] machines$inputs(IPatternDetails pattern,ICraftingInventory inventory,Level world,KeyCounter outputs,KeyCounter containers,
                                                int maxPatterns,CraftingService service,IEnergyService energy) {
        machines$batch=null;
        int limit=machines$remaining(pattern,maxPatterns);
        if(limit==0)return null;
        if(machines$share>=2)machines$batch=FixedBatchPattern.offer(pattern,Math.min(limit,machines$share),inventory,energy,((CraftingCpuLogic)(Object)this)::getWaitingFor);
        // Continue only across providers protected by the same complete CPU/factory before-image.
        boolean sameOwner=machines$pending!=null,hasFactory=false;
        for(var provider:service.getProviders(pattern)) {
            var factory=CountedCraftingProvider.resolve(provider,pattern);
            if(factory!=null) {
                hasFactory=true;
                if(machines$pending!=null && !machines$pending.owns(factory))sameOwner=false;
            } else sameOwner=false;
        }
        if(!sameOwner) {
            machines$finish();machines$before=hasFactory?ExternalHandoffs.cpuBefore(cluster):Map.of();
        }
        return CraftingCpuHelper.extractPatternInputs(machines$batch==null?pattern:machines$batch.view(),inventory,world,outputs,containers);
    }
    @Redirect(method="executeCrafting",at=@At(value="INVOKE",target="Lappeng/api/networking/crafting/ICraftingProvider;pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)Z"),require=1)
    private boolean machines$push(ICraftingProvider provider,IPatternDetails pattern,KeyCounter[] holders) {
        var factory=CountedCraftingProvider.resolve(provider,pattern);
        if(factory==null) {
            if(machines$pending!=null)throw new ExternalHandoffs.PersistenceFailure(new IllegalStateException("Provider changed during CPU batch"));
            boolean accepted=provider.pushPattern(pattern,holders);if(accepted)machines$completed++;
            return accepted;
        }
        if(machines$pending!=null) {
            if(!machines$pending.owns(factory))throw new ExternalHandoffs.PersistenceFailure(new IllegalStateException("Provider changed during CPU batch"));
            try {return machines$accept(factory,pattern,holders);}
            catch(RuntimeException failure){throw new ExternalHandoffs.PersistenceFailure(failure);}
        }
        if(machines$before.isEmpty())throw new IllegalStateException("Missing CPU before-image for factory handoff");
        ExternalHandoffs.Transaction transaction;
        try {transaction=ExternalHandoffs.begin(factory,machines$before);}
        catch(ExternalHandoffs.PersistenceFailure failure) {
            CraftingCpuHelper.reinjectPatternInputs(((CraftingCpuLogic)(Object)this).getInventory(),holders);
            for(var holder:holders)holder.clear();
            throw failure;
        }
        if(factory.nativeWorldHandoff() && transaction==null)return false;
        try {
            boolean accepted=machines$accept(factory,pattern,holders);
            if(transaction!=null) {
                if(accepted)machines$pending=transaction;else transaction.declined();
            }
            return accepted;
        } catch(RuntimeException failure) {
            if(transaction!=null)throw new ExternalHandoffs.PersistenceFailure(failure);
            throw failure;
        }
    }
    @Unique private boolean machines$accept(CountedCraftingProvider factory,IPatternDetails pattern,KeyCounter[] holders) {
        if(machines$batch==null) {
            boolean accepted=factory.pushPattern(pattern,holders);if(accepted)machines$completed++;
            return accepted;
        }
        if(!machines$receivers.contains(factory))return false;
        int count=machines$batch.count();
        if(!factory.pushPatterns(pattern,holders,count))return false;
        // AE2 subtracts the final one, posts all scaled waiting outputs and charges scaled input power.
        machines$task.machines$count(Math.subtractExact(machines$task.machines$count(),count-1L));
        machines$completed=Math.addExact(machines$completed,count);machines$extra=count-1;machines$scaled=true;return true;
    }
    @ModifyVariable(method="executeCrafting",at=@At(value="INVOKE",target="Lappeng/me/cluster/implementations/CraftingCPUCluster;markDirty()V"),index=6,require=1)
    private int machines$usedOperations(int pushedPatterns) {
        int result=Math.addExact(pushedPatterns,machines$extra);machines$extra=0;return result;
    }
    @Inject(method="executeCrafting",at=@At("RETURN"),require=1)
    private void machines$accounted(int maxPatterns,CraftingService service,IEnergyService energy,Level level,CallbackInfoReturnable<Integer> callback) {
        if(machines$scaled && callback.getReturnValueI()!=machines$completed)
            throw new ExternalHandoffs.PersistenceFailure(new IllegalStateException("Unexpected batched CPU accounting"));
        machines$batch=null;machines$task=null;machines$receivers=Set.of();machines$share=0;machines$scaled=false;
        if(!machines$ticking){machines$finish();machines$before=Map.of();}
    }
}
