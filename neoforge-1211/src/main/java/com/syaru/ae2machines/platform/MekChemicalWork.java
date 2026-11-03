package com.syaru.ae2machines.platform;

import appeng.api.stacks.*;
import mekanism.api.recipes.cache.CachedRecipe;
import mekanism.api.recipes.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.api.recipes.inputs.InputHelper;
import mekanism.api.recipes.outputs.OutputHelper;
import mekanism.api.chemical.BasicChemicalTank;
import mekanism.api.chemical.ChemicalStack;
import mekanism.common.capabilities.fluid.BasicFluidTank;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.tile.machine.*;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.*;

/** Private native IO views over already-owned inputs; never exposed as machine storage. */
public final class MekChemicalWork implements AutoCloseable {
    private final BlockEntity machine;
    private final Map<Field, Object> bindings = new LinkedHashMap<>();
    private final WorkSlot item = new WorkSlot();
    private final mekanism.api.chemical.IChemicalTank left = BasicChemicalTank.create(Long.MAX_VALUE, () -> {});
    private final mekanism.api.chemical.IChemicalTank right = BasicChemicalTank.create(Long.MAX_VALUE, () -> {});
    private final mekanism.api.chemical.IChemicalTank gasOutput = BasicChemicalTank.create(Long.MAX_VALUE, () -> {});
    private final BasicFluidTank fluid = BasicFluidTank.create(Integer.MAX_VALUE, () -> {});
    private final BasicFluidTank fluidOutput = BasicFluidTank.create(Integer.MAX_VALUE, () -> {});
    private final CachedRecipe<?> cached;

    public MekChemicalWork(BlockEntity machine, Map<AEKey, BigInteger> offered) {
        this(machine,offered,resolve(machine,offered));
    }
    public MekChemicalWork(BlockEntity machine, Map<AEKey, BigInteger> offered,Object nativeRecipe) {
        this.machine = machine;
        var gases = new ArrayList<ChemicalStack>();
        offered.forEach((key, n) -> {
            if (key instanceof AEItemKey k) item.setStack(k.toStack(n.intValueExact()));
            else if (key instanceof AEFluidKey k) fluid.setStack(k.toStack(n.intValueExact()));
            else if (key instanceof MekanismKey k && k.getStack() instanceof ChemicalStack)
                gases.add((ChemicalStack) k.withAmount(n.longValueExact()));
            else throw new IllegalArgumentException("UNSUPPORTED_NATIVE_CHEMICAL_KEY");
        });
        try {
            if (machine instanceof TileEntityChemicalOxidizer m) {
                bind("inputHandler", InputHelper.getInputHandler(item, RecipeError.NOT_ENOUGH_INPUT));
                bind("outputHandler", OutputHelper.getOutputHandler(gasOutput, RecipeError.NOT_ENOUGH_OUTPUT_SPACE));
                cached = m.createNewCachedRecipe((mekanism.api.recipes.ItemStackToChemicalRecipe)nativeRecipe, 0);
            } else if (machine instanceof TileEntityChemicalInfuser m) {
                if (gases.size() != 2) throw new IllegalArgumentException("CHEMICAL_INPUT_COUNT");
                var a = gases.get(0); var b = gases.get(1);
                var recipe = (mekanism.api.recipes.ChemicalChemicalToChemicalRecipe)nativeRecipe;
                if (!recipe.getLeftInput().test(a)) { var swap = a; a = b; b = swap; }
                left.setStack(a); right.setStack(b);
                bind("leftInputHandler", InputHelper.getInputHandler(left, RecipeError.NOT_ENOUGH_LEFT_INPUT));
                bind("rightInputHandler", InputHelper.getInputHandler(right, RecipeError.NOT_ENOUGH_RIGHT_INPUT));
                bind("outputHandler", OutputHelper.getOutputHandler(gasOutput, RecipeError.NOT_ENOUGH_OUTPUT_SPACE));
                cached = m.createNewCachedRecipe(recipe, 0);
            } else if (machine instanceof TileEntityRotaryCondensentrator m) {
                if (!gases.isEmpty()) left.setStack(gases.get(0));
                bind("gasInputHandler", InputHelper.getInputHandler(left, RecipeError.NOT_ENOUGH_INPUT));
                bind("fluidInputHandler", InputHelper.getInputHandler(fluid, RecipeError.NOT_ENOUGH_INPUT));
                bind("gasOutputHandler", OutputHelper.getOutputHandler(gasOutput, RecipeError.NOT_ENOUGH_OUTPUT_SPACE));
                bind("fluidOutputHandler", OutputHelper.getOutputHandler(fluidOutput, RecipeError.NOT_ENOUGH_OUTPUT_SPACE));
                cached = m.createNewCachedRecipe((mekanism.api.recipes.RotaryRecipe)nativeRecipe, 0);
            } else throw new IllegalArgumentException("UNSUPPORTED_NATIVE_CHEMICAL_MACHINE");
        } catch (RuntimeException | Error e) {
            close();
            throw e;
        }
    }
    public CachedRecipe<?> cached() { return cached; }
    public static Object resolve(BlockEntity machine,Map<AEKey,BigInteger> inputs) {
        if(machine instanceof TileEntityChemicalOxidizer m) {
            var e=inputs.entrySet().iterator().next();return Objects.requireNonNull(m.findFirstRecipe(((AEItemKey)e.getKey()).toStack(e.getValue().intValueExact())));
        }
        var gases=new ArrayList<ChemicalStack>();
        inputs.forEach((key,n)->{if(key instanceof MekanismKey k)gases.add((ChemicalStack)k.withAmount(n.longValueExact()));});
        if(machine instanceof TileEntityChemicalInfuser m)return Objects.requireNonNull(m.getRecipeType().getInputCache().findFirstRecipe(m.getLevel(),gases.get(0),gases.get(1)));
        var m=(TileEntityRotaryCondensentrator)machine;
        if(m.getMode()){var e=inputs.entrySet().iterator().next();return Objects.requireNonNull(m.getRecipeType().getInputCache().findFirstRecipe(m.getLevel(),((AEFluidKey)e.getKey()).toStack(e.getValue().intValueExact())));}
        return Objects.requireNonNull(m.getRecipeType().getInputCache().findFirstRecipe(m.getLevel(),gases.get(0)));
    }
    public static boolean independent(Object recipe) {
        return MekPassiveChemical.independent(recipe);
    }
    public record Configuration(int speed,int energy,boolean rotaryMode,int duration) {}
    public static Configuration configuration(BlockEntity machine) {
        var m=(mekanism.common.tile.prefab.TileEntityConfigurableMachine)machine;
        return new Configuration(m.getComponent().getUpgrades(mekanism.api.Upgrade.SPEED),m.getComponent().getUpgrades(mekanism.api.Upgrade.ENERGY),
                machine instanceof TileEntityRotaryCondensentrator r && r.getMode(),
                machine instanceof mekanism.common.tile.prefab.TileEntityProgressMachine<?> p?p.getTicksRequired():1);
    }
    public static boolean chemicalsEmpty(mekanism.common.tile.prefab.TileEntityConfigurableMachine machine){return machine.getChemicalTanks(null).stream().allMatch(t->t.isEmpty());}
    public Map<AEKey, BigInteger> inputs() {
        var amounts = new LinkedHashMap<AEKey, BigInteger>();
        if (!item.isEmpty()) add(amounts, AEItemKey.of(item.getStack()), item.getCount());
        if (!fluid.isEmpty()) add(amounts, AEFluidKey.of(fluid.getFluid()), fluid.getFluidAmount());
        if (!left.isEmpty()) add(amounts, MekanismKey.of(left.getStack()), left.getStored());
        if (!right.isEmpty()) add(amounts, MekanismKey.of(right.getStack()), right.getStored());
        return amounts;
    }
    public Map<AEKey, BigInteger> outputs() {
        var amounts = new LinkedHashMap<AEKey, BigInteger>();
        if (!gasOutput.isEmpty()) add(amounts, MekanismKey.of(gasOutput.getStack()), gasOutput.getStored());
        if (!fluidOutput.isEmpty()) add(amounts, AEFluidKey.of(fluidOutput.getFluid()), fluidOutput.getFluidAmount());
        return amounts;
    }
    private static void add(Map<AEKey, BigInteger> amounts, AEKey key, long n) {
        amounts.merge(Objects.requireNonNull(key), BigInteger.valueOf(n), BigInteger::add);
    }
    private void bind(String name, Object handler) {
        try {
            var field = machine.getClass().getDeclaredField(name);
            field.setAccessible(true);
            bindings.put(field, field.get(machine));
            field.set(machine, handler);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Mekanism chemical handler contract changed: " + name, e);
        }
    }
    @Override public void close() {
        try {
            for (var entry : bindings.entrySet()) entry.getKey().set(machine, entry.getValue());
            bindings.clear();
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Mekanism chemical handler restoration failed", e);
        }
    }
    private static final class WorkSlot extends BasicInventorySlot {
        WorkSlot() { super(Integer.MAX_VALUE, (s, a) -> true, (s, a) -> true, s -> true, () -> {}, 0, 0); obeyStackLimit = false; }
    }
}
