package com.syaru.ae2machines.compat.mek;

import com.syaru.ae2machines.platform.MekPassiveChemical;
import com.syaru.ae2machines.world.*;
import java.math.BigInteger;
import java.util.*;
import mekanism.common.lib.multiblock.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Native bulk transitions keep their real heat/material/energy constraints. */
public final class MekStructureParallel {
    private static final Set<String> TYPES=Set.of("mekanism.common.content.boiler.BoilerMultiblockData",
            "mekanism.common.content.sps.SPSMultiblockData","mekanism.common.content.tank.TankMultiblockData",
            "mekanism.generators.common.content.turbine.TurbineMultiblockData",
            "mekanism.generators.common.content.fission.FissionReactorMultiblockData",
            "mekanism.generators.common.content.fusion.FusionReactorMultiblockData");
    public interface Bound { BlockEntity machines$parallelOwner(); void machines$parallelOwner(BlockEntity owner); Level machines$structureWorld(); }
    private MekStructureParallel() {}
    public static boolean supports(BlockEntity owner){return owner instanceof IMultiblock<?> tile && tile.getMultiblock().isFormed() && TYPES.contains(tile.getMultiblock().getClass().getName());}
    public static int lanes(MultiblockData data){
        if(MekRestorationStorage.projecting())return 1;
        var owner=data instanceof Bound bound?bound.machines$parallelOwner():null;
        return owner!=null && owner.getLevel()!=null && NativeCellWorlds.owns(owner.getLevel())?NativeWorldRuntime.get((net.minecraft.server.level.ServerLevel)owner.getLevel()).state().nativeLanes():1;
    }
    public static boolean reactor(MultiblockData data){return data.getClass().getName().equals("mekanism.generators.common.content.fission.FissionReactorMultiblockData")
            || data.getClass().getName().equals("mekanism.generators.common.content.fusion.FusionReactorMultiblockData");}
    public static void prepareCache(MultiblockData data){prepare(data,((Bound)data).machines$structureWorld());}
    public static void cacheHeat(MultiblockData data,MultiblockCache<?> cache){
        var actual=data.getHeatCapacitors(null);var saved=cache.getHeatCapacitors(null);
        for(int i=0;i<Math.min(actual.size(),saved.size());i++)
            if(actual.get(i) instanceof MekStructureHeat heat && heat.machines$scaledHeat()
                    && saved.get(i) instanceof mekanism.common.capabilities.heat.BasicHeatCapacitor basic)
                basic.setHeatCapacity(heat.machines$baseHeatCapacity(),false);
        var fluids=data.getFluidTanks(null);var savedFluids=cache.getFluidTanks(null);
        for(int i=0;i<Math.min(fluids.size(),savedFluids.size());i++)
            if(fluids.get(i) instanceof MekWideFluidTank wide && savedFluids.get(i) instanceof MekWideFluidTank target)
                target.machines$fluidAmount(wide.machines$fluidAmount());
    }
    public static void enrollExtraFluids(MultiblockData data){if(data.isFormed() && data instanceof MekStructureFluids extra)extra.machines$enrollExtraFluids();}
    public static void reconfigured(java.util.List<BlockEntity> blocks,BigInteger before,BigInteger after){
        int oldWindow=before.min(BigInteger.valueOf(Integer.MAX_VALUE)).intValueExact();
        int newWindow=after.min(BigInteger.valueOf(Integer.MAX_VALUE)).intValueExact();
        if(oldWindow==newWindow)return;
        var seen=Collections.newSetFromMap(new IdentityHashMap<MultiblockData,Boolean>());
        for(var block:blocks)if(block instanceof IMultiblock<?> tile && tile.getMultiblock() instanceof MekFusionState fusion && seen.add(tile.getMultiblock()))
            fusion.machines$rescalePlasma((double)oldWindow/newWindow);
    }
    public static void prepare(MultiblockData data,Level world) {
        if(!NativeCellWorlds.owns(world) || !TYPES.contains(data.getClass().getName()) || !data.isFormed())return;
        enrollExtraFluids(data);
        var bound=(Bound)data;var owner=bound.machines$parallelOwner();
        if(owner!=null && owner.getLevel()==world && owner instanceof IMultiblock<?> tile && tile.getMultiblock()==data && !owner.isRemoved())return;
        owner=null;
        // Shaped structures such as the SPS have air at the cuboid's minimum corner.
        for(var pos:data.locations)if(world.getBlockEntity(pos) instanceof IMultiblock<?> tile && tile.getMultiblock()==data) {
            owner=world.getBlockEntity(pos);break;
        }
        if(owner==null)return;
        bound.machines$parallelOwner(owner);
        for(var tank:data.getFluidTanks(null))if(tank instanceof MekVirtualTank virtual)virtual.machines$owner(owner);
        MekPassiveChemical.prepareStructure(data,owner);
        for(var energy:data.getEnergyContainers(null))if(energy instanceof MekStructureEnergy virtual)virtual.machines$structureOwner(owner);
        if(reactor(data) || data instanceof mekanism.common.content.boiler.BoilerMultiblockData)
            for(var heat:data.getHeatCapacitors(null))if(heat instanceof MekStructureHeat virtual)virtual.machines$structureOwner(owner);
    }
    public static long capacity(Object tank,BlockEntity owner,long original){
        int lanes=MekWorldParallel.lanes(owner);
        long scaled=original>Long.MAX_VALUE/lanes?Long.MAX_VALUE:original*lanes;
        if(owner instanceof IMultiblock<?> tile && tile.getMultiblock() instanceof mekanism.common.content.sps.SPSMultiblockData sps
                && sps.inputTank==(Object)tank)
            scaled=Math.min(scaled,Integer.MAX_VALUE-(long)sps.inputProcessed);
        return scaled;
    }
    public static boolean fits(BlockEntity owner,BigInteger lanes){
        if(!supports(owner))return true;
        var data=((IMultiblock<?>)owner).getMultiblock();
        if(data instanceof MekFusionState fusion && !fusion.machines$plasmaFits((double)lanes(data)/lanes.min(BigInteger.valueOf(Integer.MAX_VALUE)).intValueExact()))return false;
        for(var tank:data.getFluidTanks(null))if(!(tank instanceof MekVirtualTank virtual)
                || MekWideFluidTank.amount(tank).compareTo(BigInteger.valueOf(virtual.machines$nativeCapacity()).multiply(lanes))>0)return false;
        for(var energy:data.getEnergyContainers(null))if(energy instanceof MekStructureEnergy virtual
                && new java.math.BigDecimal(String.valueOf(energy.getEnergy())).movePointRight(4).toBigIntegerExact().compareTo(virtual.machines$structureBaseEnergy().multiply(lanes))>0)return false;
        return MekPassiveChemical.fitsStructure(data,lanes);
    }
}
