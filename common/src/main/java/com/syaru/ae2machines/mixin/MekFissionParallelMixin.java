package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.compat.mek.FissionParallelState;
import mekanism.common.lib.multiblock.MultiblockData;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Pseudo
@Mixin(targets="mekanism.generators.common.content.fission.FissionReactorMultiblockData",remap=false)
public abstract class MekFissionParallelMixin implements FissionParallelState.Access {
    @Unique private final FissionParallelState machines$groups=new FissionParallelState();
    @Shadow public double burnRemaining,partialWaste,reactorDamage,lastBurnRate,rateLimit;
    @Shadow public abstract long getMaxBurnRate();
    @Shadow public long lastBoilRate;
    @Shadow private boolean active,forceDisable;
    @Override public FissionParallelState machines$fissionGroups(){return machines$groups;}
    @Override public FissionParallelState.Frame machines$fissionRead(int count){
        var heat=((MultiblockData)(Object)this).getHeatCapacitors(null).get(0);
        return new FissionParallelState.Frame(count,heat.getHeat(),((MekHeatAccess)heat).ae2machines$pendingHeat(),burnRemaining,partialWaste,reactorDamage,forceDisable);
    }
    @Override public void machines$fissionWrite(FissionParallelState.Frame state){
        var heat=((MultiblockData)(Object)this).getHeatCapacitors(null).get(0);
        heat.setHeat(state.heat());((MekHeatAccess)heat).ae2machines$pendingHeat(state.pending());
        burnRemaining=state.fuelRemainder();partialWaste=state.wasteRemainder();reactorDamage=state.damage();forceDisable=state.disabled();
    }
    @Override public boolean machines$fissionActive(){return active;}
    @Override public void machines$fissionActive(boolean value){active=value;}
    @Override public double machines$fissionBurned(){return lastBurnRate;}
    @Override public double machines$fissionRate(){
        return Math.min(rateLimit,getMaxBurnRate());
    }
    @Override public long machines$fissionFuel(){return com.syaru.ae2machines.platform.MekPassiveChemical.fissionFuel((MultiblockData)(Object)this);}
    @Override public long machines$fissionBoiled(){return lastBoilRate;}
    @Override public void machines$fissionTotals(double burned,long boiled){lastBurnRate=burned;lastBoilRate=boiled;}
    @ModifyArg(method="burnFuel",at=@At(value="INVOKE",target="Ljava/lang/Math;min(DD)D",ordinal=0),index=0)
    private double machines$configuredRate(double base){return base*FissionParallelState.workLanes((MultiblockData)(Object)this);}
    @ModifyArg(method="burnFuel",at=@At(value="INVOKE",target="Ljava/lang/Math;min(DD)D",ordinal=1),index=1)
    private double machines$assemblyRate(double base){return base*FissionParallelState.workLanes((MultiblockData)(Object)this);}
}
