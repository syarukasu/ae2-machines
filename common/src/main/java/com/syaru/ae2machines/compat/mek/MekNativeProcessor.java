package com.syaru.ae2machines.compat.mek;

import appeng.api.stacks.*;
import com.syaru.ae2machines.engine.Recipe;
import com.syaru.ae2machines.platform.*;
import com.syaru.ae2machines.runtime.NativeBatchProcessor;
import mekanism.api.recipes.ItemStackToItemStackRecipe;
import mekanism.api.recipes.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.api.recipes.inputs.InputHelper;
import mekanism.api.recipes.outputs.OutputHelper;
import mekanism.api.recipes.outputs.IOutputHandler;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.tile.prefab.TileEntityConfigurableMachine;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.math.BigInteger;
import java.util.Map;

/** Uses the actual machine's cached recipe, native IO helpers and native energy extraction. */
public final class MekNativeProcessor implements NativeBatchProcessor {
    private final TileEntityConfigurableMachine machine;
    private final ItemStackToItemStackRecipe nativeRecipe;
    private final Recipe<AEKey> recipe;
    private final AEItemKey inputKey;
    private final int inputCount, maximumItems;
    private final WorkSlot input = new WorkSlot(), output = new WorkSlot();

    public MekNativeProcessor(BlockEntity block, Recipe<AEKey> recipe) {
        if (!MekanismAdapter.itemMachine(block) || recipe.inputs().size() != 1 || recipe.outputs().size() != 1)
            throw new IllegalArgumentException("UNSUPPORTED_NATIVE_EXECUTOR");
        machine = (TileEntityConfigurableMachine) block; this.recipe = recipe;
        var entry = recipe.inputs().entrySet().iterator().next();
        inputKey = (AEItemKey) entry.getKey(); inputCount = entry.getValue().intValueExact();
        var checked = MekanismAdapter.inspect(machine, inputKey.toStack(inputCount)).orElseThrow();
        if (!checked.recipe().equals(recipe)) throw new IllegalArgumentException("NATIVE_RECIPE_CHANGED");
        nativeRecipe = MekItemMachine.find(machine,inputKey.toStack(inputCount));
        maximumItems = Math.min(Integer.MAX_VALUE / inputCount,
                Integer.MAX_VALUE / recipe.outputs().values().iterator().next().intValueExact());
    }
    @Override public int maximumOperations(int from, int to) {
        if(!recipe.duration().equals(BigInteger.valueOf(MekItemMachine.ticks(machine)))
                || !recipe.energyPerWork().equals(MekNativeEnergy.configuredPerTick(machine)))
            throw new IllegalArgumentException("NATIVE_MACHINE_ENERGY_CONFIGURATION_CHANGED");
        return MekNativeEnergy.maximumOperations(recipe.energyPerWork().multiply(BigInteger.valueOf((long) to - from)), maximumItems);
    }
    @Override public boolean independentCopies() {
        // Only native stateless cell machines with fixed base output can share identical groups.
        return machine.getLevel() instanceof net.minecraft.server.level.ServerLevel level
                && com.syaru.ae2machines.world.NativeCellWorlds.owns(level) && MekWorldParallel.supports(machine)
                && machine.getControlType()==mekanism.common.tile.interfaces.IRedstoneControl.RedstoneControl.DISABLED
                && MekNativeEnergy.independentRecipe(nativeRecipe)
                && MekanismAdapter.fixedOutput(nativeRecipe);
    }
    @Override public Result advance(int from, int to, int operations, Map<AEKey, BigInteger> ownedInputs) {
        if (from < 0 || to <= from || to > recipe.duration().intValueExact() || operations <= 0
                || operations > maximumOperations(from, to)) throw new IllegalArgumentException("INVALID_NATIVE_PHASE");
        int offered = Math.multiplyExact(inputCount, operations);
        if (ownedInputs.getOrDefault(inputKey, BigInteger.ZERO).compareTo(BigInteger.valueOf(offered)) < 0)
            throw new IllegalArgumentException("NATIVE_INPUT_NOT_OWNED");
        input.setStack(inputKey.toStack(offered)); output.setStack(ItemStack.EMPTY);
        var perOperation = recipe.energyPerWork().multiply(BigInteger.valueOf((long) to - from));
        var paid = perOperation.multiply(BigInteger.valueOf(operations));
        var oldInput = MekItemMachine.handler(machine,true); var oldOutput = MekItemMachine.handler(machine,false);
        try {
            MekItemMachine.handler(machine,true, InputHelper.getInputHandler(input, RecipeError.NOT_ENOUGH_INPUT));
            var nativeOutput=OutputHelper.getOutputHandler(output,RecipeError.NOT_ENOUGH_OUTPUT_SPACE);
            MekItemMachine.handler(machine,false, new IOutputHandler<ItemStack>() {
                @Override public void handleOutput(ItemStack stack,int count) { nativeOutput.handleOutput(stack,count); }
                @Override public void calculateOperationsCanSupport(mekanism.api.recipes.cache.CachedRecipe.OperationTracker tracker,ItemStack stack) {
                    // OutputHelper probes only one ordinary stack. This private view represents all owned lanes.
                    var probe=stack.copyWithCount(Integer.MAX_VALUE);
                    var rejected=output.insertItem(probe,mekanism.api.Action.SIMULATE,mekanism.api.AutomationType.INTERNAL);
                    int supported=(probe.getCount()-rejected.getCount())/stack.getCount();
                    tracker.updateOperations(supported);
                    if(supported==0)tracker.addError(RecipeError.NOT_ENOUGH_OUTPUT_SPACE);
                }
            });
            var cached = MekItemMachine.cached(machine,nativeRecipe);
            var nativeErrors=new java.util.LinkedHashSet<RecipeError>();
            cached.setErrorsChanged(nativeErrors::addAll);
            // Group representatives must not publish transient activity as ordinary world work.
            // These exact classes use onFinish only to mark inventory dirty; the owned ledger persists it.
            cached.setActive(active -> {}).setOnFinish(() -> {});
            cached.setBaselineMaxOperations(() -> operations);
            var credit = MekNativeEnergy.bind(cached, perOperation, paid);
            // These exact machine classes consume no per-tick materials and have no intermediate callbacks.
            // Only idle progress is skipped; completion, input/output and energy run in native code.
            cached.loadSavedOperatingTicks(to - 1);
            MekNativeEnergy.process(cached);
            if(paid.equals(credit.get()) && !nativeErrors.isEmpty())throw new IllegalStateException("NATIVE_OWNED_PROCESSING_REFUSED: energy="+nativeErrors.contains(RecipeError.NOT_ENOUGH_ENERGY)+", input="+nativeErrors.contains(RecipeError.NOT_ENOUGH_INPUT)+", outputSpace="+nativeErrors.contains(RecipeError.NOT_ENOUGH_OUTPUT_SPACE)+", matchingOutput="+!nativeErrors.contains(RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT));
            int consumed = offered - input.getCount();
            var actual = output.getStack();
            return new Result(consumed == 0 ? Map.of() : Map.of(inputKey, BigInteger.valueOf(consumed)),
                    actual.isEmpty() ? Map.of() : Map.of(AEItemKey.of(actual), BigInteger.valueOf(actual.getCount())),
                    paid.subtract(credit.get()), MekItemMachine.progress(machine));
        } finally {
            MekItemMachine.handler(machine,true,oldInput); MekItemMachine.handler(machine,false,oldOutput);
            input.setStack(ItemStack.EMPTY); output.setStack(ItemStack.EMPTY);
        }
    }
    /** Private view of already-owned work, not an expanded externally accessible storage slot. */
    private static final class WorkSlot extends BasicInventorySlot {
        WorkSlot() { super(Integer.MAX_VALUE, (s, a) -> true, (s, a) -> true, s -> true, () -> {}, 0, 0); obeyStackLimit = false; }
    }
}
