package com.syaru.ae2machines.compat.aco;

import appeng.api.stacks.*;
import com.syaru.ae2craftingoptimizer.api.batch.*;
import com.syaru.ae2craftingoptimizer.api.batch.v2.*;
import com.syaru.ae2machines.block.BaseAccelerator;
import com.syaru.ae2machines.platform.PlatformData;
import com.syaru.ae2machines.runtime.*;
import java.math.BigInteger;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/** V2's long transport window; whole-order BigInteger requests use ExactNativeWorker. */
public final class AcoWorldBatchAdapter implements TransactionalPatternBatchAdapter {
    public static final AcoWorldBatchAdapter INSTANCE=new AcoWorldBatchAdapter();
    public static final ResourceLocation ID=PlatformData.id("ae2machines:native_world");
    public static void register(){if(PatternBatchV2Api.adapter(ID).isEmpty())PatternBatchV2Api.registerAdapter(INSTANCE);}
    @Override public ResourceLocation id(){return ID;}
    @Override public int priority(){return 110;}
    @Override public BatchCpuAccountingMode cpuAccountingMode(){return BatchCpuAccountingMode.SINGLE_PHYSICAL_OPERATION;}
    @Override public BatchEnergyAccountingMode energyAccountingMode(){return BatchEnergyAccountingMode.SOURCE_LOGICAL_EXECUTIONS;}
    private static NativeWorldBatches factory(PatternBatchContext context) {
        if(!context.providerOwnedTarget() || !(context.provider() instanceof NativeWorldProvider provider)
                || !(context.target() instanceof BaseAccelerator host) || context.level()!=host.getLevel()
                || !(host.factoryHandle() instanceof NativeWorldSession session))return null;
        var batches=NativeWorldBatches.get(provider.machines$providerEntity());
        return batches==session.batches()?batches:null;
    }
    @Override public boolean supports(PatternBatchContext context){return limitExecutions(context,Long.MAX_VALUE)>=2;}
    @Override public long minimumExecutions(PatternBatchContext context){return 2;}
    private static MekNativeRequests variable(PatternBatchContext context){return factory(context)!=null && context.target() instanceof BaseAccelerator host && host.factoryHandle() instanceof NativeWorldSession session?session.variable():null;}
    @Override public long limitExecutions(PatternBatchContext context,long offered){
        var b=factory(context);if(b==null || !(context.target() instanceof BaseAccelerator host)
                || !(host.factoryHandle() instanceof NativeWorldSession session)
                || !com.syaru.ae2machines.world.ExternalHandoffs.supportsNativeOutput(session))return 0;
        long fixed=b.limit(context.pattern(),offered);if(fixed>=2)return fixed;
        var v=variable(context);if(offered<2 || v==null || !v.supportsExact(context.pattern()))return 0;
        var n=BigInteger.valueOf(offered);for(var map:List.of(PatternProgram.inputTotals(context.pattern()),PatternProgram.outputTotals(context.pattern())))for(var amount:map.values())n=n.min(BigInteger.valueOf(Long.MAX_VALUE).divide(amount));return n.longValueExact();
    }
    @Override public PreparedPatternBatch prepare(PatternBatchContext context,PatternBatchBudget budget,UUID transaction) {
        if(!budget.hasTimeRemaining())return null;var b=factory(context);if(b==null)return null;
        long count=limitExecutions(context,budget.maximumExecutions());if(count<2)return null;
        boolean changing=b.limit(context.pattern(),count)<2;
        var data=new CompoundTag();data.putInt("schema",1);data.putUUID("factory",b.factoryId());
        data.putLong("recipes",FactoryRecipes.revision());data.putBoolean("variable",changing);
        data.putString("pattern",PatternBatchIdentity.canonicalFingerprint(context));
        if(changing)return payload(context,PatternProgram.inputTotals(context.pattern()),PatternProgram.outputTotals(context.pattern()),count,transaction,data);
        var recipe=b.target(context.pattern()).orElseThrow().profile().recipe();data.putString("recipe",recipe.id());
        return nativePayload(context,recipe,count,transaction,data);
    }
    static PreparedPatternBatch nativePayload(PatternBatchContext context,com.syaru.ae2machines.engine.Recipe<AEKey> recipe,long count,UUID transaction,CompoundTag data) {
        return payload(context,recipe.inputs(),recipe.outputs(),count,transaction,data);
    }
    private static PreparedPatternBatch payload(PatternBatchContext context,Map<AEKey,BigInteger> in,Map<AEKey,BigInteger> out,long count,UUID transaction,CompoundTag data) {
        var inputs=flatten(context.copyInputsPerExecution());var outputs=flatten(new KeyCounter[]{context.copyOutputsPerExecution()});
        require(context.copyRemainingOutputsPerExecution().isEmpty() && amounts(inputs).equals(in)
                && amounts(outputs).equals(out),"Native batch coefficients differ from AE2's concrete plan");
        // Retain AE2's actual keys and slot split. Equal chemical keys can serialize different
        // internal stack counts; rebuilding keys from a native output changes source identity.
        return new PreparedPatternBatch(transaction,count,stacks(inputs,count),stacks(outputs,count),data);
    }
    private static List<GenericStack> flatten(KeyCounter[] slots) {
        var result=new ArrayList<GenericStack>();
        for(var slot:slots)for(var entry:slot)result.add(new GenericStack(entry.getKey(),entry.getLongValue()));
        return List.copyOf(result);
    }
    @Override public PatternBatchCommit commit(PatternBatchContext context,PreparedPatternBatch prepared) {
        var b=Objects.requireNonNull(factory(context),"Native world boundary changed");var data=prepared.adapterData();
        require(data.getInt("schema")==1 && data.getUUID("factory").equals(b.factoryId()),"Native world identity changed");
        require(data.getString("pattern").equals(PatternBatchIdentity.canonicalFingerprint(context)),"Native pattern identity changed");
        var v=variable(context);boolean changing=data.getBoolean("variable");
        if((changing?(v==null?Optional.empty():v.v2Receipt(prepared.transactionId())):b.v2Receipt(prepared.transactionId())).isEmpty() && data.getLong("recipes")!=FactoryRecipes.revision())return new PatternBatchCommit(0,"",data);
        String digest=BatchPayloadFingerprint.of(prepared);
        boolean accepted=changing?v!=null && v.acceptV2(new com.syaru.ae2machines.api.VariableProcessingTarget.Request(prepared.transactionId(),digest,context.pattern(),BigInteger.valueOf(prepared.offeredExecutions()),amounts(prepared.aggregateInputs())),amounts(prepared.expectedOutputs()),data.getString("pattern"))
                :b.accept(prepared.transactionId(),digest,context.pattern(),BigInteger.valueOf(prepared.offeredExecutions()),amounts(prepared.aggregateInputs()),amounts(prepared.expectedOutputs()),true,data.getString("pattern"));
        if(!accepted)return new PatternBatchCommit(0,"",data);
        String owner="ae2machines:world/"+b.factoryId();
        return new PatternBatchCommit(prepared.offeredExecutions(),owner,data,new BatchOwnershipProof(prepared.transactionId(),prepared.offeredExecutions(),digest,owner));
    }
    @Override public void rollback(PatternBatchContext context,PreparedPatternBatch prepared){}
    @Override public BatchRecoveryResult reconcileTarget(ServerLevel level,BatchTransactionRecord record) {
        var data=record.adapterData();
        if(data.getInt("schema")!=1 || !data.hasUUID("factory"))return result(BatchRecoveryResult.TargetState.QUARANTINE,0,"Unknown native-world receipt schema");
        final Optional<CompoundTag> saved;
        try{saved=data.getBoolean("variable")?MekNativeRequests.savedV2Receipt(level,data.getUUID("factory"),record.id()):NativeWorldBatches.savedV2Receipt(level,data.getUUID("factory"),record.id());}
        catch(RuntimeException unavailable){return result(BatchRecoveryResult.TargetState.QUARANTINE,0,"Native-world journal unavailable");}
        if(saved.isEmpty())return result(BatchRecoveryResult.TargetState.NOT_ACCEPTED,0,"Atomic journal has no native-world acceptance");
        var receipt=saved.get();
        if(!receipt.getString("digest").equals(record.payloadDigest()) || !receipt.getString("pattern").equals(record.patternFingerprint()) || receipt.getLong("executions")!=record.offeredExecutions())return result(BatchRecoveryResult.TargetState.QUARANTINE,0,"Native-world ownership identity differs");
        return result(BatchRecoveryResult.TargetState.ACCEPTED,record.offeredExecutions(),"Durable actual-machine ownership");
    }
    @Override public void forgetResolvedTarget(PatternBatchContext context,UUID id){var b=factory(context);if(b!=null)b.sourceAcknowledged(id);var v=variable(context);if(v!=null)v.sourceAcknowledged(id);}
    @Override public void forgetResolvedTarget(ServerLevel level,BatchTransactionRecord record){
        var data=record.adapterData();require(data.getInt("schema")==1 && data.hasUUID("factory"),"Unknown native-world receipt schema");
        var saved=data.getBoolean("variable")?MekNativeRequests.savedV2Receipt(level,data.getUUID("factory"),record.id()):NativeWorldBatches.savedV2Receipt(level,data.getUUID("factory"),record.id());
        if(saved.isPresent())require(saved.get().getString("digest").equals(record.payloadDigest()) && saved.get().getString("pattern").equals(record.patternFingerprint()),"Native-world receipt identity changed");
        if(data.getBoolean("variable"))MekNativeRequests.acknowledgeSavedSource(level,data.getUUID("factory"),record.id());else NativeWorldBatches.acknowledgeSavedSource(level,data.getUUID("factory"),record.id());
    }
    private static BatchRecoveryResult result(BatchRecoveryResult.TargetState state,long count,String detail){return new BatchRecoveryResult(state,count,detail);}
    private static void require(boolean valid,String detail){if(!valid)throw new IllegalArgumentException(detail);}
    private static List<GenericStack> stacks(List<GenericStack> amounts,long count){return amounts.stream().map(e->new GenericStack(e.what(),Math.multiplyExact(e.amount(),count))).toList();}
    private static Map<AEKey,BigInteger> amounts(List<GenericStack> stacks){var result=new LinkedHashMap<AEKey,BigInteger>();for(var stack:stacks){require(stack.amount()>0,"Nonpositive aggregate");result.merge(stack.what(),BigInteger.valueOf(stack.amount()),BigInteger::add);}return Map.copyOf(result);}
}
