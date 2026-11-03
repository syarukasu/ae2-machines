package com.syaru.ae2machines.api;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import java.math.BigInteger;
import java.util.*;

/** Server-thread-only actual-result contract. The older ACO exact-output receipt contract is unchanged. */
public interface VariableProcessingTarget {
    int API_VERSION=1;
    record Request(UUID id,String digest,IPatternDetails pattern,BigInteger executions,Map<AEKey,BigInteger> ownedInputs){
        public Request {Objects.requireNonNull(id);Objects.requireNonNull(pattern);if(digest==null || digest.isBlank() || digest.length()>256)throw new IllegalArgumentException("Invalid request identity");
            com.syaru.ae2machines.engine.ExactAmounts.positive(executions);ownedInputs=com.syaru.ae2machines.engine.ExactAmounts.freeze(ownedInputs);}
    }
    enum State { RUNNING, OUTPUT_READY, ACKNOWLEDGED, CANCELLED, QUARANTINED }
    record Snapshot(UUID id,String digest,State state,BigInteger executions,BigInteger completed,
                    Map<AEKey,BigInteger> consumed,Map<AEKey,BigInteger> produced,Map<AEKey,BigInteger> unusedInputs,Map<AEKey,BigInteger> deliveredToME,
                    BigInteger energyUsed,BigInteger energyCredit,String energyUnit){
        public Snapshot{consumed=Map.copyOf(consumed);produced=Map.copyOf(produced);unusedInputs=Map.copyOf(unusedInputs);deliveredToME=Map.copyOf(deliveredToME);}
    }
    /** Caller reserves inputs once; true transfers their ownership, never extracts ME stock again. */
    boolean acceptVariableProcessing(Request request);
    Optional<Snapshot> variableProcessingSnapshot(UUID id,String digest);
    /** Only pre-work cancellation succeeds. Partial irreversible work must finish. */
    boolean cancelVariableProcessing(UUID id,String digest);
    /** Caller durably received both actual outputs AND unused inputs before acknowledging. */
    boolean acknowledgeVariableProcessing(UUID id,String digest);
    /** Alternative to caller receipt collection: durably deliver actual outputs/refunds to the host ME. */
    boolean deliverVariableProcessingToME(UUID id,String digest);
    boolean forgetVariableProcessing(UUID id,String digest);
}
