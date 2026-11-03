package com.syaru.ae2machines.runtime;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.*;
import appeng.crafting.pattern.AEProcessingPattern;
import com.syaru.ae2machines.engine.*;
import java.math.BigInteger;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Immutable native recipes tied to the complete encoded patterns, including item data. */
public final class PatternProgram implements CompiledProgram {
    private final Map<AEItemKey,Recipe<AEKey>> recipes;
    private final List<IPatternDetails> patterns;
    private final Map<AEItemKey,BigInteger> parallelism;
    private final BooleanSupplier online;
    public PatternProgram(Map<IPatternDetails,MachineProfile> profiles,BooleanSupplier online) {
        var recipes=new LinkedHashMap<AEItemKey,Recipe<AEKey>>();
        var parallelism=new LinkedHashMap<AEItemKey,BigInteger>();
        for(var entry:profiles.entrySet()) {
            var pattern=entry.getKey();var recipe=entry.getValue().recipe();
            if(pattern.getClass()!=AEProcessingPattern.class) throw new IllegalArgumentException("UNSUPPORTED_PATTERN_TYPE");
            var inputs=inputTotals(pattern);var outputs=outputTotals(pattern);
            // Multi-operation encoded patterns need intermediate output-capacity semantics; don't guess them.
            if(!inputs.equals(recipe.inputs()) || !outputs.equals(recipe.outputs()))
                throw new IllegalArgumentException("PATTERN_NATIVE_RECIPE_MISMATCH");
            if(recipes.put(pattern.getDefinition(),recipe)!=null) throw new IllegalArgumentException("DUPLICATE_PATTERN");
            parallelism.put(pattern.getDefinition(),entry.getValue().parallelism());
        }
        this.recipes=Map.copyOf(recipes);this.patterns=List.copyOf(profiles.keySet());this.online=Objects.requireNonNull(online);
        this.parallelism=Map.copyOf(parallelism);
    }
    public static Map<AEKey,BigInteger> inputTotals(IPatternDetails pattern) {
        var result=new LinkedHashMap<AEKey,BigInteger>();
        for(var input:pattern.getInputs()) {
            if(input==null) continue;
            var options=input.getPossibleInputs();
            if(options.length!=1 || options[0]==null || input.getRemainingKey(options[0].what())!=null)
                throw new IllegalArgumentException("DYNAMIC_INPUT_OR_REMAINDER");
            var stack=options[0];
            var amount=ExactAmounts.positive(BigInteger.valueOf(stack.amount()))
                    .multiply(ExactAmounts.positive(BigInteger.valueOf(input.getMultiplier())));
            result.merge(stack.what(),amount,BigInteger::add);
        }
        return Map.copyOf(result);
    }
    public static Map<AEKey,BigInteger> outputTotals(IPatternDetails pattern) {
        var result=new LinkedHashMap<AEKey,BigInteger>();
        for(var output:pattern.getOutputs()) {
            if(output!=null) result.merge(output.what(),ExactAmounts.positive(BigInteger.valueOf(output.amount())),BigInteger::add);
        }
        return Map.copyOf(result);
    }
    @Override public Optional<Recipe<AEKey>> resolve(IPatternDetails pattern) {
        if(pattern==null || pattern.getClass()!=AEProcessingPattern.class) return Optional.empty();
        var recipe=recipes.get(pattern.getDefinition());
        return recipe!=null && inputTotals(pattern).equals(recipe.inputs()) && outputTotals(pattern).equals(recipe.outputs())
                ? Optional.of(recipe) : Optional.empty();
    }
    @Override public boolean boundaryActive() { return online.getAsBoolean(); }
    @Override public BigInteger nativeLanes(IPatternDetails pattern) { return parallelism.getOrDefault(pattern.getDefinition(),BigInteger.ONE); }
    public List<IPatternDetails> patterns() { return patterns; }
    public boolean contains(Recipe<AEKey> recipe) { return recipes.containsValue(recipe); }
}
