package com.syaru.ae2machines.platform;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.syaru.ae2machines.engine.Recipe;
import com.syaru.ae2machines.engine.MeteredEnergy;
import com.syaru.ae2machines.runtime.MachineProfile;
import com.syaru.ae2machines.runtime.NativeEnergyInput;
import com.syaru.ae2machines.runtime.NativePowerReceiver;
import mekanism.api.Action;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.api.math.FloatingLong;
import mekanism.common.integration.energy.forgeenergy.ForgeEnergyIntegration;
import mekanism.api.recipes.ItemStackToItemStackRecipe;
import mekanism.common.tile.machine.*;
import mekanism.common.tile.prefab.TileEntityElectricMachine;
import mekanism.common.tile.prefab.TileEntityConfigurableMachine;
import mekanism.common.tile.factory.TileEntityItemStackToItemStackFactory;
import com.syaru.ae2machines.compat.mek.MekItemMachine;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.UnitDisplayUtils.EnergyUnit;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Loaded only when Mekanism is installed. Reads its live recipe and upgrade-adjusted machine. */
public final class MekanismAdapter {
    private static final Set<Class<?>> TYPES=Set.of(TileEntityEnrichmentChamber.class,
            TileEntityCrusher.class,TileEntityEnergizedSmelter.class,TileEntityItemStackToItemStackFactory.class);
    private MekanismAdapter() {}
    public static boolean itemMachine(BlockEntity block) { return block != null && TYPES.contains(block.getClass()); }
    public static void verifyPassiveBoundary(BlockEntity block,java.util.Set<net.minecraft.core.BlockPos> captured) {
        var machine=(mekanism.common.tile.prefab.TileEntityConfigurableMachine)block;
        if(itemMachine(block) && !MekItemMachine.idle(machine))
            throw new IllegalArgumentException("MACHINE_NOT_IDLE");
        if(machine instanceof mekanism.common.tile.prefab.TileEntityProgressMachine<?> progress && progress.getOperatingTicks()!=0)
            throw new IllegalArgumentException("MACHINE_NOT_IDLE");
        if(machine.getControlType()!=mekanism.common.tile.interfaces.IRedstoneControl.RedstoneControl.DISABLED
                || machine.getSecurity().getMode()!=mekanism.api.security.SecurityMode.PUBLIC)
            throw new IllegalArgumentException("PASSIVE_MACHINE_CONTROL_UNSUPPORTED");
        if(!machine.getComponent().getUpgradeSlot().isEmpty() || !machine.getComponent().getUpgradeOutputSlot().isEmpty()
                || machine.getInventorySlots(null).stream().anyMatch(s->s instanceof mekanism.common.inventory.slot.EnergyInventorySlot && !s.isEmpty()))
            throw new IllegalArgumentException("PASSIVE_MACHINE_PENDING_UPGRADE_OR_BATTERY");
        if(!itemMachine(block) && machine.getInventorySlots(null).stream().anyMatch(s->!s.isEmpty()
                && !(s instanceof mekanism.common.inventory.slot.InputInventorySlot)))
            throw new IllegalArgumentException("PASSIVE_CONTAINER_SLOT_NOT_EMPTY");
        for(var type:machine.getConfig().getTransmissions()) {
            if(type==mekanism.common.lib.transmitter.TransmissionType.ENERGY || type==mekanism.common.lib.transmitter.TransmissionType.HEAT)continue;
            var config=machine.getConfig().getConfig(type);
            for(var side:mekanism.api.RelativeSide.values())if(config.getDataType(side)!=mekanism.common.tile.component.config.DataType.NONE
                    && !captured.contains(block.getBlockPos().relative(side.getDirection(machine.getDirection()))))
                throw new IllegalArgumentException("EXTRA_MACHINE_MATERIAL_BOUNDARY");
        }
    }
    public static MachineProfile idleProfile(BlockEntity block) {
        var power=((mekanism.common.tile.prefab.TileEntityConfigurableMachine)block).getEnergyContainers(null).get(0);
        return new MachineProfile(null,units(power.getMaxEnergy().toString()),units(power.getMaxEnergy().toString()),
                units(power.getEnergy().toString()),units(EnergyUnit.FORGE_ENERGY.convertFrom(1).toString()),BigInteger.ONE,
                BigInteger.ONE,0,0,MachineProfile.MEK_UNIT);
    }
    public static boolean buffered(BlockEntity block){return TYPES.contains(block.getClass()) || chemicals() && MekChemicalAdapter.supports(block);}
    public static com.syaru.ae2machines.runtime.BufferedMachine buffer(BlockEntity block,Runnable changed) {
        return TYPES.contains(block.getClass())?new com.syaru.ae2machines.compat.mek.MekBufferedMachine(block,changed):MekMaterialAccess.create(block,changed);
    }
    public static boolean supports(BlockEntity block) { return TYPES.contains(block.getClass()) || chemicals() && MekChemicalAdapter.supports(block); }
    public static boolean chemicals() {
        try { Class.forName("me.ramidzkh.mekae2.ae2.MekanismKey",false,MekanismAdapter.class.getClassLoader());return true; }
        catch(ClassNotFoundException|LinkageError e) { return false; }
    }
    public static Optional<MachineProfile> inspect(BlockEntity block,Map<AEKey,BigInteger> inputs) {
        if(chemicals() && MekChemicalAdapter.supports(block))return MekChemicalAdapter.inspect(block,inputs);
        if(inputs.size()!=1)return Optional.empty();
        var entry=inputs.entrySet().iterator().next();
        if(!(entry.getKey() instanceof AEItemKey key) || entry.getValue().bitLength()>31)return Optional.empty();
        return inspect(block,key.toStack(entry.getValue().intValueExact()));
    }
    public static void verifyRecipeRoute(BlockEntity block,net.minecraft.core.Direction side,MachineProfile profile) {
        if(chemicals() && MekChemicalAdapter.supports(block))MekChemicalAdapter.verifyRecipeRoute(block,side,profile);
    }
    public static NativeEnergyInput energyInput(MeteredEnergy buffer) {
        return energyInput(NativePowerReceiver.reservoir(buffer),()->0L);
    }
    public static NativeEnergyInput energyInput(NativePowerReceiver receiver,java.util.function.LongSupplier clock) {
        var tick=new long[1];
        var nativeFE=new ForgeEnergyIntegration(new IStrictEnergyHandler() {
            public int getEnergyContainerCount() { return 1; }
            public FloatingLong getEnergy(int slot) { return slot==0 ? joules(receiver.stored()):FloatingLong.ZERO; }
            public FloatingLong getMaxEnergy(int slot) { return slot==0 ? nativeDemand(receiver.capacity(tick[0])):FloatingLong.ZERO; }
            public FloatingLong getNeededEnergy(int slot) { return slot==0 ? nativeDemand(receiver.capacity(tick[0]).subtract(receiver.stored())):FloatingLong.ZERO; }
            public void setEnergy(int slot,FloatingLong energy) { throw new UnsupportedOperationException("Input-only conversion view"); }
            public FloatingLong insertEnergy(int slot,FloatingLong offered,Action action) {
                if(slot!=0) return offered;
                var exact=units(offered.toString());
                return joules(exact.subtract(receiver.receive(exact,tick[0],action.simulate())));
            }
            public FloatingLong extractEnergy(int slot,FloatingLong amount,Action action) { return FloatingLong.ZERO; }
        });
        return new NativeEnergyInput() {
            public int receive(int offered,long now,boolean simulate) {
                if(offered<=0) return 0;
                tick[0]=now;return nativeFE.receiveEnergy(offered,simulate);
            }
            public int stored() { return nativeFE.getEnergyStored(); }
            public int capacity() { tick[0]=clock.getAsLong();return nativeFE.getMaxEnergyStored(); }
        };
    }
    private static FloatingLong nativeDemand(BigInteger n) {
        return joules(n.min(units(FloatingLong.MAX_VALUE.toString())));
    }
    private static FloatingLong joules(BigInteger exact) {
        return FloatingLong.parseFloatingLong(new BigDecimal(exact).movePointLeft(4).toPlainString());
    }
    public static void setStoredEnergy(BlockEntity block,BigInteger stored) {
        ((mekanism.common.tile.base.TileEntityMekanism)block).getEnergyContainers(null).get(0).setEnergy(mekanism.api.math.FloatingLong.parseFloatingLong(new BigDecimal(stored).movePointLeft(4).toPlainString()));
    }
    public static void verifyIdleBoundary(BlockEntity block,net.minecraft.core.Direction returnSide) {
        verifyIdleBoundary(block,returnSide,java.util.Set.of());
    }
    public static void verifyIdleBoundary(BlockEntity block,net.minecraft.core.Direction returnSide,java.util.Set<net.minecraft.core.BlockPos> captured) {
        if(chemicals() && MekChemicalAdapter.supports(block)) { MekChemicalAdapter.verifyIdleBoundary(block,returnSide,captured);return; }
        var machine=(TileEntityConfigurableMachine)block;
        if(!MekItemMachine.idle(machine) || machine.getInventorySlots(null).stream().anyMatch(s->!s.isEmpty()))
            throw new IllegalArgumentException("MACHINE_NOT_EMPTY_OR_IDLE");
        if(machine.getControlType()!=mekanism.common.tile.interfaces.IRedstoneControl.RedstoneControl.DISABLED)
            throw new IllegalArgumentException("EXTERNAL_REDSTONE_CONTROL");
        if(machine.getSecurity().getMode()!=mekanism.api.security.SecurityMode.PUBLIC)
            throw new IllegalArgumentException("MACHINE_SECURITY_NOT_SUPPORTED");
        var upgrades=machine.getComponent();
        if(!upgrades.getUpgradeSlot().isEmpty() || !upgrades.getUpgradeOutputSlot().isEmpty())
            throw new IllegalArgumentException("UPGRADE_INSTALLATION_PENDING");
        var config=machine.getConfig().getConfig(mekanism.common.lib.transmitter.TransmissionType.ITEM);
        if(!config.isEjecting() || returnSide==null ||
                config.getDataType(mekanism.api.RelativeSide.fromDirections(machine.getDirection(),returnSide))
                        !=mekanism.common.tile.component.config.DataType.INPUT_OUTPUT)
            throw new IllegalArgumentException("NATIVE_RETURN_ROUTE_NOT_CONFIGURED");
        for(var side:mekanism.api.RelativeSide.values()) {
            if(side.getDirection(machine.getDirection())!=returnSide &&
                    config.getDataType(side)!=mekanism.common.tile.component.config.DataType.NONE
                    && !captured.contains(block.getBlockPos().relative(side.getDirection(machine.getDirection()))))
                throw new IllegalArgumentException("EXTRA_MACHINE_ITEM_BOUNDARY");
        }
    }
    public static Optional<MachineProfile> inspect(BlockEntity block,ItemStack offered) {
        if (!itemMachine(block) || offered.isEmpty()) return Optional.empty();
        var machine=(TileEntityConfigurableMachine)block;
        if (machine.getLevel()==null || machine.getLevel().isClientSide || !MekanismUtils.canFunction(machine))
            return Optional.empty();
        var nativeRecipe=MekItemMachine.find(machine,offered);
        if (nativeRecipe==null || !fixedOutput(nativeRecipe)) return Optional.empty();
        var input=nativeRecipe.getInput().getMatchingInstance(offered);
        var output=nativeRecipe.getOutput(input);
        if (input.isEmpty() || output.isEmpty()) return Optional.empty();
        if(machine.getInventorySlots(null).stream().filter(s->s instanceof mekanism.common.inventory.slot.InputInventorySlot)
                .noneMatch(s->input.getCount()<=s.getLimit(input))
                || machine.getInventorySlots(null).stream().filter(s->s instanceof mekanism.common.inventory.slot.OutputInventorySlot)
                .noneMatch(s->output.getCount()<=s.getLimit(output)))return Optional.empty();
        var energy=MekItemMachine.energy(machine);
        if (!EnergyUnit.FORGE_ENERGY.isEnabled()) return Optional.empty();
        var recipe=new Recipe<AEKey>(nativeRecipe.getId().toString(),
                Map.of(AEItemKey.of(input),BigInteger.valueOf(input.getCount())),
                Map.of(AEItemKey.of(output),BigInteger.valueOf(output.getCount())),
                BigInteger.valueOf(MekItemMachine.ticks(machine)),units(energy.getEnergyPerTick().toString()),
                MachineProfile.MEK_UNIT,BigInteger.ZERO);
        return Optional.of(new MachineProfile(recipe,units(energy.getMaxEnergy().toString()),
                units(energy.getMaxEnergy().toString()),units(energy.getEnergy().toString()),
                units(EnergyUnit.FORGE_ENERGY.convertFrom(1).toString()),BigInteger.ONE,
                BigInteger.valueOf(MekItemMachine.lanes(machine)),0,0));
    }
    public static boolean fixedOutput(ItemStackToItemStackRecipe recipe) {
        try { return recipe.getClass().getMethod("getOutput",ItemStack.class).getDeclaringClass()==ItemStackToItemStackRecipe.class; }
        catch(ReflectiveOperationException e) { return false; }
    }
    public static BigInteger units(String nativeJoules) { return new BigDecimal(nativeJoules).movePointRight(4).toBigIntegerExact(); }
    public static BigInteger energy(BlockEntity block) { return units(((mekanism.common.tile.base.TileEntityMekanism)block).getEnergyContainers(null).get(0).getEnergy().toString()); }
    public static int feedFE(BlockEntity block,int offered,boolean simulate) {
        return block.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY,net.minecraft.core.Direction.UP)
                .map(cap->cap.receiveEnergy(offered,simulate)).orElse(0);
    }
}
