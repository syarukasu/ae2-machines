package com.syaru.ae2machines.compat.mek;

import appeng.api.stacks.AEKey;
import com.syaru.ae2machines.engine.ExactAmounts;
import com.syaru.ae2machines.platform.*;
import com.syaru.ae2machines.runtime.NativeWorldBatches;
import com.syaru.ae2machines.world.NativeWorldRuntime;
import java.lang.reflect.*;
import java.math.*;
import java.util.*;
import mekanism.api.recipes.cache.CachedRecipe;
import mekanism.common.tile.prefab.TileEntityConfigurableMachine;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.EntityBlock;

/** Stateful native processing. Random, per-tick and environmental results are never extrapolated. */
public class MekVariableWork {
    public static final class NoMatchingRecipe extends IllegalArgumentException {public NoMatchingRecipe(){super("No native recipe matches owned inputs");}}
    public static final class RecipeChanged extends IllegalStateException {public RecipeChanged(){super("Owned native recipe content changed");}}
    public record Step(int completed,BigInteger used,Map<AEKey,BigInteger> outputs) {}
    public record WideStep(BigInteger completed,BigInteger used,Map<AEKey,BigInteger> outputs) {}
    public static MekVariableWork create(TileEntityConfigurableMachine source,CompoundTag saved,Map<AEKey,BigInteger> pool,
            Map<AEKey,BigInteger> per,appeng.api.stacks.GenericStack primary,BigInteger remaining,BigInteger lanes,java.util.function.IntSupplier available){
        if(saved.contains("iid") || saved.isEmpty() && remaining.min(lanes).compareTo(BigInteger.valueOf(Integer.MAX_VALUE))>0 && MekIidVariableWork.certify(source,per)!=null)
            return new MekIidVariableWork(source,saved,pool,per,primary,remaining.min(lanes));
        return new MekVariableWork(source,saved,pool,per,primary,remaining.min(lanes).min(BigInteger.valueOf(Integer.MAX_VALUE)).intValueExact(),available);
    }
    public boolean aggregated(){return false;}
    public WideStep stepWide(Map<AEKey,BigInteger> pool,Map<AEKey,BigInteger> per,BigInteger remaining,BigInteger lanes,BigInteger credit){
        var step=step(pool,per,remaining.min(lanes).min(BigInteger.valueOf(Integer.MAX_VALUE)).intValueExact(),credit);
        return new WideStep(BigInteger.valueOf(step.completed()),step.used(),step.outputs());
    }
    private final TileEntityConfigurableMachine machine;
    private final MekRequestMaterials materials;
    private final List<CachedRecipe<?>> cached=new ArrayList<>();
    private final List<String> recipes=new ArrayList<>();
    private final List<String> witnesses=new ArrayList<>();
    private final int[] groups;
    private final java.util.function.IntSupplier available;
    private int machines$stepCompleted;
    private record MethodKey(Class<?> type,String name,List<Class<?>> parameters){}
    private static final Map<MethodKey,Method> METHODS=new java.util.concurrent.ConcurrentHashMap<>();
    public static boolean supports(net.minecraft.world.level.block.entity.BlockEntity source){return source instanceof TileEntityConfigurableMachine
            && (MekRecipeMachines.supports(source) || MekWorldParallel.chemical(source) || MekWorldParallel.supports(source));}
    /** Metadata only: no CachedRecipe, RNG draw, world group update or acquired input. */
    public static boolean guarantees(TileEntityConfigurableMachine source,Map<AEKey,BigInteger> per,Map<AEKey,BigInteger> wanted){
        var state=source.getBlockState();
        var scratch=(TileEntityConfigurableMachine)((EntityBlock)state.getBlock()).newBlockEntity(source.getBlockPos(),state);
        if(scratch==null)return false;
        scratch.setLevel(source.getLevel());PlatformData.loadBlock(scratch,PlatformData.saveBlock(source));MekWorldParallel.prepare(scratch);
        var materials=new MekRequestMaterials(scratch,new int[0],true);materials.offer(new LinkedHashMap<>(per),per,1);
        Object recipe=call(scratch,"getRecipe",0);if(recipe==null || MekVariableEnergy.recipeWitness(recipe,source.getLevel()).isEmpty())return false;
        var outputs=new LinkedHashMap<AEKey,BigInteger>();
        var seen=new HashSet<String>();
        for(var method:recipe.getClass().getMethods()){
            var name=method.getName();
            // Direction-specific definitions can describe the reverse leg of a rotary recipe.
            if(method.isBridge() || method.isSynthetic() || method.getParameterCount()!=0 || !(name.equals("getOutputDefinition") || name.equals("getMainOutputDefinition")) || !seen.add(name))continue;
            try{
                method.setAccessible(true);var values=method.invoke(recipe);if(!(values instanceof Iterable<?> options))return false;
                appeng.api.stacks.GenericStack fixed=null;
                for(var value:options){var stack=MekRequestMaterials.output(value);if(stack==null || fixed!=null && !fixed.equals(stack))return false;fixed=stack;}
                if(fixed!=null)outputs.merge(fixed.what(),BigInteger.valueOf(fixed.amount()),BigInteger::add);
            }catch(ReflectiveOperationException e){throw new IllegalStateException("Native guaranteed output metadata unavailable",e);}
        }
        return !wanted.isEmpty() && wanted.entrySet().stream().allMatch(e->e.getValue().equals(outputs.get(e.getKey())));
    }
    public MekVariableWork(TileEntityConfigurableMachine source,CompoundTag saved,Map<AEKey,BigInteger> pool,
                           Map<AEKey,BigInteger> perExecution,appeng.api.stacks.GenericStack primary,int window,java.util.function.IntSupplier available){
        this.available=available;var state=source.getBlockState();
        machine=(TileEntityConfigurableMachine)((EntityBlock)state.getBlock()).newBlockEntity(source.getBlockPos(),state);
        if(machine==null)throw new IllegalStateException("Native variable machine constructor unavailable");
        machine.setLevel(source.getLevel());PlatformData.loadBlock(machine,saved.contains("machine")?saved.getCompound("machine"):PlatformData.saveBlock(source));
        MekWorldParallel.prepare(machine);
        materials=new MekRequestMaterials(machine,saved.getIntArray("inputMask"),!saved.contains("machine"));
        if(!machine.getEnergyContainers(null).isEmpty())com.syaru.ae2machines.runtime.NativeMachines.setStoredEnergy(machine,BigInteger.ZERO);
        materials.offer(pool,perExecution,window);
        groups=new int[MekRecipeMachines.processes(machine)];var oldGroups=saved.getIntArray("groups");var progress=saved.getIntArray("progress");
        for(int i=0;i<groups.length;i++){
            int index=i;Object nativeRecipe=call(machine,"getRecipe",i);
            if(saved.getBoolean("terminal")){cached.add(null);recipes.add(saved.getList("recipes",net.minecraft.nbt.Tag.TAG_STRING).getString(i));witnesses.add(saved.getList("witnesses",net.minecraft.nbt.Tag.TAG_STRING).getString(i));continue;}
            if(nativeRecipe==null && saved.contains("recipes"))nativeRecipe=MekVariableEnergy.savedRecipe(machine,saved.getList("recipes",net.minecraft.nbt.Tag.TAG_STRING).getString(i));
            if(nativeRecipe==null){cached.add(null);recipes.add("");witnesses.add("");continue;}
            if(!matchesOutput(nativeRecipe,primary))throw new NoMatchingRecipe();
            String id=MekRecipeMaterials.id(machine,nativeRecipe);recipes.add(id);
            String witness=MekVariableEnergy.recipeWitness(nativeRecipe,machine.getLevel());witnesses.add(witness);
            if(saved.contains("recipes") && !saved.getList("recipes",net.minecraft.nbt.Tag.TAG_STRING).getString(i).equals(id))throw new RecipeChanged();
            if(saved.contains("witnesses") && !saved.getList("witnesses",net.minecraft.nbt.Tag.TAG_STRING).getString(i).equals(witness))throw new RecipeChanged();
            var selected=nativeRecipe;var recipe=NativeWorldBatches.nativeScope(machine,()->(CachedRecipe<?>)call(machine,"createNewCachedRecipe",selected,index));
            call(machine,"onCachedRecipeChanged",recipe,i);
            recipe.setActive(active->{}).setOnFinish(()->{});
            MekWorldParallel.bindGroup(machine,recipe,i);
            var base=((com.syaru.ae2machines.mixin.MekCachedOperationsAccess)(Object)recipe).machines$baseline();
            recipe.setBaselineMaxOperations(()->Math.min(base.getAsInt(),groups[index]>0?groups[index]:Math.max(0,available.getAsInt()-machines$stepCompleted)));
            var post=((com.syaru.ae2machines.mixin.MekCachedOperationsAccess)(Object)recipe).machines$postProcess();
            recipe.setPostProcessOperations(tracker->{post.accept(tracker);if(groups[index]==0){var access=(com.syaru.ae2machines.mixin.MekOperationsAccess)(Object)tracker;int possible=Math.min(access.machines$currentMax(),access.machines$maxForEnergy());if(possible>0)groups[index]=possible;}});
            if(i<oldGroups.length)groups[i]=oldGroups[i];
            if(i<progress.length)((MekNativeStep)recipe).machines$progress(progress[i]);
            MekVariableEnergy.restoreUsage(machine,recipe,i);
            NativeWorldRuntime.get((net.minecraft.server.level.ServerLevel)machine.getLevel()).state().processWork(machine.getBlockPos().asLong(),i,groups[i]);
            cached.add(recipe);
        }
        if(!saved.getBoolean("terminal") && cached.stream().allMatch(Objects::isNull))throw new NoMatchingRecipe();
    }
    public int reserved(){return Arrays.stream(groups).sum();}
    public boolean recipesUnchanged(){
        for(int i=0;i<recipes.size();i++)if(!witnesses.get(i).isEmpty()){
            var current=MekVariableEnergy.savedRecipe(machine,recipes.get(i));
            if(current==null || !witnesses.get(i).equals(MekVariableEnergy.recipeWitness(current,machine.getLevel())))return false;
        }
        return true;
    }
    private static boolean matchesOutput(Object recipe,appeng.api.stacks.GenericStack primary){
        for(var method:recipe.getClass().getMethods())if(method.getParameterCount()==0 && method.getName().startsWith("get") && method.getName().endsWith("OutputDefinition"))try{
            method.setAccessible(true);
            var values=method.invoke(recipe);if(values instanceof Iterable<?> outputs)for(var output:outputs){var stack=MekRequestMaterials.output(output);if(primary.equals(stack))return true;}
        }catch(ReflectiveOperationException error){throw new IllegalStateException("Native output definition unavailable",error);}
        return false;
    }
    public Map<AEKey,BigInteger> inputs(){return materials.inputAmounts();}
    public Map<AEKey,BigInteger> returnInputs(){return materials.returnInputs();}
    public BigInteger unit(){return machine.getEnergyContainers(null).isEmpty()?BigInteger.ONE:MekanismAdapter.idleProfile(machine).unitsPerFE();}
    public BigInteger quantum(){return machine.getEnergyContainers(null).isEmpty()?BigInteger.ONE:MekanismAdapter.idleProfile(machine).nativeQuantum();}
    protected final BigInteger nativePerOperation(){return machine.getEnergyContainers(null).isEmpty()?BigInteger.ZERO:MekNativeEnergy.configuredPerTick(machine);}
    public BigInteger perOperation(){return nativePerOperation();}
    public Step step(Map<AEKey,BigInteger> pool,Map<AEKey,BigInteger> perExecution,int window,BigInteger credit){
        materials.offer(pool,perExecution,window);climate();
        int completed=0;machines$stepCompleted=0;var used=BigInteger.ZERO;var output=new LinkedHashMap<AEKey,BigInteger>();
        for(int i=0;i<cached.size();i++){
            var recipe=cached.get(i);if(recipe==null)continue;
            MekVariableEnergy.unpause(recipe);
            var balance=credit.subtract(used);var before=bind(recipe,balance);
            NativeWorldBatches.nativeScope(machine,()->{MekNativeEnergy.process(recipe);return null;});
            var spent=before.amount.subtract(before.remaining.get());if(spent.signum()<0)throw new IllegalStateException("Native payment grew");used=used.add(spent);
            int made=((MekNativeStep)recipe).machines$completed();
            if(made>0){if(made!=groups[i])throw new IllegalStateException("Native completion group mismatch");completed=Math.addExact(completed,made);machines$stepCompleted=completed;groups[i]=0;}
        }
        materials.drainOutputs().forEach((key,n)->output.merge(key,n,BigInteger::add));
        return new Step(completed,used,Map.copyOf(output));
    }
    private record Payment(BigInteger amount,java.util.function.Supplier<BigInteger> remaining) {}
    private Payment bind(CachedRecipe<?> recipe,BigInteger offered){
        var amount=offered.min(MekVariableEnergy.maximum());
        return new Payment(amount,MekNativeEnergy.bind(recipe,nativePerOperation(),amount));
    }
    private void climate(){
        if(!machine.getClass().getSimpleName().equals("TileEntitySolarNeutronActivator"))return;
        if(!(boolean)field(machine,"settingsChecked"))call(machine,"recheckSettings");
        setField(machine,"productionRate",call(machine,"recalculateProductionRate"));
    }
    public CompoundTag save(){
        var tag=new CompoundTag();tag.put("machine",PlatformData.saveBlock(machine));tag.putIntArray("inputMask",materials.mask());tag.putIntArray("groups",groups);
        tag.putIntArray("progress",cached.stream().mapToInt(c->c==null?0:((MekNativeStep)c).machines$progress()).toArray());
        var ids=new net.minecraft.nbt.ListTag();recipes.forEach(id->ids.add(net.minecraft.nbt.StringTag.valueOf(id)));tag.put("recipes",ids);
        var proof=new net.minecraft.nbt.ListTag();witnesses.forEach(id->proof.add(net.minecraft.nbt.StringTag.valueOf(id)));tag.put("witnesses",proof);return tag;
    }
    public static Object call(Object target,String name,Object... args){
        var key=new MethodKey(target.getClass(),name,Arrays.stream(args).map(Object::getClass).toList());
        var method=METHODS.computeIfAbsent(key,ignored->{
            var methods=new ArrayList<Method>(Arrays.asList(target.getClass().getMethods()));
            for(var type=target.getClass();type!=null;type=type.getSuperclass())methods.addAll(Arrays.asList(type.getDeclaredMethods()));
            for(var candidate:methods)if(candidate.getName().equals(name) && !candidate.isBridge() && candidate.getParameterCount()==args.length){
                var types=candidate.getParameterTypes();boolean match=true;for(int i=0;i<types.length;i++)if(!(types[i]==int.class && args[i] instanceof Integer) && !types[i].isInstance(args[i]))match=false;
                if(match){candidate.setAccessible(true);return candidate;}
            }
            throw new IllegalStateException("Native Mekanism method unavailable: "+name);
        });
        try{return method.invoke(target,args);}catch(InvocationTargetException e){if(e.getCause() instanceof RuntimeException r)throw r;if(e.getCause() instanceof Error error)throw error;throw new IllegalStateException(e.getCause());}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private static Object field(Object target,String name){try{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    private static void setField(Object target,String name,Object value){try{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
}
