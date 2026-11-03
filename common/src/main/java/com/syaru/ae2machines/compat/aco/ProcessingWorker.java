package com.syaru.ae2machines.compat.aco;

import appeng.api.crafting.IPatternDetails;
import com.syaru.ae2craftingoptimizer.api.processing.*;
import com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchSnapshot;
import com.syaru.ae2machines.runtime.ProcessingRuntime;
import java.util.Optional;
import java.util.UUID;

/** This interface and its implementing BE load only when the new ACO API exists. */
public interface ProcessingWorker extends ProcessingPatternBatchTarget {
    ProcessingRuntime machineRuntime();
    default java.util.List<ProcessingRuntime> machineRuntimes() { return java.util.List.of(machineRuntime()); }
    private Optional<ProcessingRuntime> owner(UUID id) { return machineRuntimes().stream().filter(r->r.tracks(id)).findFirst(); }
    @Override default boolean aco$supportsProcessingPattern(IPatternDetails pattern) {
        return machineRuntimes().stream().anyMatch(r->r.resolve(pattern).isPresent());
    }
    @Override default boolean aco$acceptProcessingPatternBatch(ProcessingPatternBatchRequest request) {
        var known=owner(request.transactionId());
        var candidates=known.map(java.util.List::of).orElseGet(this::machineRuntimes);
        for(var runtime:candidates) if(runtime.accept(request.transactionId(), request.payloadDigest(), request.pattern(),
                request.executions(), request.aggregateInputTotals(), request.aggregateExpectedOutputs())) return true;
        return false;
    }
    @Override default boolean aco$ownsProcessingPatternBatch(UUID id, String digest) {
        return owner(id).flatMap(r->r.find(id,digest)).isPresent();
    }
    @Override default Optional<CraftingTableBatchSnapshot> aco$processingPatternBatchSnapshot(UUID id, String digest) {
        return owner(id).flatMap(runtime->runtime.find(id,digest).map(job -> {
            if (runtime.quarantined())
                return new CraftingTableBatchSnapshot(id,digest,CraftingTableBatchSnapshot.State.QUARANTINED,
                        0,1,java.util.Map.of(),"ae2machines:checkpoint_failure");
            var outputs = job.receipt();
            if(!runtime.deliveryReady(id))
                return new CraftingTableBatchSnapshot(id,digest,CraftingTableBatchSnapshot.State.RUNNING,
                        0,1,java.util.Map.of(),"ae2machines:native_completion_pending");
            return new CraftingTableBatchSnapshot(id,digest,
                    CraftingTableBatchSnapshot.State.valueOf(job.state().name()),
                    outputs.isEmpty() ? 0 : 1, 1, outputs, "ae2machines:paid_native_work");
        }));
    }
    @Override default boolean aco$acknowledgeProcessingPatternBatch(UUID id, String digest) {
        return owner(id).map(r->r.acknowledge(id,digest)).orElse(false);
    }
    @Override default boolean aco$forgetProcessingPatternBatch(UUID id, String digest) {
        return owner(id).map(r->r.forget(id,digest)).orElse(false);
    }
    @Override default boolean aco$cancelProcessingPatternBatch(UUID id, String digest) {
        return owner(id).map(r->r.cancel(id,digest)).orElse(false);
    }
}
