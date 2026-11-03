package com.syaru.ae2machines.compat.mek;

import appeng.api.stacks.*;
import com.syaru.ae2machines.engine.*;
import com.syaru.ae2machines.platform.*;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.*;
import mekanism.common.tile.prefab.TileEntityConfigurableMachine;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.EntityBlock;

/** Only a fixed native sawmill recipe may share deterministic work across IID lanes. */
public final class MekIidVariableWork extends MekVariableWork {
    public record Certificate(BigInteger probability,GenericStack secondary){}
    private static final ThreadLocal<MekIidVariableWork> CURRENT=new ThreadLocal<>();
    private final TileEntityConfigurableMachine source;
    private final Certificate certificate;
    private final Map<AEKey,BigInteger> representative;
    private BigInteger copies;
    private long seed;
    private Future<BigInteger> draw;
    private BigInteger sampled;
    private boolean observedSecondary,started;
    private record Initial(CompoundTag nativeState,Map<AEKey,BigInteger> pool,BigInteger copies,long seed){}
    public static Certificate certify(TileEntityConfigurableMachine source,Map<AEKey,BigInteger> per){
        if(!source.getClass().getName().equals("mekanism.common.tile.machine.TileEntityPrecisionSawmill") || per.size()!=1)return null;
        var entry=per.entrySet().iterator().next();
        if(!(entry.getKey() instanceof AEItemKey item) || entry.getValue().signum()<=0 || entry.getValue().compareTo(BigInteger.valueOf(64))>0)return null;
        var state=source.getBlockState();var probe=(TileEntityConfigurableMachine)((EntityBlock)state.getBlock()).newBlockEntity(source.getBlockPos(),state);
        probe.setLevel(source.getLevel());PlatformData.loadBlock(probe,PlatformData.saveBlock(source));MekWorldParallel.prepare(probe);
        var materials=new MekRequestMaterials(probe,new int[0],true);materials.offer(new LinkedHashMap<>(per),per,1);
        var recipe=call(probe,"getRecipe",0);if(recipe==null)return null;
        var type=recipe.getClass().getName();
        if(!type.equals("mekanism.common.recipe.impl.SawmillIRecipe") && !type.equals("mekanism.api.recipes.basic.BasicSawmillRecipe"))return null;
        var matching=(ItemStack)call(call(recipe,"getInput"),"getMatchingInstance",item.toStack(entry.getValue().intValueExact()));
        if(matching.isEmpty() || !BigInteger.valueOf(matching.getCount()).equals(entry.getValue()))return null;
        double p=((Number)call(recipe,"getSecondaryChance")).doubleValue();
        if(!(p>0 && p<1))return null;
        var main=(List<?>)call(recipe,"getMainOutputDefinition");var secondary=(List<?>)call(recipe,"getSecondaryOutputDefinition");
        if(main.size()!=1 || secondary.size()!=1 || MekRequestMaterials.output(main.get(0))==null)return null;
        var extra=MekRequestMaterials.output(secondary.get(0));
        return extra==null?null:new Certificate(ExactBinomial.inclusiveDoubleNumerator(p),extra);
    }
    public MekIidVariableWork(TileEntityConfigurableMachine source,CompoundTag saved,Map<AEKey,BigInteger> pool,
            Map<AEKey,BigInteger> per,GenericStack primary,BigInteger lanes){this(source,saved,pool,per,primary,initial(saved,per,lanes));}
    private MekIidVariableWork(TileEntityConfigurableMachine source,CompoundTag saved,Map<AEKey,BigInteger> pool,
            Map<AEKey,BigInteger> per,GenericStack primary,Initial initial){
        super(source,initial.nativeState,initial.pool,per,primary,1,()->1);
        this.source=source;representative=initial.pool;copies=initial.copies;seed=initial.seed;
        var current=certify(source,per);
        if(saved.getBoolean("terminal") && saved.contains("iid")){
            var iid=saved.getCompound("iid");
            certificate=new Certificate(new BigInteger(iid.getByteArray("probability")),new GenericStack(PlatformData.loadKey(iid.getCompound("secondaryKey"),source.getLevel()),iid.getLong("secondaryAmount")));
        }else{certificate=current;if(certificate==null)throw new RecipeChanged();}
        if(saved.contains("iid")){
            var iid=saved.getCompound("iid");if(iid.getInt("schema")!=1 || !new BigInteger(iid.getByteArray("probability")).equals(certificate.probability))throw new RecipeChanged();
            if(!PlatformData.loadKey(iid.getCompound("secondaryKey"),source.getLevel()).equals(certificate.secondary.what()) || iid.getLong("secondaryAmount")!=certificate.secondary.amount())throw new RecipeChanged();
            if(iid.contains("sampled")){sampled=ExactAmounts.nonnegative(new BigInteger(iid.getByteArray("sampled")));if(copies.signum()>0 && sampled.compareTo(copies)>0)throw new IllegalStateException("IID draw exceeds cohort");}
            started=iid.getBoolean("started") || super.reserved()>0;
        }else debit(pool,per,copies);
    }
    private static Initial initial(CompoundTag saved,Map<AEKey,BigInteger> per,BigInteger lanes){
        if(!saved.contains("iid"))return new Initial(new CompoundTag(),new LinkedHashMap<>(per),lanes,new java.security.SecureRandom().nextLong());
        var iid=saved.getCompound("iid");if(iid.getInt("schema")!=1)throw new IllegalStateException("Unknown IID work schema");
        var nativeState=saved.getCompound("native").copy();nativeState.putBoolean("terminal",saved.getBoolean("terminal"));
        // A certified representative always fits in native input slots; the pool is empty after offer.
        return new Initial(nativeState,new LinkedHashMap<>(),ExactAmounts.nonnegative(new BigInteger(iid.getByteArray("copies"))),iid.getLong("seed"));
    }
    private static void debit(Map<AEKey,BigInteger> pool,Map<AEKey,BigInteger> per,BigInteger n){
        var required=ExactAmounts.multiply(per,n);
        required.forEach((key,count)->{if(pool.getOrDefault(key,BigInteger.ZERO).compareTo(count)<0)throw new IllegalStateException("IID input overdraft");});
        required.forEach((key,count)->{var left=pool.get(key).subtract(count);if(left.signum()==0)pool.remove(key);else pool.put(key,left);});
    }
    @Override public boolean aggregated(){return true;}
    @Override public BigInteger perOperation(){return nativePerOperation().multiply(copies);}
    @Override public Map<AEKey,BigInteger> inputs(){
        var input=new LinkedHashMap<>(super.inputs());representative.forEach((key,n)->input.merge(key,n,BigInteger::add));return ExactAmounts.multiply(input,copies);
    }
    @Override public Map<AEKey,BigInteger> returnInputs(){
        if(draw!=null)draw.cancel(true);var input=new LinkedHashMap<>(super.returnInputs());representative.forEach((key,n)->input.merge(key,n,BigInteger::add));representative.clear();var returned=ExactAmounts.multiply(input,copies);copies=BigInteger.ZERO;return returned;
    }
    @Override public WideStep stepWide(Map<AEKey,BigInteger> pool,Map<AEKey,BigInteger> per,BigInteger remaining,BigInteger lanes,BigInteger credit){
        if(copies.signum()==0){copies=remaining.min(lanes);if(copies.signum()==0)return new WideStep(BigInteger.ZERO,BigInteger.ZERO,Map.of());debit(pool,per,copies);representative.putAll(per);seed=new java.security.SecureRandom().nextLong();sampled=null;draw=null;started=false;}
        if(!started && nativePerOperation().signum()>0){
            var funded=copies.min(credit.divide(nativePerOperation()));
            if(funded.signum()==0)return new WideStep(BigInteger.ZERO,BigInteger.ZERO,Map.of());
            if(funded.compareTo(copies)<0){
                ExactAmounts.multiply(per,copies.subtract(funded)).forEach((key,n)->pool.merge(key,n,BigInteger::add));
                copies=funded;if(draw!=null)draw.cancel(true);draw=null;sampled=null;
            }
        }
        if(sampled==null){
            if(draw==null)draw=MekIidDraws.submit(source.getLevel().getServer(),copies,certificate.probability,seed);
            if(!draw.isDone())return new WideStep(BigInteger.ZERO,BigInteger.ZERO,Map.of());
            try{sampled=draw.get();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}catch(ExecutionException e){throw new IllegalStateException("Exact IID sampling failed",e.getCause());}
        }
        if(CURRENT.get()!=null)throw new IllegalStateException("Nested IID native work");
        final Step step;observedSecondary=false;CURRENT.set(this);
        try{step=super.step(representative,per,1,credit.divide(copies));}finally{CURRENT.remove();}
        started|=step.used().signum()>0 || super.reserved()>0 || step.completed()>0;
        var outputs=new LinkedHashMap<>(ExactAmounts.multiply(step.outputs(),copies));var completed=BigInteger.ZERO;
        var spent=step.used().multiply(copies);
        if(step.completed()>0){
            if(step.completed()!=1 || !observedSecondary || !super.inputs().isEmpty() || !representative.isEmpty())throw new IllegalStateException("Certified IID native completion changed");
            if(sampled.signum()>0)outputs.merge(certificate.secondary.what(),sampled.multiply(BigInteger.valueOf(certificate.secondary.amount())),BigInteger::add);
            completed=copies;copies=BigInteger.ZERO;started=false;
        }
        return new WideStep(completed,spent,Map.copyOf(outputs));
    }
    public static void observe(Object chanceOutput){
        var work=CURRENT.get();if(work==null)return;
        var nativeOutput=MekRequestMaterials.output(call(chanceOutput,"getMaxSecondaryOutput"));
        if(!work.certificate.secondary.equals(nativeOutput))throw new IllegalStateException("Native secondary output differs from IID certificate");
        work.observedSecondary=true;
    }
    public static boolean suppressSecondary(){return CURRENT.get()!=null;}
    @Override public CompoundTag save(){
        var saved=new CompoundTag();saved.put("native",super.save());var iid=new CompoundTag();iid.putInt("schema",1);iid.putByteArray("copies",copies.toByteArray());iid.putBoolean("started",started);iid.putByteArray("probability",certificate.probability.toByteArray());iid.put("secondaryKey",PlatformData.saveKey(certificate.secondary.what(),source.getLevel()));iid.putLong("secondaryAmount",certificate.secondary.amount());iid.putLong("seed",seed);if(sampled!=null)iid.putByteArray("sampled",sampled.toByteArray());saved.put("iid",iid);return saved;
    }
}
