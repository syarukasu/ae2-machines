package com.syaru.ae2machines.compat.mek;

import com.syaru.ae2machines.mixin.*;
import com.syaru.ae2machines.platform.MekTimeClock;
import com.syaru.ae2machines.world.NativeQuietStep;
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
    public record Shell(BlockEntity owner,NativeQuietStep clock,List<?> heat,BooleanSupplier quiet,Supplier<NativeQuietStep> reset,Object group,BooleanSupplier driver){}
    private MekThermalShells(){}
    public static boolean known(BlockEntity be){return SHELLS.contains(be.getClass().getName()) || ACTIVE.contains(be.getClass().getName());}
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
        boolean heater=circuit && raw.getClass().getName().equals("mekanism.common.tile.multiblock.TileEntitySuperheatingElement");
        boolean fission=circuit && raw.getClass().getName().equals("mekanism.generators.common.tile.fission.TileEntityFissionReactorCasing");
        if(circuit && (raw.getClass()==mekanism.common.tile.transmitter.TileEntityPressurizedTube.class || raw.getClass()==mekanism.common.tile.transmitter.TileEntityMechanicalPipe.class)){
            var tile=(mekanism.common.tile.transmitter.TileEntityTransmitter)raw;
            if(raw.isRemoved() || !tile.getTransmitter().hasTransmitterNetwork() || !MekThermalHooks.safe(tile.getTransmitter().getClass()))return null;
            BooleanSupplier quiet=()->{
                if(!MekTimeClock.transmitterQuiet(tile))return false;
                for(var side:Direction.values())if(tile.getTransmitter().getConnectionTypeRaw(side)==mekanism.common.lib.transmitter.ConnectionType.PULL)return false;
                return true;
            };
            if(!quiet.getAsBoolean())return null;
            // Native transmitters use their own ticker, which does not advance the Mek tile clock.
            Supplier<NativeQuietStep> reset=()->new NativeQuietStep(count,n->{},()->{});
            return new Shell(raw,reset.get(),List.of(),quiet,reset,tile.getTransmitter().getTransmitterNetwork(),()->false);
        }
        if((!SHELLS.contains(raw.getClass().getName()) && !heater) || raw.isRemoved())return null;
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
            if(raw.getClass().getName().contains("FissionReactorCasing")){
                try{
                    boolean soundEnabled=(boolean)owner.getClass().getMethod("handlesSound",m.getClass()).invoke(owner,m);
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
            if(raw.getClass().getName().endsWith("TurbineVent"))for(var side:Direction.values()){
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
        var clock=MekTimeClock.plan(m,count);if(quiet==null && clock.limit()<count)return null;
        return new Shell(m,clock,List.copyOf(m.getHeatCapacitors(null)),quiet,()->MekTimeClock.plan(m,count),owner,driver);
    }
}
