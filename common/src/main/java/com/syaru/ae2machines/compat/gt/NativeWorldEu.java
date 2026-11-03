package com.syaru.ae2machines.compat.gt;

import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.syaru.ae2machines.engine.PowerDistribution;
import com.syaru.ae2machines.platform.GtEnergyRegistration;
import java.util.*;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class NativeWorldEu {
    private NativeWorldEu() {}
    private record Port(IEnergyContainer energy,Direction side) {
        long receive(long voltage,long offered,boolean simulate) {
            if(voltage<=0 || voltage>energy.getInputVoltage() || !energy.inputsEnergy(side))return 0;
            if(!simulate)return energy.acceptEnergyFromNetwork(side,voltage,offered);
            // GT has no simulate overload. Read its actual per-native-tick packet budget.
            return Math.min(offered,Math.min(GtEnergyRegistration.remainingNativeAmps(energy),
                    Math.max(0,energy.getEnergyCanBeInserted())/voltage));
        }
    }
    private static List<Port> ports(ServerLevel world,List<BlockEntity> blocks) {
        var result=new ArrayList<Port>();
        var seen=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());
        for(var be:blocks) {
            if(com.syaru.ae2machines.runtime.NativeWorldBatches.owns(be))continue;
            for(var side:Direction.values()) {
            var cap=GtEnergyRegistration.nativeInput(world,be,side);
            // Do not guess packet accounting of third-party energy container implementations.
            if(cap==null || GtEnergyRegistration.remainingNativeAmps(cap)<0 || !cap.inputsEnergy(side))continue;
            if(seen.add(cap))result.add(new Port(cap,side));
            }
        }
        return result;
    }
    private static List<Port> endpointPorts(ServerLevel world,List<com.syaru.ae2machines.runtime.NativeWorldPower.Target> targets){
        var result=new ArrayList<Port>();var seen=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());
        for(var target:targets){
            var cap=GtEnergyRegistration.nativeInput(world,target.block(),target.side());
            if(cap!=null && GtEnergyRegistration.remainingNativeAmps(cap)>=0 && cap.inputsEnergy(target.side()) && seen.add(cap))result.add(new Port(cap,target.side()));
        }
        return result;
    }
    public static long receiveEndpoints(ServerLevel world,List<com.syaru.ae2machines.runtime.NativeWorldPower.Target> targets,long voltage,long amps,long tick,boolean simulate){
        return PowerDistribution.receive(amps,tick,simulate,endpointPorts(world,targets).stream().<PowerDistribution.Receiver>map(port->(n,probe)->port.receive(voltage,n,probe)).toList());
    }
    public static long voltageEndpoints(ServerLevel world,List<com.syaru.ae2machines.runtime.NativeWorldPower.Target> targets){return endpointPorts(world,targets).stream().mapToLong(p->p.energy.getInputVoltage()).max().orElse(0);}
    public static long demandEndpoints(ServerLevel world,List<com.syaru.ae2machines.runtime.NativeWorldPower.Target> targets){
        long sum=0;for(var port:endpointPorts(world,targets))sum+=Math.min(Math.max(0,port.energy.getEnergyCanBeInserted()),Long.MAX_VALUE-sum);return sum;
    }
    public static long receive(ServerLevel world,List<BlockEntity> blocks,long voltage,long amps,long tick,boolean simulate) {
        var targets=ports(world,blocks).stream().<PowerDistribution.Receiver>map(port->(n,probe)->port.receive(voltage,n,probe)).toList();
        return PowerDistribution.receive(amps,tick,simulate,targets);
    }
    public static long voltage(ServerLevel world,List<BlockEntity> blocks) {
        return ports(world,blocks).stream().mapToLong(p->p.energy.getInputVoltage()).max().orElse(0);
    }
    public static long demand(ServerLevel world,List<BlockEntity> blocks) {
        long sum=0;
        for(var port:ports(world,blocks)) {
            long free=Math.max(0,port.energy.getEnergyCanBeInserted());
            sum+=Math.min(free,Long.MAX_VALUE-sum);
        }
        return sum;
    }
}
