package com.syaru.ae2machines.compat.mek;

import appeng.api.stacks.AEKey;
import com.syaru.ae2machines.engine.*;
import com.syaru.ae2machines.platform.*;
import com.syaru.ae2machines.runtime.*;
import java.lang.reflect.*;
import java.math.BigInteger;
import java.util.*;
import mekanism.api.recipes.cache.CachedRecipe;
import mekanism.common.tile.prefab.TileEntityConfigurableMachine;
import mekanism.common.tile.prefab.TileEntityProgressMachine;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;

/** A bounded native recipe invocation. Only stateless, fixed-output native recipes are count-scaled. */
public final class MekExactRecipeWork implements NativeBatchProcessor {
    private static final Set<String> MACHINES=Set.of("TileEntityCombiner","TileEntityMetallurgicInfuser",
            "TileEntityPigmentExtractor","TileEntityPigmentMixer","TileEntityPaintingMachine",
            "TileEntityNutritionalLiquifier","TileEntityElectrolyticSeparator","TileEntityChemicalWasher",
            "TileEntityChemicalCrystallizer","TileEntityIsotopicCentrifuge","TileEntityPressurizedReactionChamber");
    private static final Set<String> RECIPES=Set.of("CombinerIRecipe","MetallurgicInfuserIRecipe","PigmentExtractingIRecipe",
            "PigmentMixingIRecipe","PaintingIRecipe","NutritionalLiquifierIRecipe","ElectrolysisIRecipe",
            "FluidSlurryToSlurryIRecipe","ChemicalCrystallizerIRecipe","CentrifugingIRecipe","PressurizedReactionIRecipe",
            "BasicCombinerRecipe","BasicMetallurgicInfuserRecipe","BasicPigmentExtractingRecipe","BasicPigmentMixingRecipe",
            "BasicPaintingRecipe","BasicNutritionalLiquifierRecipe","BasicElectrolysisRecipe","BasicWashingRecipe",
            "BasicChemicalCrystallizerRecipe","BasicCentrifugingRecipe","BasicPressurizedReactionRecipe");
    private final TileEntityConfigurableMachine machine;
    private final Recipe<AEKey> recipe;
    private final Object nativeRecipe;
    private final Configuration configuration;
    private record Configuration(int speed,int energy,mekanism.common.tile.interfaces.IRedstoneControl.RedstoneControl redstone) {}
    private record Invocation(TileEntityConfigurableMachine machine,MekRecipeMaterials materials,CachedRecipe<?> cached,String id,Object nativeRecipe) {}
    private record Probe(MachineProfile profile,Object nativeRecipe) {}

    public static boolean supports(BlockEntity machine) {
        return machine instanceof TileEntityConfigurableMachine && MACHINES.contains(machine.getClass().getSimpleName())
                && machine.getClass().getPackageName().equals("mekanism.common.tile.machine");
    }
    public static Optional<MachineProfile> inspect(BlockEntity machine,Map<AEKey,BigInteger> inputs) {
        if(!supports(machine) || inputs.isEmpty())return Optional.empty();
        try {
            var probe=probe((TileEntityConfigurableMachine)machine,inputs);
            return probe.profile().recipe().inputs().equals(inputs)?Optional.of(probe.profile()):Optional.empty();
        } catch(NoRecipe unavailable) { return Optional.empty(); }
    }
    public MekExactRecipeWork(BlockEntity machine,Recipe<AEKey> recipe) {
        this.machine=(TileEntityConfigurableMachine)machine;
        if(!supports(machine))throw new IllegalArgumentException("NATIVE_EXACT_MACHINE_UNSUPPORTED");
        var probe=probe(this.machine,recipe.inputs());
        if(!probe.profile().recipe().equals(recipe))
            throw new IllegalArgumentException("NATIVE_EXACT_RECIPE_CHANGED");
        this.recipe=recipe;nativeRecipe=probe.nativeRecipe();configuration=configuration(this.machine);
    }
    @Override public boolean independentCopies(){return machine.getLevel() instanceof net.minecraft.server.level.ServerLevel level
            && com.syaru.ae2machines.world.NativeCellWorlds.owns(level)
            && configuration.redstone()==mekanism.common.tile.interfaces.IRedstoneControl.RedstoneControl.DISABLED;}
    @Override public int maximumOperations(int from,int to) {
        if(!configuration.equals(configuration(machine)))throw new IllegalArgumentException("NATIVE_EXACT_CONFIGURATION_CHANGED");
        // The ledger aggregates independent native results in BigInteger, not this bounded invocation.
        return 1;
    }
    @Override public Result advance(int from,int to,int operations,Map<AEKey,BigInteger> ownedInputs) {
        if(from<0 || to<=from || to>recipe.duration().intValueExact() || operations!=maximumOperations(from,to))
            throw new IllegalArgumentException("INVALID_NATIVE_EXACT_PHASE");
        recipe.inputs().forEach((key,n)->{if(ownedInputs.getOrDefault(key,BigInteger.ZERO).compareTo(n)<0)
            throw new IllegalArgumentException("NATIVE_EXACT_INPUT_NOT_OWNED");});
        var invocation=invocation(machine,recipe.inputs(),nativeRecipe,recipe.id());
        if(!invocation.id().equals(recipe.id()))throw new IllegalArgumentException("NATIVE_EXACT_RECIPE_CHANGED");
        int ticks=duration(invocation.machine());
        if(ticks!=recipe.duration().intValueExact() || !MekNativeEnergy.configuredPerTick(invocation.machine()).equals(recipe.energyPerWork()))
            throw new IllegalArgumentException("NATIVE_EXACT_RECIPE_COST_CHANGED");
        return process(invocation,to,recipe.energyPerWork().multiply(BigInteger.valueOf((long)to-from)));
    }
    private static Probe probe(TileEntityConfigurableMachine source,Map<AEKey,BigInteger> inputs) {
        var invocation=invocation(source,inputs);int ticks=duration(invocation.machine());
        var perTick=MekNativeEnergy.configuredPerTick(invocation.machine());
        var result=process(invocation,ticks,perTick.multiply(BigInteger.valueOf(ticks)));
        if(result.produced().isEmpty() || result.consumed().isEmpty())throw new NoRecipe();
        var idle=MekanismAdapter.idleProfile(invocation.machine());
        var recipe=new Recipe<AEKey>(invocation.id(),result.consumed(),result.produced(),BigInteger.valueOf(ticks),perTick,MachineProfile.MEK_UNIT,BigInteger.ZERO);
        var profile=new MachineProfile(recipe,idle.capacity(),idle.inputRate(),BigInteger.ZERO,idle.unitsPerFE(),
                BigInteger.ONE,BigInteger.ONE,0,0);
        return new Probe(profile,invocation.nativeRecipe());
    }
    private static Invocation invocation(TileEntityConfigurableMachine source,Map<AEKey,BigInteger> inputs) {
        return invocation(source,inputs,null,null);
    }
    private static Invocation invocation(TileEntityConfigurableMachine source,Map<AEKey,BigInteger> inputs,Object capturedRecipe,String capturedId) {
        var state=source.getBlockState();
        var clone=(TileEntityConfigurableMachine)((EntityBlock)state.getBlock()).newBlockEntity(source.getBlockPos(),state);
        if(clone==null)throw new IllegalStateException("NATIVE_EXACT_MACHINE_CONSTRUCTOR");
        return NativeWorldBatches.nativeScope(clone,()->{
        clone.setLevel(source.getLevel());PlatformData.loadBlock(clone,PlatformData.saveBlock(source));
        var materials=new MekRecipeMaterials(clone,inputs);
        var nativeRecipe=capturedRecipe==null?call(clone,"getRecipe",0):capturedRecipe;
        if(nativeRecipe==null || !pure(nativeRecipe))throw new NoRecipe();
        var cached=(CachedRecipe<?>)call(clone,"createNewCachedRecipe",nativeRecipe,0);
        call(clone,"onCachedRecipeChanged",cached,0);
        cached.setActive(active->{}).setOnFinish(()->{}).setErrorsChanged(errors->{}).setBaselineMaxOperations(()->1);
        return new Invocation(clone,materials,cached,capturedId==null?MekRecipeMaterials.id(clone,nativeRecipe):capturedId,nativeRecipe);
        });
    }
    private static Result process(Invocation invocation,int to,BigInteger paid) {
        return NativeWorldBatches.nativeScope(invocation.machine(),()->processNative(invocation,to,paid));
    }
    private static Result processNative(Invocation invocation,int to,BigInteger paid) {
        var cached=invocation.cached();var before=invocation.materials().inputs();
        var credit=MekNativeEnergy.bind(cached,paid,paid);
        cached.loadSavedOperatingTicks(to-1);MekNativeEnergy.process(cached);
        var remaining=invocation.materials().inputs();var consumed=new LinkedHashMap<AEKey,BigInteger>();
        before.forEach((key,n)->{
            var used=n.subtract(remaining.getOrDefault(key,BigInteger.ZERO));
            if(used.signum()<0)throw new IllegalStateException("NATIVE_EXACT_INPUT_GREW");
            if(used.signum()>0)consumed.put(key,used);
        });
        int progress=invocation.machine() instanceof TileEntityProgressMachine<?> machine?machine.getOperatingTicks():0;
        return new Result(consumed,invocation.materials().outputs(),paid.subtract(credit.get()),progress);
    }
    private static int duration(TileEntityConfigurableMachine machine){return machine instanceof TileEntityProgressMachine<?> progress?Math.max(1,progress.getTicksRequired()):1;}
    private static Configuration configuration(TileEntityConfigurableMachine machine){return new Configuration(machine.getComponent().getUpgrades(mekanism.api.Upgrade.SPEED),machine.getComponent().getUpgrades(mekanism.api.Upgrade.ENERGY),machine.getControlType());}
    private static boolean pure(Object recipe) {
        String name=recipe.getClass().getName();
        if(!MekRecipeMaterials.fixedUsage(recipe) || !(name.startsWith("mekanism.common.recipe.impl.") || name.startsWith("mekanism.api.recipes.basic."))
                || !RECIPES.contains(recipe.getClass().getSimpleName()))return false;
        for(var method:recipe.getClass().getMethods())if(method.getParameterCount()==0
                && Set.of("getInput","getMainInput","getExtraInput","getLeftInput","getRightInput","getItemInput","getChemicalInput","getFluidInput","getInputSolid","getInputFluid","getInputGas","getInputChemical").contains(method.getName())) {
            Object ingredient=invoke(method,recipe);
            if(ingredient!=null && !MekRecipeMaterials.fixedIngredient(ingredient))return false;
        }
        return true;
    }
    private static Object call(Object target,String name,Object... args) {
        for(var method:target.getClass().getMethods())if(method.getName().equals(name) && !method.isBridge() && method.getParameterCount()==args.length) {
            boolean matches=true;var types=method.getParameterTypes();
            for(int i=0;i<types.length;i++)if(!(types[i]==int.class && args[i] instanceof Integer) && !types[i].isInstance(args[i]))matches=false;
            if(matches)return invoke(method,target,args);
        }
        throw new IllegalStateException("Mekanism native method changed: "+name);
    }
    private static Object invoke(Method method,Object target,Object... args) {
        try{return method.invoke(target,args);}catch(InvocationTargetException e){
            if(e.getCause() instanceof RuntimeException failure)throw failure;
            if(e.getCause() instanceof Error failure)throw failure;
            throw new IllegalStateException(e.getCause());
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private static final class NoRecipe extends RuntimeException {}
}
