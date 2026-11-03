package com.syaru.ae2machines.platform;

import appeng.api.stacks.*;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.SimpleTieredMachine;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.syaru.ae2machines.runtime.MachineProfile;
import com.syaru.ae2machines.engine.Recipe;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import java.math.BigInteger;
import java.util.*;
import static com.syaru.ae2machines.world.NativeFactoryCompiler.require;

/** Native GT lookup, overclock and IO on a detached probe. No item names or copied recipe tables. */
public final class GregTechAdapter {
    private GregTechAdapter() {}
    public static boolean supports(BlockEntity be) {
        return be instanceof MetaMachineBlockEntity holder && holder.getMetaMachine().getClass()==SimpleTieredMachine.class;
    }
    static SimpleTieredMachine machine(BlockEntity be) { return (SimpleTieredMachine)((MetaMachineBlockEntity)be).getMetaMachine(); }
    public static void verifyIdleBoundary(BlockEntity be,Direction side) {
        verifyControls(be);
        var m=machine(be);
        require(m.importItems.isEmpty() && m.exportItems.isEmpty() && m.importFluids.isEmpty() && m.exportFluids.isEmpty(),"GT_MACHINE_NOT_EMPTY");
    }
    private static void verifyControls(BlockEntity be) {
        var m=machine(be);
        require(m.getRecipeLogic().isIdle() && m.getProgress()==0 && m.isWorkingEnabled(),"GT_MACHINE_NOT_IDLE");
        for(int i=0;i<m.getChargerInventory().getSlots();i++)require(m.getChargerInventory().getStackInSlot(i).isEmpty(),"GT_BATTERY_PRESENT");
        require(m.getCoverContainer().getCovers().isEmpty(),"GT_COVER_BOUNDARY_UNSUPPORTED");
    }
    public static void verifyRecipeRoute(BlockEntity be,Direction side,MachineProfile p) {
        var m=machine(be);
        if(p.recipe().outputs().keySet().stream().anyMatch(AEItemKey.class::isInstance))require(m.isAutoOutputItems() && m.getOutputFacingItems()==side,"GT_ITEM_RETURN_ROUTE");
        if(p.recipe().outputs().keySet().stream().anyMatch(AEFluidKey.class::isInstance))require(m.isAutoOutputFluids() && m.getOutputFacingFluids()==side,"GT_FLUID_RETURN_ROUTE");
        if(p.recipe().inputs().keySet().stream().anyMatch(AEItemKey.class::isInstance) && m.getOutputFacingItems()==side)require(m.isAllowInputFromOutputSideItems(),"GT_ITEM_INPUT_DISABLED");
        if(p.recipe().inputs().keySet().stream().anyMatch(AEFluidKey.class::isInstance) && m.getOutputFacingFluids()==side)require(m.isAllowInputFromOutputSideFluids(),"GT_FLUID_INPUT_DISABLED");
    }
    static SimpleTieredMachine probe(BlockEntity source,Map<AEKey,BigInteger> inputs) {
        return probe(source,inputs,true);
    }
    private static SimpleTieredMachine probe(BlockEntity source,Map<AEKey,BigInteger> inputs,boolean exact) {
        var be=((EntityBlock)source.getBlockState().getBlock()).newBlockEntity(source.getBlockPos(),source.getBlockState());
        require(be!=null,"GT_PROBE_UNAVAILABLE");be.setLevel(source.getLevel());PlatformData.loadBlock(be,PlatformData.saveBlock(source));
        var m=machine(be);
        // Captured stock is owned separately. A recipe probe receives only its explicit inputs.
        for(var items:List.of(m.importItems,m.exportItems))
            for(int i=0;i<items.getSlots();i++)items.setStackInSlot(i,net.minecraft.world.item.ItemStack.EMPTY);
        for(var fluids:List.of(m.importFluids,m.exportFluids))
            for(int i=0;i<fluids.getTanks();i++)fluids.setFluidInTank(i,FluidStack.EMPTY);
        // Build native recipe handlers without loading the probe into the world's tick/subscription system.
        var handlers=new EnumMap<IO,List<IRecipeHandler<?>>>(IO.class);
        for(var trait:m.getTraits())if(trait instanceof com.gregtechceu.gtceu.api.machine.trait.IRecipeHandlerTrait<?> handler)
            handlers.computeIfAbsent(handler.getHandlerIO(),ignored->new ArrayList<>()).add(handler);
        handlers.forEach((io,list)->m.addHandlerList(com.gregtechceu.gtceu.api.machine.trait.RecipeHandlerList.of(io,list)));
        for(var entry:inputs.entrySet()) {
            int count=exact?entry.getValue().intValueExact():entry.getValue().min(BigInteger.valueOf(Integer.MAX_VALUE)).intValueExact();
            if(entry.getKey() instanceof AEItemKey key) {
                var left=key.toStack(count);
                for(int slot=0;slot<m.importItems.getSlots() && !left.isEmpty();slot++)left=m.importItems.insertItem(slot,left,false);
                require(!exact || left.isEmpty(),"GT_NATIVE_INPUT_CAPACITY");
            } else if(entry.getKey() instanceof AEFluidKey key) {
                var fluid=new FluidStack(key.getFluid(),count,key.getTag());
                int filled=m.importFluids.fill(fluid,IFluidHandler.FluidAction.EXECUTE);
                require(!exact || filled==count,"GT_NATIVE_FLUID_CAPACITY");
            } else throw new IllegalArgumentException("GT_UNSUPPORTED_MATERIAL");
        }
        return m;
    }
    public static Optional<MachineProfile> inspect(BlockEntity source,Map<AEKey,BigInteger> offered) {
        if(!supports(source))return Optional.empty();
        var m=probe(source,offered);
        var original=first(m);
        if(original==null || !deterministic(original))return Optional.empty();
        var modified=m.fullModifyRecipe(original.copy());
        if(modified==null || !deterministic(modified) || modified.duration<=0)return Optional.empty();
        // The native IO probe includes the captured circuit slot, so non-consumed circuits are never fabricated.
        if(!RecipeHelper.handleRecipeIO(m,original,IO.IN,new HashMap<>()).isSuccess())return Optional.empty();
        if(!m.importItems.isEmpty() || !m.importFluids.isEmpty())return Optional.empty();
        if(!RecipeHelper.handleRecipeIO(m,original,IO.OUT,new HashMap<>()).isSuccess())return Optional.empty();
        var outputs=outputs(m);
        if(outputs.isEmpty())return Optional.empty();
        var nativeOutputs=new LinkedHashMap<AEKey,BigInteger>();
        for(var stack:RecipeHelper.getOutputItems(modified))add(nativeOutputs,AEItemKey.of(stack),stack.getCount());
        for(var stack:RecipeHelper.getOutputFluids(modified))add(nativeOutputs,AEFluidKey.of(stack),stack.getAmount());
        if(!nativeOutputs.keySet().equals(outputs.keySet()))return Optional.empty();
        BigInteger parallel=null;
        for(var entry:outputs.entrySet()) {
            var ratio=nativeOutputs.get(entry.getKey()).divideAndRemainder(entry.getValue());
            if(ratio[1].signum()!=0 || ratio[0].signum()<=0 || parallel!=null && !parallel.equals(ratio[0]))return Optional.empty();
            parallel=ratio[0];
        }
        var expanded=new LinkedHashMap<AEKey,BigInteger>();final var factor=parallel;
        offered.forEach((k,n)->expanded.put(k,n.multiply(factor)));
        var check=probe(source,expanded);
        if(!RecipeHelper.handleRecipeIO(check,modified,IO.IN,new HashMap<>()).isSuccess()
                || !check.importItems.isEmpty() || !check.importFluids.isEmpty())return Optional.empty();
        if(!RecipeHelper.handleRecipeIO(check,modified,IO.OUT,new HashMap<>()).isSuccess()
                || !outputs(check).equals(nativeOutputs))return Optional.empty();
        var eu=modified.getInputEUt();
        var total=BigInteger.valueOf(eu.voltage()).multiply(BigInteger.valueOf(eu.amperage()));
        var divided=total.divideAndRemainder(parallel);
        if(divided[1].signum()!=0 || divided[0].signum()<=0)return Optional.empty();
        var nativeMachine=machine(source);var power=nativeMachine.energyContainer;
        var recipe=new Recipe<AEKey>(original.getId().toString(),offered,outputs,BigInteger.valueOf(modified.duration),
                divided[0],MachineProfile.GT_UNIT,BigInteger.valueOf(eu.voltage()));
        return Optional.of(new MachineProfile(recipe,BigInteger.valueOf(power.getEnergyCapacity()),
                BigInteger.valueOf(power.getInputVoltage()).multiply(BigInteger.valueOf(power.getInputAmperage())),
                BigInteger.valueOf(power.getEnergyStored()),BigInteger.ONE,BigInteger.ONE,parallel,
                power.getInputVoltage(),power.getInputAmperage()));
    }
    public static Optional<MachineProfile> discover(BlockEntity source,Map<AEKey,BigInteger> offered) {
        if(!supports(source) || offered.isEmpty() || offered.keySet().stream().anyMatch(k->!(k instanceof AEItemKey) && !(k instanceof AEFluidKey)))
            return Optional.empty();
        var probe=probe(source,offered,false);
        var candidates=probe.getRecipeType().searchRecipe(probe,r->RecipeHelper.matchRecipe(probe,r).isSuccess());
        if(!candidates.hasNext())return Optional.empty();
        var recipe=candidates.next();if(!deterministic(recipe))return Optional.empty();
        var before=inputStock(probe);
        if(!RecipeHelper.handleRecipeIO(probe,recipe,IO.IN,new HashMap<>()).isSuccess())return Optional.empty();
        var after=inputStock(probe);var consumed=new LinkedHashMap<AEKey,BigInteger>();
        before.forEach((key,n)->{var used=n.subtract(after.getOrDefault(key,BigInteger.ZERO));if(used.signum()>0)consumed.put(key,used);});
        return consumed.isEmpty()?Optional.empty():inspect(source,consumed);
    }
    private static Map<AEKey,BigInteger> inputStock(SimpleTieredMachine m) {
        var result=new LinkedHashMap<AEKey,BigInteger>();
        for(int i=0;i<m.importItems.getSlots();i++){var s=m.importItems.getStackInSlot(i);if(!s.isEmpty())add(result,AEItemKey.of(s),s.getCount());}
        for(int i=0;i<m.importFluids.getTanks();i++){var s=m.importFluids.getFluidInTank(i);if(!s.isEmpty())add(result,AEFluidKey.of(s),s.getAmount());}
        return Map.copyOf(result);
    }
    public static MachineProfile idleProfile(BlockEntity be) {
        var e=machine(be).energyContainer;
        return new MachineProfile(null,BigInteger.valueOf(e.getEnergyCapacity()),
                BigInteger.valueOf(e.getInputVoltage()).multiply(BigInteger.valueOf(e.getInputAmperage())),
                BigInteger.valueOf(e.getEnergyStored()),BigInteger.ONE,BigInteger.ONE,BigInteger.ONE,
                e.getInputVoltage(),e.getInputAmperage(),MachineProfile.GT_UNIT);
    }
    public static void verifyPassiveBoundary(BlockEntity be,Set<net.minecraft.core.BlockPos> captured) {
        verifyControls(be);
        for(var side:Direction.values()) {
            var neighbor=be.getBlockPos().relative(side);
            require(captured.contains(neighbor) || be.getLevel().getBlockEntity(neighbor)==null,"EXTRA_MACHINE_MATERIAL_BOUNDARY");
        }
    }
    private static GTRecipe first(SimpleTieredMachine m) {
        var candidates=m.getRecipeType().searchRecipe(m,r->RecipeHelper.matchRecipe(m,r).isSuccess());
        return candidates.hasNext()?candidates.next():null;
    }
    static boolean deterministic(GTRecipe recipe) {
        if(!recipe.conditions.isEmpty() || !recipe.ingredientActions.isEmpty() || !recipe.tickOutputs.isEmpty()
                || !recipe.tickInputs.keySet().equals(Set.of(EURecipeCapability.CAP)))return false;
        var caps=Set.of(ItemRecipeCapability.CAP,FluidRecipeCapability.CAP);
        if(!caps.containsAll(recipe.inputs.keySet()) || !caps.containsAll(recipe.outputs.keySet()))return false;
        for(var map:List.of(recipe.inputs,recipe.outputs,recipe.tickInputs))
            for(var list:map.values())for(var c:list)
                if(c.chance!=0 && c.chance!=c.maxChance || c.tierChanceBoost!=0
                        || c.content.getClass().getSimpleName().startsWith("IntProvider"))return false;
        return recipe.outputs.values().stream().flatMap(Collection::stream).allMatch(c->c.chance==c.maxChance);
    }
    private static Map<AEKey,BigInteger> outputs(SimpleTieredMachine m) {
        var result=new LinkedHashMap<AEKey,BigInteger>();
        for(int i=0;i<m.exportItems.getSlots();i++) { var stack=m.exportItems.getStackInSlot(i);if(!stack.isEmpty())add(result,AEItemKey.of(stack),stack.getCount()); }
        for(int i=0;i<m.exportFluids.getTanks();i++) { var stack=m.exportFluids.getFluidInTank(i);if(!stack.isEmpty())add(result,AEFluidKey.of(stack),stack.getAmount()); }
        return Map.copyOf(result);
    }
    private static void add(Map<AEKey,BigInteger> target,AEKey key,long n) {
        require(key!=null && n>0,"GT_EMPTY_NATIVE_OUTPUT");target.merge(key,BigInteger.valueOf(n),BigInteger::add);
    }
    public static void setStoredEnergy(BlockEntity be,BigInteger stored) {
        var energy=machine(be).energyContainer;
        energy.changeEnergy(stored.longValueExact()-energy.getEnergyStored());
        require(energy.getEnergyStored()==stored.longValueExact(),"GT_ENERGY_RESTORE_FAILED");
    }
    public static BigInteger energy(BlockEntity be) { return BigInteger.valueOf(machine(be).energyContainer.getEnergyStored()); }
    public static long feedEU(BlockEntity be,long voltage,long amps) { return machine(be).energyContainer.acceptEnergyFromNetwork(Direction.UP,voltage,amps); }
}
