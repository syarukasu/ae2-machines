package com.syaru.ae2machines.platform;

import appeng.api.stacks.*;
import com.syaru.ae2machines.engine.Recipe;
import com.syaru.ae2machines.runtime.MachineProfile;
import mekanism.api.Upgrade;
import mekanism.api.RelativeSide;
import mekanism.api.chemical.ChemicalStack;
import mekanism.common.tile.machine.*;
import mekanism.common.tile.prefab.TileEntityConfigurableMachine;
import mekanism.common.tile.prefab.TileEntityProgressMachine;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.component.config.slot.*;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;
import java.math.BigInteger;
import java.util.*;
import static com.syaru.ae2machines.world.NativeFactoryCompiler.require;

/** Exact coefficients from Mekanism recipes. Chemical identities are owned by Applied Mekanistics. */
public final class MekChemicalAdapter {
    private MekChemicalAdapter() {}
    public static boolean supports(BlockEntity be) {
        return be.getClass()==TileEntityChemicalOxidizer.class || be.getClass()==TileEntityChemicalInfuser.class
                || be.getClass()==TileEntityRotaryCondensentrator.class;
    }
    public static void verifyIdleBoundary(BlockEntity be,Direction side) {
        verifyIdleBoundary(be,side,Set.of());
    }
    public static void verifyIdleBoundary(BlockEntity be,Direction side,Set<net.minecraft.core.BlockPos> captured) {
        var machine=(TileEntityConfigurableMachine)be;
        require(!(machine instanceof TileEntityProgressMachine<?> p) || p.getOperatingTicks()==0,"MACHINE_NOT_IDLE");
        require(machine.getInventorySlots(null).stream().allMatch(s->s.isEmpty())
                && machine.getFluidTanks(null).stream().allMatch(t->t.isEmpty())
                && machine.getChemicalTanks(null).stream().allMatch(t->t.isEmpty()),"MACHINE_NOT_EMPTY");
        require(machine.getControlType()==mekanism.common.tile.interfaces.IRedstoneControl.RedstoneControl.DISABLED,"EXTERNAL_REDSTONE_CONTROL");
        require(machine.getSecurity().getMode()==mekanism.api.security.SecurityMode.PUBLIC,"MACHINE_SECURITY_NOT_SUPPORTED");
        require(machine.getComponent().getUpgradeSlot().isEmpty() && machine.getComponent().getUpgradeOutputSlot().isEmpty(),"UPGRADE_INSTALLATION_PENDING");
        for(var type:machine.getConfig().getTransmissions()) {
            if(type==TransmissionType.ENERGY || type==TransmissionType.HEAT)continue;
            var config=machine.getConfig().getConfig(type);
            for(var relative:RelativeSide.values()) require(relative.getDirection(machine.getDirection())==side
                    || config.getDataType(relative)==DataType.NONE
                    || captured.contains(be.getBlockPos().relative(relative.getDirection(machine.getDirection()))),"EXTRA_MACHINE_MATERIAL_BOUNDARY");
        }
    }
    public static Optional<MachineProfile> inspect(BlockEntity be,Map<AEKey,BigInteger> inputs) {
        return inspect(be,inputs,true);
    }
    public static Optional<MachineProfile> discover(BlockEntity be,Map<AEKey,BigInteger> inputs) {
        return inspect(be,inputs,false);
    }
    private static Optional<MachineProfile> inspect(BlockEntity be,Map<AEKey,BigInteger> inputs,boolean exact) {
        var machine=(TileEntityConfigurableMachine)be;
        if(!machine.canFunction())return Optional.empty();
        var in=new LinkedHashMap<AEKey,BigInteger>();var out=new LinkedHashMap<AEKey,BigInteger>();
        String id;int ticks=1;BigInteger parallel=BigInteger.ONE;
        if(be instanceof TileEntityChemicalOxidizer m) {
            if(inputs.size()!=1 || !(inputs.keySet().iterator().next() instanceof AEItemKey key))return Optional.empty();
            var offered=key.toStack(inputs.get(key).intValueExact());var r=m.findFirstRecipe(offered);
            if(r==null || !fixed(r,"getOutput",mekanism.api.recipes.basic.BasicItemStackToChemicalRecipe.class,ItemStack.class))return Optional.empty();
            var input=r.getInput().getMatchingInstance(offered);var output=r.getOutput(input);
            if(input.isEmpty() || output.isEmpty())return Optional.empty();
            if(machine.getInventorySlots(null).stream().filter(s->s instanceof mekanism.common.inventory.slot.InputInventorySlot)
                    .noneMatch(s->input.getCount()<=s.getLimit(input)) || output.getAmount()>m.gasTank.getCapacity())return Optional.empty();
            add(in,AEItemKey.of(input),input.getCount());add(out,MekanismKey.of(output),output.getAmount());
            id=m.getRecipeType().getRecipes(m.getLevel()).stream().filter(h->h.value()==r).findFirst().orElseThrow().id().toString();
            ticks=m.getTicksRequired();
        } else if(be instanceof TileEntityRotaryCondensentrator m) {
            if(inputs.size()!=1)return Optional.empty();
            var entry=inputs.entrySet().iterator().next();
            if(m.getMode() && entry.getKey() instanceof AEFluidKey key) {
                var offered=fluid(key,entry.getValue().intValueExact());var r=m.getRecipeType().getInputCache().findFirstRecipe(m.getLevel(),offered);
                if(r==null || !r.hasFluidToChemical() || !fixed(r,"getChemicalOutput",mekanism.api.recipes.basic.BasicRotaryRecipe.class,FluidStack.class))return Optional.empty();
                var input=r.getFluidInput().getMatchingInstance(offered);var output=r.getChemicalOutput(input);
                if(input.isEmpty() || output.isEmpty())return Optional.empty();
                if(input.getAmount()>m.fluidTank.getCapacity() || output.getAmount()>m.gasTank.getCapacity())return Optional.empty();
                add(in,fluidKey(input),input.getAmount());add(out,MekanismKey.of(output),output.getAmount());
                id=m.getRecipeType().getRecipes(m.getLevel()).stream().filter(h->h.value()==r).findFirst().orElseThrow().id().toString();
            } else if(!m.getMode() && entry.getKey() instanceof MekanismKey key && key.getStack() instanceof ChemicalStack) {
                var offered=(ChemicalStack)key.withAmount(entry.getValue().longValueExact());var r=m.getRecipeType().getInputCache().findFirstRecipe(m.getLevel(),offered);
                if(r==null || !r.hasChemicalToFluid() || !fixed(r,"getFluidOutput",mekanism.api.recipes.basic.BasicRotaryRecipe.class,ChemicalStack.class))return Optional.empty();
                var input=r.getChemicalInput().getMatchingInstance(offered);var output=r.getFluidOutput(input);
                if(input.isEmpty() || output.isEmpty())return Optional.empty();
                if(input.getAmount()>m.gasTank.getCapacity() || output.getAmount()>m.fluidTank.getCapacity())return Optional.empty();
                add(in,MekanismKey.of(input),input.getAmount());add(out,fluidKey(output),output.getAmount());
                id=m.getRecipeType().getRecipes(m.getLevel()).stream().filter(h->h.value()==r).findFirst().orElseThrow().id().toString();
            } else return Optional.empty();
            parallel=BigInteger.ONE.shiftLeft(m.getComponent().getUpgrades(Upgrade.SPEED));
        } else if(be instanceof TileEntityChemicalInfuser m) {
            if(inputs.size()!=2)return Optional.empty();
            var samples=new ArrayList<ChemicalStack>();
            for(var entry:inputs.entrySet()) {
                if(!(entry.getKey() instanceof MekanismKey key) || !(key.getStack() instanceof ChemicalStack))return Optional.empty();
                samples.add((ChemicalStack)key.withAmount(entry.getValue().longValueExact()));
            }
            var a=samples.get(0);var b=samples.get(1);
            var r=m.getRecipeType().getInputCache().findFirstRecipe(m.getLevel(),a,b);
            if(r==null || !fixed(r,"getOutput",mekanism.api.recipes.basic.BasicChemicalChemicalToChemicalRecipe.class,ChemicalStack.class,ChemicalStack.class))return Optional.empty();
            if(!r.getLeftInput().test(a)) { var tmp=a;a=b;b=tmp; }
            var left=r.getLeftInput().getMatchingInstance(a);var right=r.getRightInput().getMatchingInstance(b);
            if(left.isEmpty() || right.isEmpty())return Optional.empty();
            var output=r.getOutput(left,right);if(output.isEmpty())return Optional.empty();
            if(left.getAmount()>m.leftTank.getCapacity() || right.getAmount()>m.rightTank.getCapacity() || output.getAmount()>m.centerTank.getCapacity())return Optional.empty();
            add(in,MekanismKey.of(left),left.getAmount());add(in,MekanismKey.of(right),right.getAmount());
            add(out,MekanismKey.of(output),output.getAmount());
            id=m.getRecipeType().getRecipes(m.getLevel()).stream().filter(h->h.value()==r).findFirst().orElseThrow().id().toString();
            parallel=BigInteger.ONE.shiftLeft(m.getComponent().getUpgrades(Upgrade.SPEED));
        } else return Optional.empty();
        if(exact && !in.equals(inputs))return Optional.empty();
        var energy=(mekanism.common.capabilities.energy.MachineEnergyContainer<?>)machine.getEnergyContainers(null).get(0);
        var recipe=new Recipe<AEKey>(id,in,out,BigInteger.valueOf(ticks),MekanismAdapter.units(energy.getEnergyPerTick()),MachineProfile.MEK_UNIT,BigInteger.ZERO);
        return Optional.of(new MachineProfile(recipe,MekanismAdapter.units(energy.getMaxEnergy()),MekanismAdapter.units(energy.getMaxEnergy()),
                MekanismAdapter.units(energy.getEnergy()),java.math.BigDecimal.valueOf(mekanism.common.util.UnitDisplayUtils.EnergyUnit.FORGE_ENERGY.getConversion()).movePointRight(4).toBigIntegerExact(),
                BigInteger.valueOf(10000),parallel,0,0));
    }
    public static void verifyRecipeRoute(BlockEntity be,Direction side,MachineProfile profile) {
        var machine=(TileEntityConfigurableMachine)be;var relative=RelativeSide.fromDirections(machine.getDirection(),side);
        for(boolean output:new boolean[]{false,true}) {
            for(var key:(output?profile.recipe().outputs():profile.recipe().inputs()).keySet()) {
                var type=key instanceof AEItemKey ? TransmissionType.ITEM:key instanceof AEFluidKey ? TransmissionType.FLUID:TransmissionType.CHEMICAL;
                var config=machine.getConfig().getConfig(type);var slot=config==null?null:config.getSlotInfo(relative);
                require(slot!=null && (output?slot.canOutput() && config.isEjecting():slot.canInput()),"NATIVE_MATERIAL_ROUTE_NOT_CONFIGURED");
                if(type==TransmissionType.CHEMICAL && be instanceof TileEntityChemicalInfuser) {
                    require(slot instanceof ChemicalSlotInfo info && info.getTanks().containsAll(machine.getChemicalTanks(null)),"NATIVE_CHEMICAL_ROUTE_INCOMPLETE");
                }
            }
        }
    }
    private static void add(Map<AEKey,BigInteger> map,AEKey key,long n) {
        require(key!=null && n>0,"EMPTY_NATIVE_MATERIAL");map.merge(key,BigInteger.valueOf(n),BigInteger::add);
    }
    private static boolean fixed(Object r,String method,Class<?> owner,Class<?>... args) {
        try { return r.getClass().getMethod(method,args).getDeclaringClass()==owner; }
        catch(ReflectiveOperationException e) { return false; }
    }
    private static FluidStack fluid(AEFluidKey key,int n) { return key.toStack(n); }
    private static AEFluidKey fluidKey(FluidStack stack) { return AEFluidKey.of(stack); }
}
