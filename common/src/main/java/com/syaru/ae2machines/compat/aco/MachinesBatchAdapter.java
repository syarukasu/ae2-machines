package com.syaru.ae2machines.compat.aco;

import appeng.api.stacks.*;
import com.syaru.ae2craftingoptimizer.api.batch.*;
import com.syaru.ae2craftingoptimizer.api.batch.v2.*;
import com.syaru.ae2machines.platform.PlatformData;
import com.syaru.ae2machines.runtime.*;
import java.math.BigInteger;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/** ACO owns extraction/accounting; the captured factory owns native work and its durable receipt. */
public final class MachinesBatchAdapter implements TransactionalPatternBatchAdapter {
    public static final MachinesBatchAdapter INSTANCE=new MachinesBatchAdapter();
    public static final ResourceLocation ID=PlatformData.id("ae2machines:native_factory");
    private MachinesBatchAdapter(){}
    public static void register(){if(PatternBatchV2Api.adapter(ID).isEmpty())PatternBatchV2Api.registerAdapter(INSTANCE);}
    @Override public ResourceLocation id(){return ID;}
    @Override public int priority(){return 100;}
    @Override public BatchCpuAccountingMode cpuAccountingMode(){return BatchCpuAccountingMode.SINGLE_PHYSICAL_OPERATION;}
    @Override public BatchEnergyAccountingMode energyAccountingMode(){return BatchEnergyAccountingMode.SOURCE_LOGICAL_EXECUTIONS;}
    private static FactorySession factory(PatternBatchContext context) {
        if(!context.providerOwnedTarget() || !(context.provider() instanceof FactorySession session)
                || context.target()!=session.batchTarget() || context.level()!=session.handoffLevel())return null;
        return session;
    }
    @Override public boolean supports(PatternBatchContext context){var session=factory(context);return session!=null && session.transactionalLimit(context.pattern(),Long.MAX_VALUE)>=2;}
    @Override public long minimumExecutions(PatternBatchContext context){return 2;}
    @Override public long limitExecutions(PatternBatchContext context,long offered){var session=factory(context);return session==null?0:session.transactionalLimit(context.pattern(),offered);}
    @Override public PreparedPatternBatch prepare(PatternBatchContext context,PatternBatchBudget budget,UUID transaction) {
        if(!budget.hasTimeRemaining())return null;
        var session=factory(context);if(session==null)return null;
        long count=session.transactionalLimit(context.pattern(),budget.maximumExecutions());if(count<2)return null;
        var recipe=session.runtime().resolve(context.pattern()).orElseThrow();
        var data=new CompoundTag();data.putInt("schema",1);data.putUUID("factory",session.id());data.putInt("lane",session.laneIndex());
        data.putLong("recipes",FactoryRecipes.revision());data.putString("recipe",recipe.id());
        data.putString("pattern",PatternBatchIdentity.canonicalFingerprint(context));
        return new PreparedPatternBatch(transaction,count,stacks(recipe.inputs(),count),stacks(recipe.outputs(),count),data);
    }
    @Override public PatternBatchCommit commit(PatternBatchContext context,PreparedPatternBatch prepared) {
        var session=Objects.requireNonNull(factory(context),"Factory boundary changed");var data=prepared.adapterData();
        require(data.getInt("schema")==1 && data.getUUID("factory").equals(session.id()) && data.getInt("lane")==session.laneIndex(),"Factory identity changed");
        require(data.getString("pattern").equals(PatternBatchIdentity.canonicalFingerprint(context)),"Pattern identity changed");
        String digest=BatchPayloadFingerprint.of(prepared);
        if(session.batchReceipt(prepared.transactionId()).isEmpty() && data.getLong("recipes")!=FactoryRecipes.revision())return rejected(data);
        boolean accepted=session.acceptTransactional(prepared.transactionId(),digest,data.getString("pattern"),context.pattern(),prepared.offeredExecutions(),amounts(prepared.aggregateInputs()),amounts(prepared.expectedOutputs()));
        if(!accepted)return rejected(data);
        String owner="ae2machines:factory/"+session.id()+"/"+session.laneIndex();
        return new PatternBatchCommit(prepared.offeredExecutions(),owner,data,new BatchOwnershipProof(prepared.transactionId(),prepared.offeredExecutions(),digest,owner));
    }
    @Override public void rollback(PatternBatchContext context,PreparedPatternBatch prepared){}
    @Override public BatchRecoveryResult reconcileTarget(ServerLevel level,BatchTransactionRecord record) {
        var data=record.adapterData();
        if(data.getInt("schema")!=1 || !data.hasUUID("factory"))return result(BatchRecoveryResult.TargetState.QUARANTINE,0,"Unknown factory receipt schema");
        final Optional<CompoundTag> saved;
        try {saved=FactorySession.savedBatch(level,data.getUUID("factory"),data.getInt("lane"),record.id());}
        catch(RuntimeException unavailable){return result(BatchRecoveryResult.TargetState.QUARANTINE,0,"Factory journal unavailable");}
        if(saved.isEmpty())return result(BatchRecoveryResult.TargetState.NOT_ACCEPTED,0,"Atomic owned journal has no target acceptance");
        var receipt=saved.get();
        if(!receipt.getString("digest").equals(record.payloadDigest()) || !receipt.getString("pattern").equals(record.patternFingerprint()) || receipt.getLong("executions")!=record.offeredExecutions())return result(BatchRecoveryResult.TargetState.QUARANTINE,0,"Factory ownership identity differs");
        return result(BatchRecoveryResult.TargetState.ACCEPTED,record.offeredExecutions(),"Durable native-factory ownership");
    }
    @Override public void forgetResolvedTarget(PatternBatchContext context,UUID id){var session=factory(context);if(session!=null)session.forgetBatch(id);}
    @Override public void forgetResolvedTarget(ServerLevel level,BatchTransactionRecord record) {
        var data=record.adapterData();
        require(data.getInt("schema")==1 && data.hasUUID("factory"),"Unknown factory receipt schema");
        var saved=FactorySession.savedBatch(level,data.getUUID("factory"),data.getInt("lane"),record.id());
        if(saved.isPresent())require(saved.get().getString("digest").equals(record.payloadDigest()) && saved.get().getString("pattern").equals(record.patternFingerprint()),"Receipt identity changed before cleanup");
        FactorySession.forgetSavedBatch(level,data.getUUID("factory"),data.getInt("lane"),record.id(),record.targetPos());
    }
    private static PatternBatchCommit rejected(CompoundTag data){return new PatternBatchCommit(0,"",data);}
    private static BatchRecoveryResult result(BatchRecoveryResult.TargetState state,long count,String detail){return new BatchRecoveryResult(state,count,detail);}
    private static void require(boolean valid,String detail){if(!valid)throw new IllegalStateException(detail);}
    private static List<GenericStack> stacks(Map<AEKey,BigInteger> amounts,long count){return amounts.entrySet().stream().map(e->new GenericStack(e.getKey(),e.getValue().multiply(BigInteger.valueOf(count)).longValueExact())).toList();}
    private static Map<AEKey,BigInteger> amounts(List<GenericStack> stacks){var result=new LinkedHashMap<AEKey,BigInteger>();for(var stack:stacks){require(stack.amount()>0,"Nonpositive aggregate");result.merge(stack.what(),BigInteger.valueOf(stack.amount()),BigInteger::add);}return Map.copyOf(result);}
}
