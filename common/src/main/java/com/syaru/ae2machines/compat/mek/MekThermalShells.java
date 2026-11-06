package com.syaru.ae2machines.compat.mek;

import com.syaru.ae2machines.mixin.*;
import com.syaru.ae2machines.platform.MekTimeClock;
import java.util.*;
import java.util.function.*;
import mekanism.common.lib.frequency.FrequencyType;
import mekanism.common.lib.multiblock.*;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.common.tile.prefab.*;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Only structural housekeeping is folded. Masters, real ports and heat barriers stay native. */
public final class MekThermalShells {
    private static final Set<String> DATA=Set.of("mekanism.common.content.boiler.BoilerMultiblockData",
        "mekanism.generators.common.content.fission.FissionReactorMultiblockData","mekanism.generators.common.content.turbine.TurbineMultiblockData");
    private static final Set<String> SHELLS=Set.of("mekanism.common.tile.prefab.TileEntityInternalMultiblock","mekanism.common.tile.TileEntityPressureDisperser",
        "mekanism.common.tile.multiblock.TileEntityStructuralGlass","mekanism.common.tile.multiblock.TileEntityBoilerCasing",
        "mekanism.generators.common.tile.TileEntityReactorGlass",
        "mekanism.generators.common.tile.fission.TileEntityFissionReactorCasing","mekanism.generators.common.tile.fission.TileEntityFissionAssembly",
        "mekanism.generators.common.tile.fission.TileEntityFissionFuelAssembly","mekanism.generators.common.tile.fission.TileEntityControlRodAssembly",
        "mekanism.generators.common.tile.turbine.TileEntityTurbineCasing","mekanism.generators.common.tile.turbine.TileEntityTurbineVent",
        "mekanism.generators.common.tile.turbine.TileEntityElectromagneticCoil","mekanism.generators.common.tile.turbine.TileEntitySaturatingCondenser",
        "mekanism.generators.common.tile.turbine.TileEntityRotationalComplex",
        "mekanism.generators.common.tile.turbine.TileEntityTurbineRotor");
    private static final Set<String> ACTIVE=Set.of("mekanism.common.tile.multiblock.TileEntityBoilerValve",
        "mekanism.generators.common.tile.fission.TileEntityFissionReactorPort","mekanism.generators.common.tile.turbine.TileEntityTurbineValve",
        "mekanism.common.tile.transmitter.TileEntityPressurizedTube","mekanism.common.tile.transmitter.TileEntityMechanicalPipe",
        "mekanism.common.tile.transmitter.TileEntityUniversalCable","mekanism.common.tile.multiblock.TileEntitySuperheatingElement");
    // These native ticks are retained, not folded. Their IO does not observe another tile's
    // housekeeping clock. Unknown owners or injected observers still reject the interval.
    private static final Set<String> NATIVE_WORK=Set.of(
        "mekanism.common.tile.machine.TileEntityChemicalInfuser",
        "mekanism.common.tile.machine.TileEntityChemicalOxidizer",
        "mekanism.common.tile.machine.TileEntityElectricPump",
        "mekanism.common.tile.machine.TileEntitySolarNeutronActivator",
        "mekanism.common.tile.machine.TileEntityRotaryCondensentrator",
        "mekanism.common.tile.machine.TileEntityIsotopicCentrifuge",
        "mekanism.common.tile.machine.TileEntityEnrichmentChamber",
        "mekanism.common.tile.machine.TileEntityEnergizedSmelter",
        "mekanism.common.tile.machine.TileEntityElectrolyticSeparator",
        "mekanism.common.tile.machine.TileEntityChemicalDissolutionChamber",
        "mekanism.common.tile.machine.TileEntityChemicalCrystallizer",
        "mekanism.common.tile.TileEntityFluidTank",
        "mekanism.common.tile.TileEntityChemicalTank",
        "mekanism.common.tile.TileEntityEnergyCube",
        "mekanism.common.tile.TileEntityBoundingBlock",
        "mekanism.common.tile.multiblock.TileEntitySPSCasing",
        "mekanism.common.tile.multiblock.TileEntitySPSPort",
        "mekanism.common.tile.multiblock.TileEntitySuperchargedCoil");
    public record Shell(BlockEntity owner,int offered,List<?> heat,BooleanSupplier quiet,Object group,BooleanSupplier driver){
        public int clockLimit(){
            if(!(owner instanceof TileEntityMekanism m))return offered;
            int delay=((MekTimeBaseAccess)m).machines$timeDelay();
            return delay>0?Math.min(offered,delay-1):offered;
        }
        public void advance(int ticks,long through){
            if(owner instanceof TileEntityMekanism m)MekTimeClock.advanceShell(m,ticks,through);
        }
    }
    private MekThermalShells(){}
    private static final String FISSION_CASING="mekanism.generators.common.tile.fission.TileEntityFissionReactorCasing";
    private enum Role { UNKNOWN, ACTIVE, SHELL, FISSION, VENT, HEATER, PIPE }
    private static final ClassValue<Role> ROLES=new ClassValue<>(){
        @Override protected Role computeValue(Class<?> type){
            String name=type.getName();
            if(FISSION_CASING.equals(name))return Role.FISSION;
            if(name.equals("mekanism.generators.common.tile.turbine.TileEntityTurbineVent"))return Role.VENT;
            if(name.equals("mekanism.common.tile.multiblock.TileEntitySuperheatingElement"))return Role.HEATER;
            if(type==mekanism.common.tile.transmitter.TileEntityPressurizedTube.class
                    || type==mekanism.common.tile.transmitter.TileEntityMechanicalPipe.class
                    || type==mekanism.common.tile.transmitter.TileEntityUniversalCable.class)return Role.PIPE;
            return SHELLS.contains(name)?Role.SHELL:ACTIVE.contains(name)?Role.ACTIVE:Role.UNKNOWN;
        }
    };
    // Cache the native method, never the answer: sound eligibility belongs to the live owner.
    private static final ClassValue<Optional<java.lang.reflect.Method>> SOUND=new ClassValue<>(){
        @Override protected Optional<java.lang.reflect.Method> computeValue(Class<?> type){
            try{return Optional.of(type.getMethod("handlesSound",Class.forName(FISSION_CASING,false,type.getClassLoader())));}
            catch(ReflectiveOperationException unavailable){return Optional.empty();}
        }
    };
    public static boolean known(BlockEntity be){return ROLES.get(be.getClass())!=Role.UNKNOWN;}
    public static boolean nativeWork(BlockEntity be){return NATIVE_WORK.contains(be.getClass().getName());}
    public static boolean data(MultiblockData d){return d!=null && DATA.contains(d.getClass().getName()) && d.isFormed() && !d.recheckStructure;}
    public static boolean bounded(MultiblockData d,int ticks){
        if(!data(d))return false;
        if(!d.getClass().getName().contains("fission"))return true;
        try{
            var type=d.getClass();double damage=type.getField("reactorDamage").getDouble(d),rate=type.getField("rateLimit").getDouble(d);
            var config=Class.forName("mekanism.generators.common.config.MekanismGeneratorsConfig").getField("generators").get(null);
            var entry=config.getClass().getField("energyPerFissionFuel").get(config);var amount=entry.getClass().getMethod("get").invoke(entry);
            double price=((Number)amount).doubleValue();var cap=d.getHeatCapacitors(null).get(0);
            double pending=((MekHeatAccess)cap).ae2machines$pendingHeat();
            // Upper bound: every available tick burns at the configured rate, with no cooling.
            double heat=cap.getHeat()+Math.max(0,pending)+rate*MekStructureParallel.lanes(d)*(ticks+1d)*price;
            double bound=heat/cap.getHeatCapacity();
            return damage<98 && rate>=0 && price>=0 && Double.isFinite(bound) && bound<1190;
        }catch(ReflectiveOperationException|ClassCastException changed){return false;}
    }
    private static boolean stable(Structure s,long now){
        var a=(MekThermalStructureAccess)s;
        return s.isValid() && s.getController()!=null && data(s.getMultiblockData())
            && (a.machines$thermalUpdated() || a.machines$thermalUpdate()<now-1);
    }
    public static Shell plan(BlockEntity raw,int count){
        boolean circuit=com.syaru.ae2machines.world.NativeCircuitOptions.ENABLED;
        var role=ROLES.get(raw.getClass());
        boolean heater=circuit && role==Role.HEATER;
        boolean fission=circuit && role==Role.FISSION;
        if(circuit && role==Role.PIPE){
            var tile=(mekanism.common.tile.transmitter.TileEntityTransmitter)raw;
            if(raw.isRemoved() || !tile.getTransmitter().hasTransmitterNetwork() || !MekThermalHooks.safe(tile.getTransmitter().getClass()))return null;
            BooleanSupplier quiet=()->{
                if(!MekTimeClock.transmitterQuiet(tile))return false;
                for(var side:Direction.values())if(tile.getTransmitter().getConnectionTypeRaw(side)==mekanism.common.lib.transmitter.ConnectionType.PULL)return false;
                return true;
            };
            if(!quiet.getAsBoolean())return null;
            // Native transmitters use their own ticker, which does not advance the Mek tile clock.
            return new Shell(raw,count,List.of(),quiet,tile.getTransmitter().getTransmitterNetwork(),()->false);
        }
        if((role!=Role.SHELL && role!=Role.FISSION && role!=Role.VENT && !heater) || raw.isRemoved())return null;
        var m=(TileEntityMekanism)raw;
        // No frequency, comparator, upgrade, redstone or activation event can be hidden here.
        var f=m.getFrequencyComponent();var flags=(MekThermalFrequencyAccess)f;
        if(m.supportsUpgrades() || m.supportsComparator() || m.supportsRedstone() || m.isActivatable() && !heater && !fission
                || f.hasCustomFrequencies() || f.getFrequency(FrequencyType.SECURITY)!=null || flags.machines$thermalSave() || flags.machines$thermalNotify())return null;
        MultiblockData owner;BooleanSupplier quiet=null,driver=null;
        boolean clientActive=((MekTimeBaseAccess)m).machines$thermalClientActive();
        if(m instanceof TileEntityMultiblock<?> tile){
            owner=tile.getMultiblock();var state=(MekThermalTileAccess)tile;
            if(tile.isMaster() || !data(owner) || !state.machines$thermalFormed() || state.machines$thermalId()!=owner.inventoryID
                    || !stable(tile.getStructure(),m.getLevel().getGameTime()))return null;
            if(role==Role.FISSION){
                try{
                    var method=SOUND.get(owner.getClass()).orElse(null);
                    if(method==null)return null;
                    boolean soundEnabled=(boolean)method.invoke(owner,m);
                    if(soundEnabled && !circuit)return null;
                    if(fission){
                        var data=(MekThermalFissionDataAccess)owner;var sound=(MekThermalFissionTileAccess)m;
                        driver=()->data.machines$thermalBurnRate()>0;
                        quiet=()->clientActive==m.getActive()
                            && sound.machines$thermalPreviousBurning()==(soundEnabled && data.machines$thermalBurnRate()>0);
                    }
                }
                catch(ReflectiveOperationException changed){return null;}
            }
            if(role==Role.VENT)for(var side:Direction.values()){
                var pos=m.getBlockPos().relative(side);
                if(!com.syaru.ae2machines.world.NativeRegionMove.contains(pos,owner.getMinPos(),owner.getMaxPos())){
                    var outside=m.getLevel().getBlockState(pos);
                    if(!outside.isAir() && !outside.is(net.minecraft.world.level.block.Blocks.BARRIER))return null;
                }
            }
        }else if(m instanceof TileEntityInternalMultiblock tile){owner=tile.getMultiblock();if(!data(owner))return null;
            if(heater){
                var data=owner;driver=()->mekanism.common.content.boiler.BoilerMultiblockData.hotMap.getBoolean(data.inventoryID);quiet=()->{
                    boolean active=mekanism.common.content.boiler.BoilerMultiblockData.hotMap.getBoolean(data.inventoryID);
                    return m.getActive()==active && clientActive==active;
                };
            }
        }else if(m instanceof TileEntityStructuralMultiblock tile){
            String active=null;owner=null;
            for(var e:tile.getStructureMap().entrySet()){
                if(!stable(e.getValue(),m.getLevel().getGameTime()))return null;
                if(active==null){active=e.getKey().getNameLower();owner=e.getValue().getMultiblockData();}
            }
            if(owner==null || !MekTimeClock.glassStable(tile,active))return null;
        }else return null;
        var shell=new Shell(m,count,List.copyOf(m.getHeatCapacitors(null)),quiet,owner,driver);
        return quiet==null && shell.clockLimit()<count?null:shell;
    }
}
