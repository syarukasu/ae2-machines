package com.syaru.ae2machines.compat.mek;

import appeng.api.stacks.*;
import com.syaru.ae2machines.engine.*;
import com.syaru.ae2machines.platform.*;
import com.syaru.ae2machines.runtime.NativeBatchProcessor;
import mekanism.common.tile.prefab.TileEntityProgressMachine;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;

/** Chemical work is executed by the captured machine's CachedRecipe, not by recipe coefficients. */
public final class MekChemicalNativeProcessor implements NativeBatchProcessor {
    private final BlockEntity machine;
    private final Recipe<AEKey> recipe;
    private final int maximum;
    private final Object nativeRecipe;
    private final MekChemicalWork.Configuration configuration;

    public MekChemicalNativeProcessor(BlockEntity machine, Recipe<AEKey> recipe) {
        if (!MekChemicalAdapter.supports(machine)
                || !MekChemicalAdapter.inspect(machine, recipe.inputs()).orElseThrow().recipe().equals(recipe))
            throw new IllegalArgumentException("NATIVE_CHEMICAL_RECIPE_CHANGED");
        this.machine = machine;
        this.recipe = recipe;
        nativeRecipe=MekChemicalWork.resolve(machine,recipe.inputs());
        configuration=MekChemicalWork.configuration(machine);
        var limit = BigInteger.valueOf(Integer.MAX_VALUE);
        for (var amounts : java.util.List.of(recipe.inputs(), recipe.outputs())) {
            for (var entry : amounts.entrySet()) {
                var capacity = BigInteger.valueOf(entry.getKey() instanceof AEItemKey || entry.getKey() instanceof AEFluidKey
                        ? Integer.MAX_VALUE : Long.MAX_VALUE);
                limit = limit.min(capacity.divide(entry.getValue()));
            }
        }
        maximum = limit.intValueExact();
        if (maximum <= 0) throw new IllegalArgumentException("NATIVE_CHEMICAL_AMOUNT_TOO_LARGE");
    }

    @Override public int maximumOperations(int from, int to) {
        if(!configuration.equals(MekChemicalWork.configuration(machine))
                || !recipe.energyPerWork().equals(MekNativeEnergy.configuredPerTick((mekanism.common.tile.prefab.TileEntityConfigurableMachine)machine)))
            throw new IllegalArgumentException("NATIVE_CHEMICAL_CONFIGURATION_CHANGED");
        return MekNativeEnergy.maximumOperations(recipe.energyPerWork().multiply(BigInteger.valueOf((long) to - from)), maximum);
    }
    @Override public boolean independentCopies() {
        return machine.getLevel() instanceof net.minecraft.server.level.ServerLevel level
                && com.syaru.ae2machines.world.NativeCellWorlds.owns(level) && MekChemicalWork.independent(nativeRecipe)
                && ((mekanism.common.tile.prefab.TileEntityConfigurableMachine)machine).getControlType()==mekanism.common.tile.interfaces.IRedstoneControl.RedstoneControl.DISABLED;
    }

    @Override public Result advance(int from, int to, int operations, Map<AEKey, BigInteger> ownedInputs) {
        if (from < 0 || to <= from || to > recipe.duration().intValueExact()
                || operations <= 0 || operations > maximumOperations(from, to))
            throw new IllegalArgumentException("INVALID_NATIVE_CHEMICAL_PHASE");
        var offered = ExactAmounts.multiply(recipe.inputs(), BigInteger.valueOf(operations));
        offered.forEach((key, count) -> {
            if (ownedInputs.getOrDefault(key, BigInteger.ZERO).compareTo(count) < 0)
                throw new IllegalArgumentException("NATIVE_CHEMICAL_INPUT_NOT_OWNED");
        });
        try (var work = new MekChemicalWork(machine, offered,nativeRecipe)) {
            var cached = work.cached();
            // Only visual state and the detached inventory's dirty callback are replaced.
            cached.setActive(active -> {}).setOnFinish(() -> {}).setBaselineMaxOperations(() -> operations);
            var perOperation = recipe.energyPerWork().multiply(BigInteger.valueOf((long) to - from));
            var paid = perOperation.multiply(BigInteger.valueOf(operations));
            var credit = MekNativeEnergy.bind(cached, perOperation, paid);
            cached.loadSavedOperatingTicks(to - 1);
            MekNativeEnergy.process(cached);
            var consumed = new LinkedHashMap<AEKey, BigInteger>();
            var remaining = work.inputs();
            offered.forEach((key, count) -> {
                var used = count.subtract(remaining.getOrDefault(key, BigInteger.ZERO));
                if (used.signum() < 0) throw new IllegalStateException("NATIVE_CHEMICAL_INPUT_GREW");
                if (used.signum() > 0) consumed.put(key, used);
            });
            int progress = machine instanceof TileEntityProgressMachine<?> p ? p.getOperatingTicks() : 0;
            return new Result(consumed, work.outputs(), paid.subtract(credit.get()), progress);
        }
    }
}
