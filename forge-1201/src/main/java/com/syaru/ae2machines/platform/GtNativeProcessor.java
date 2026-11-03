package com.syaru.ae2machines.platform;

import appeng.api.stacks.*;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.*;
import com.gregtechceu.gtceu.api.recipe.*;
import com.gregtechceu.gtceu.api.recipe.content.*;
import com.gregtechceu.gtceu.api.recipe.ingredient.EnergyStack;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.DistillationTowerMachine;
import com.syaru.ae2machines.engine.ExactAmounts;
import com.syaru.ae2machines.engine.Recipe;
import com.syaru.ae2machines.runtime.NativeBatchProcessor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import java.math.BigInteger;
import java.util.*;
import static com.syaru.ae2machines.world.NativeFactoryCompiler.require;

/** Native GT recipe IO over owned lanes; in-world execution restores live handlers in finally. */
public final class GtNativeProcessor implements NativeBatchProcessor {
    private final IRecipeLogicMachine machine;
    private final Recipe<AEKey> plan;
    private final GTRecipe original;
    private final NotifiableItemStackHandler itemsIn, itemsOut;
    private final NotifiableFluidTank fluidsIn;
    private final List<NotifiableFluidTank> fluidsOut = new ArrayList<>();
    private final NotifiableEnergyContainer energy;
    private final int maximum;
    private final boolean independentCopies;
    private IRecipeLogicMachine live;
    private int overclockTier;
    private BlockEntity liveBlock;
    private List<Object> structureConfiguration;

    public static GtNativeProcessor inWorld(BlockEntity source,Recipe<AEKey> plan) {
        var processor=GtMultiblockAdapter.supports(source)
                ?new GtNativeProcessor((ServerLevel)source.getLevel(),GtMultiblockAdapter.worldSnapshot(source),plan)
                :new GtNativeProcessor(source,plan);
        processor.liveBlock=source;
        if(GtMultiblockAdapter.supports(source)) {
            processor.live=GtMultiblockAdapter.worldMachine(source);
            processor.structureConfiguration=GtMultiblockAdapter.worldConfiguration(source);
        } else {
            var single=GregTechAdapter.machine(source);processor.live=single;processor.overclockTier=single.getOverclockTier();
        }
        return processor;
    }

    public GtNativeProcessor(BlockEntity source, Recipe<AEKey> plan) {
        this(GregTechAdapter.probe(source, plan.inputs()), plan);
    }
    public GtNativeProcessor(ServerLevel level, CompoundTag structure, Recipe<AEKey> plan) {
        this(GtMultiblockAdapter.probe(level, structure, plan.inputs()), plan);
    }
    private GtNativeProcessor(IRecipeLogicMachine machine, Recipe<AEKey> plan) {
        this.machine = machine; this.plan = plan;
        var matches = machine.getRecipeType().searchRecipe(machine, r ->
                r.getId().toString().equals(plan.id()) && RecipeHelper.matchRecipe(machine, r).isSuccess());
        require(matches.hasNext(), "GT_NATIVE_RECIPE_MISSING");
        original = matches.next().copy();
        require(GregTechAdapter.deterministic(original), "GT_NATIVE_RECIPE_NOT_DETERMINISTIC");
        independentCopies = independentNative(machine, original);
        var owner = (MetaMachine) machine;
        // Preserve captured non-consumed circuit handlers; they are never synthesized from the pattern.
        var constants = machine.getCapabilitiesFlat(IO.IN, ItemRecipeCapability.CAP).stream()
                .filter(h -> !h.shouldSearchContent()).toList();
        int slots = Math.max(1, plan.inputs().size() + plan.outputs().size());
        itemsIn = items(owner, slots, IO.IN); itemsOut = items(owner, slots, IO.OUT);
        fluidsIn = fluids(owner, slots, IO.IN);
        energy = new NotifiableEnergyContainer(owner, Long.MAX_VALUE, Long.MAX_VALUE, 1, 0, 0);
        machine.getCapabilitiesProxy().clear(); machine.getCapabilitiesFlat().clear();
        var inputs = new ArrayList<IRecipeHandler<?>>(); inputs.add(itemsIn); inputs.add(fluidsIn); inputs.add(energy); inputs.addAll(constants);
        machine.addHandlerList(RecipeHandlerList.of(IO.IN, inputs));
        var outputs = new ArrayList<IRecipeHandler<?>>(); outputs.add(itemsOut);
        if (machine instanceof DistillationTowerMachine tower) {
            int layers = tower.getFluidOutputs().size();
            require(layers >= original.getOutputContents(FluidRecipeCapability.CAP).size(), "GT_DISTILLATION_LAYERS_MISSING");
            var nativeLayers = new ArrayList<IFluidHandler>();
            for (int i = 0; i < layers; i++) { var tank = fluids(owner, 1, IO.OUT); fluidsOut.add(tank); outputs.add(tank); nativeLayers.add(tank); }
            try {
                var field = DistillationTowerMachine.class.getDeclaredField("fluidOutputs"); field.setAccessible(true); field.set(tower, nativeLayers);
                field = DistillationTowerMachine.class.getDeclaredField("firstValid"); field.setAccessible(true); field.set(tower, nativeLayers.get(0));
            } catch (ReflectiveOperationException e) { throw new IllegalStateException("GT_DISTILLATION_API_CHANGED", e); }
        } else { var tank = fluids(owner, slots, IO.OUT); fluidsOut.add(tank); outputs.add(tank); }
        machine.addHandlerList(RecipeHandlerList.of(IO.OUT, outputs));
        var limit = BigInteger.valueOf(Integer.MAX_VALUE);
        for (var amounts : List.of(plan.inputs(), plan.outputs()))
            for (var n : amounts.values()) limit = limit.min(BigInteger.valueOf(Integer.MAX_VALUE).divide(n));
        maximum = limit.intValueExact();
        require(maximum > 0, "GT_NATIVE_AMOUNT_TOO_LARGE");
    }
    @Override public boolean consumesAtStart() { return true; }
    @Override public boolean independentCopies() { return independentCopies; }
    private static boolean independentNative(IRecipeLogicMachine machine, GTRecipe recipe) {
        if(machine.getClass()==com.gregtechceu.gtceu.api.machine.SimpleTieredMachine.class
                && machine.getRecipeLogic().getClass()!=com.gregtechceu.gtceu.api.machine.trait.RecipeLogic.class)return false;
        // Only known native, stateless IO. Custom ingredients/machines and non-consumed
        // materials keep individual execution, even when a recipe advertises fixed amounts.
        if (recipe.getClass() != GTRecipe.class || !Set.of(
                com.gregtechceu.gtceu.api.machine.SimpleTieredMachine.class,
                WorkableElectricMultiblockMachine.class,
                com.gregtechceu.gtceu.api.machine.multiblock.CoilWorkableElectricMultiblockMachine.class,
                DistillationTowerMachine.class).contains(machine.getClass())) return false;
        for (var map : List.of(recipe.inputs, recipe.outputs)) {
            for(var list:map.values())for(var content:list) {
                if(content.chance!=content.maxChance && !(map==recipe.inputs && content.chance==0))return false;
                if(content.content instanceof com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient fluid) {
                    if(fluid.getClass()!=com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient.class)return false;
                    for(var value:fluid.values)if(!Set.of(com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient.FluidValue.class,
                            com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient.TagValue.class).contains(value.getClass()))return false;
                }else if(content.content instanceof net.minecraft.world.item.crafting.Ingredient ingredient) {
                    if(ingredient.getClass()==com.gregtechceu.gtceu.api.recipe.ingredient.SizedIngredient.class)
                        ingredient=((com.gregtechceu.gtceu.api.recipe.ingredient.SizedIngredient)ingredient).getInner();
                    if(!NativeIngredients.fixedItem(ingredient)
                            && !(map==recipe.inputs && content.chance==0
                            && ingredient.getClass()==com.gregtechceu.gtceu.api.recipe.ingredient.IntCircuitIngredient.class))return false;
                }else return false;
            }
        }
        return true;
    }
    @Override public int maximumOperations(int from, int to) {
        if(live!=null) {
            require(live.isWorkingEnabled(),"GT_NATIVE_CONFIGURATION_CHANGED");
            if(structureConfiguration!=null)require(structureConfiguration.equals(GtMultiblockAdapter.worldConfiguration(liveBlock)),"GT_NATIVE_CONFIGURATION_CHANGED");
            else require(((com.gregtechceu.gtceu.api.machine.SimpleTieredMachine)live).getOverclockTier()==overclockTier,"GT_NATIVE_CONFIGURATION_CHANGED");
        }
        var cost = plan.energyPerWork().multiply(BigInteger.valueOf((long) to - from));
        return BigInteger.valueOf(Long.MAX_VALUE).divide(cost).min(BigInteger.valueOf(maximum)).intValueExact();
    }
    @Override public Result advance(int from, int to, int operations, Map<AEKey, BigInteger> ownedInputs) {
        if(structureConfiguration!=null)return GtMultiblockAdapter.worldLocked(liveBlock,()->advanceBound(from,to,operations,ownedInputs));
        return advanceBound(from,to,operations,ownedInputs);
    }
    private Result advanceBound(int from,int to,int operations,Map<AEKey,BigInteger> ownedInputs) {
        if(live==null)return advanceNative(machine,from,to,operations,ownedInputs);
        var proxy=new HashMap<>(live.getCapabilitiesProxy());var flat=new HashMap<>(live.getCapabilitiesFlat());
        var tower=live instanceof DistillationTowerMachine?towerState(live):null;
        try {
            if(tower!=null)towerState(live,towerState(machine));
            live.getCapabilitiesProxy().clear();live.getCapabilitiesProxy().putAll(machine.getCapabilitiesProxy());
            live.getCapabilitiesFlat().clear();live.getCapabilitiesFlat().putAll(machine.getCapabilitiesFlat());
            return advanceNative(live,from,to,operations,ownedInputs);
        }finally {
            live.getCapabilitiesProxy().clear();live.getCapabilitiesProxy().putAll(proxy);
            live.getCapabilitiesFlat().clear();live.getCapabilitiesFlat().putAll(flat);
            if(tower!=null)towerState(live,tower);
        }
    }
    private static Object[] towerState(IRecipeLogicMachine machine) {
        try {
            var result=new Object[2];int index=0;
            for(var name:List.of("fluidOutputs","firstValid")) {
                var field=DistillationTowerMachine.class.getDeclaredField(name);field.setAccessible(true);result[index++]=field.get(machine);
            }
            return result;
        }catch(ReflectiveOperationException error){throw new IllegalStateException("GT_DISTILLATION_API_CHANGED",error);}
    }
    private static void towerState(IRecipeLogicMachine machine,Object[] state) {
        try {
            int index=0;for(var name:List.of("fluidOutputs","firstValid")) {
                var field=DistillationTowerMachine.class.getDeclaredField(name);field.setAccessible(true);field.set(machine,state[index++]);
            }
        }catch(ReflectiveOperationException error){throw new IllegalStateException("GT_DISTILLATION_API_CHANGED",error);}
    }
    private Result advanceNative(IRecipeLogicMachine machine,int from,int to,int operations,Map<AEKey,BigInteger> ownedInputs) {
        require(from >= 0 && to > from && to <= plan.duration().intValueExact()
                && operations > 0 && operations <= maximumOperations(from, to), "GT_INVALID_NATIVE_PHASE");
        clear();
        var offered = from == 0 ? ExactAmounts.multiply(plan.inputs(), BigInteger.valueOf(operations)) : Map.<AEKey,BigInteger>of();
        int itemSlot = 0, fluidSlot = 0;
        for (var entry : offered.entrySet()) {
            require(ownedInputs.getOrDefault(entry.getKey(), BigInteger.ZERO).compareTo(entry.getValue()) >= 0, "GT_NATIVE_INPUT_NOT_OWNED");
            if (entry.getKey() instanceof AEItemKey key) itemsIn.setStackInSlot(itemSlot++, key.toStack(entry.getValue().intValueExact()));
            else if (entry.getKey() instanceof AEFluidKey key) fluidsIn.setFluidInTank(fluidSlot++, new FluidStack(key.getFluid(), entry.getValue().intValueExact(), key.getTag()));
            else throw new IllegalArgumentException("GT_UNSUPPORTED_MATERIAL");
        }
        // GT's own modifier preserves non-consumed ingredients. Integer material products are bounded exactly above.
        var scaled = original.copy();
        var modifier = ContentModifier.multiplier(operations);
        scaled.inputs.clear(); scaled.inputs.putAll(modifier.applyContents(original.inputs));
        scaled.outputs.clear(); scaled.outputs.putAll(modifier.applyContents(original.outputs));
        scaled.duration = plan.duration().intValueExact();
        var paid = plan.energyPerWork().multiply(BigInteger.valueOf((long) to - from)).multiply(BigInteger.valueOf(operations));
        scaled.tickInputs.clear();
        scaled.tickInputs.put(EURecipeCapability.CAP, List.of(new Content(new EnergyStack(paid.longValueExact(), 1), 10000, 10000, 0)));
        energy.setEnergyStored(paid.longValueExact());
        if (from == 0) require(RecipeHelper.handleRecipeIO(machine, scaled, IO.IN, new HashMap<>()).isSuccess(), "GT_NATIVE_INPUT_FAILED");
        var remaining = contents(itemsIn, List.of(fluidsIn));
        var consumed = new LinkedHashMap<AEKey, BigInteger>();
        offered.forEach((key, n) -> {
            var used = n.subtract(remaining.getOrDefault(key, BigInteger.ZERO));
            require(used.signum() >= 0, "GT_NATIVE_INPUT_GREW");
            if (used.signum() > 0) consumed.put(key, used);
        });
        require(RecipeHelper.handleTickRecipeIO(machine, scaled, IO.IN, new HashMap<>()).isSuccess(), "GT_NATIVE_POWER_FAILED");
        boolean complete = to == plan.duration().intValueExact();
        if (complete) {
            boolean success = machine instanceof WorkableElectricMultiblockMachine multi ? GtMultiblockAdapter.output(multi, scaled)
                    : RecipeHelper.handleRecipeIO(machine, scaled, IO.OUT, new HashMap<>()).isSuccess();
            require(success, "GT_NATIVE_OUTPUT_FAILED");
        }
        return new Result(consumed, contents(itemsOut, fluidsOut), paid.subtract(BigInteger.valueOf(energy.getEnergyStored())), complete ? 0 : to);
    }
    private void clear() {
        for (var items : List.of(itemsIn, itemsOut)) for (int i=0;i<items.getSlots();i++) items.setStackInSlot(i, ItemStack.EMPTY);
        for (int i=0;i<fluidsIn.getTanks();i++) fluidsIn.setFluidInTank(i, FluidStack.EMPTY);
        for (var fluids : fluidsOut) for (int i=0;i<fluids.getTanks();i++) fluids.setFluidInTank(i, FluidStack.EMPTY);
    }
    private static NotifiableItemStackHandler items(MetaMachine owner, int slots, IO io) {
        return new NotifiableItemStackHandler(owner, slots, io, io, n -> new CustomItemStackHandler(n) {
            @Override public int getSlotLimit(int slot) { return Integer.MAX_VALUE; }
            @Override protected int getStackLimit(int slot, ItemStack stack) { return Integer.MAX_VALUE; }
            @Override public ItemStack extractItem(int slot,int requested,boolean simulate) {
                var stack=getStackInSlot(slot);
                if(requested<=0 || stack.isEmpty())return ItemStack.EMPTY;
                int taken=Math.min(requested,stack.getCount());
                var result=stack.copyWithCount(taken);
                if(!simulate)setStackInSlot(slot,stack.copyWithCount(stack.getCount()-taken));
                return result;
            }
        });
    }
    private static NotifiableFluidTank fluids(MetaMachine owner, int slots, IO io) {
        return new NotifiableFluidTank(owner, slots, Integer.MAX_VALUE, io, io);
    }
    private static Map<AEKey,BigInteger> contents(NotifiableItemStackHandler items,List<NotifiableFluidTank> fluids) {
        var amounts = new LinkedHashMap<AEKey,BigInteger>();
        for(int i=0;i<items.getSlots();i++) { var stack=items.getStackInSlot(i);if(!stack.isEmpty())amounts.merge(AEItemKey.of(stack),BigInteger.valueOf(stack.getCount()),BigInteger::add); }
        for(var tank:fluids)for(int i=0;i<tank.getTanks();i++) { var stack=tank.getFluidInTank(i);if(!stack.isEmpty())amounts.merge(AEFluidKey.of(stack),BigInteger.valueOf(stack.getAmount()),BigInteger::add); }
        return amounts;
    }
}
