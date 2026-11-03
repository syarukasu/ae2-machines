package com.syaru.ae2machines.compat.aco;

import com.syaru.ae2craftingoptimizer.api.craftingtable.*;
import com.syaru.ae2machines.block.BaseAccelerator;
import com.syaru.ae2machines.runtime.*;
import java.util.*;

/** Optional bridge to the existing ACO BigInteger physical receipt lifecycle. */
public interface ExactNativeWorker extends CraftingTableBatchTarget {
    private NativeWorldBatches batches(){return ((BaseAccelerator)this).factoryHandle() instanceof NativeWorldSession session?session.batches():null;}
    private MekNativeRequests variable(){return ((BaseAccelerator)this).factoryHandle() instanceof NativeWorldSession session?session.variable():null;}
    default boolean aco$supportsExactPattern(appeng.api.crafting.IPatternDetails pattern){var b=batches();var v=variable();return b!=null && b.supportsExact(pattern) || v!=null && v.supportsExact(pattern);}
    @Override default boolean aco$acceptCraftingTableBatch(CraftingTableBatchRequest request) {
        if(request.mode()!=CraftingTableBatchMode.BIG_INTEGER_JOB)return false;
        var b=batches();var v=variable();
        if(b!=null && (b.owns(request.transactionId(),request.payloadDigest()) || (v==null || !v.hasId(request.transactionId())) && b.supportsExact(request.pattern())))
            return b.accept(request.transactionId(),request.payloadDigest(),request.pattern(),request.executions(),request.aggregateInputTotals(),request.aggregateExpectedOutputs(),false,"");
        return v!=null && v.acceptExact(new com.syaru.ae2machines.api.VariableProcessingTarget.Request(request.transactionId(),request.payloadDigest(),request.pattern(),request.executions(),request.aggregateInputTotals()),request.aggregateExpectedOutputs());
    }
    @Override default boolean aco$ownsCraftingTableBatch(UUID id,String digest){var b=batches();var v=variable();return b!=null && b.owns(id,digest) || v!=null && v.exactReceipt(id,digest).isPresent() || b==null && (durable(id,digest).flatMap(r->r.find(id,digest)).isPresent() || durableVariable(id,digest).isPresent());}
    private Optional<MekNativeRequests.ClaimedReceipt> durableVariable(UUID id,String digest){
        if(!NativeMachines.chemicals())return Optional.empty();
        var host=(BaseAccelerator)this;
        return host.factoryId()!=null && host.getLevel() instanceof net.minecraft.server.level.ServerLevel level
                ?MekNativeRequests.durableExactReceipt(level,host.factoryId(),id,digest):Optional.empty();
    }
    private Optional<ProcessingRuntime> durable(UUID id,String digest) {
        var host=(BaseAccelerator)this;
        return host.factoryId()!=null && host.getLevel() instanceof net.minecraft.server.level.ServerLevel level
                ?NativeWorldBatches.savedRuntime(level,host.factoryId(),id,digest):Optional.empty();
    }
    @Override default Optional<CraftingTableBatchSnapshot> aco$craftingTableBatchSnapshot(UUID id,String digest) {
        var b=batches();var v=variable();
        var actual=v==null?durableVariable(id,digest):v.exactReceipt(id,digest);
        if(actual.isPresent()){var receipt=actual.get();return Optional.of(new CraftingTableBatchSnapshot(id,digest,CraftingTableBatchSnapshot.State.valueOf(receipt.state().name()),receipt.outputs().isEmpty()?0:1,1,receipt.outputs(),receipt.detail()));}
        if(b==null)return durable(id,digest).flatMap(r->r.find(id,digest)).map(job->new CraftingTableBatchSnapshot(id,digest,CraftingTableBatchSnapshot.State.QUARANTINED,0,1,Map.of(),"native_world_reconciliation_required"));
        return b.find(id,digest).map(job->{
            if(b.quarantined(id))return new CraftingTableBatchSnapshot(id,digest,CraftingTableBatchSnapshot.State.QUARANTINED,0,1,Map.of(),"native_world_transfer_unproven");
            if(!b.receiptReady(id))return new CraftingTableBatchSnapshot(id,digest,CraftingTableBatchSnapshot.State.RUNNING,0,1,Map.of(),"native_side_effects_pending");
            return new CraftingTableBatchSnapshot(id,digest,CraftingTableBatchSnapshot.State.valueOf(job.state().name()),
                    job.receipt().isEmpty()?0:1,1,job.receipt(),"actual_cell_machine_native_receipt");
        });
    }
    @Override default boolean aco$acknowledgeCraftingTableBatch(UUID id,String digest){var b=batches();var v=variable();return v!=null && v.hasId(id)?v.acknowledgeExact(id,digest):b!=null && b.acknowledge(id,digest);}
    @Override default boolean aco$forgetCraftingTableBatch(UUID id,String digest){var b=batches();var v=variable();return v!=null && v.hasId(id)?v.forget(id,digest):b!=null && b.forget(id,digest);}
    @Override default boolean aco$cancelCraftingTableBatch(UUID id,String digest){var b=batches();var v=variable();return v!=null && v.hasId(id)?v.cancelExact(id,digest):b!=null && b.cancel(id,digest);}
}
