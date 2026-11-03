package com.syaru.ae2machines.compat.gt;

import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.syaru.ae2machines.block.BaseEnergyPort;
import com.syaru.ae2machines.block.EnergyPortBlock;
import com.syaru.ae2machines.platform.GtEnergyRegistration;
import com.syaru.ae2machines.world.NativeCellWorlds;
import com.syaru.ae2machines.world.NativeWorldRuntime;
import java.util.*;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** A missing native output neighbor can reach the real EU port. The source still owns emission. */
public final class NativeEuOutput {
    private NativeEuOutput() {}
    public static IEnergyContainer target(Level level) {
        if(!(level instanceof ServerLevel world) || !NativeCellWorlds.owns(world))return null;
        var host=NativeWorldRuntime.get(world).energyHost();
        if(host==null || !host.factoryOnline() || !(host.getLevel() instanceof ServerLevel external))return null;
        var targets=new ArrayList<Map.Entry<IEnergyContainer,Direction>>();
        var seen=Collections.newSetFromMap(new IdentityHashMap<IEnergyContainer,Boolean>());
        for(var face:Direction.values()) {
            var p=host.getBlockPos().relative(face);
            if(!external.hasChunkAt(p) || !(external.getBlockEntity(p) instanceof BaseEnergyPort port)
                    || port.euTarget()!=host || port.getBlockState().getValue(EnergyPortBlock.FACING)!=face)continue;
            var neighbor=p.relative(face);
            if(!external.hasChunkAt(neighbor))continue;
            var be=external.getBlockEntity(neighbor);
            if(be==null || be instanceof BaseEnergyPort)continue;
            var cap=GtEnergyRegistration.nativeInput(external,be,face.getOpposite());
            if(cap!=null && cap.inputsEnergy(face.getOpposite()) && seen.add(cap))targets.add(Map.entry(cap,face.getOpposite()));
        }
        if(targets.isEmpty())return null;
        return new IEnergyContainer() {
            @Override public boolean inputsEnergy(Direction side){return true;}
            @Override public long acceptEnergyFromNetwork(Direction side,long voltage,long amps) {
                if(voltage<=0 || amps<=0)return 0;
                NativeWorldRuntime.get(world).externalPower();
                long accepted=0;
                for(var target:targets) {
                    long n=target.getKey().acceptEnergyFromNetwork(target.getValue(),voltage,amps-accepted);
                    if(n<0 || n>amps-accepted)throw new IllegalStateException("Invalid native EU output acknowledgement");
                    accepted+=n;if(accepted==amps)break;
                }
                return accepted;
            }
            @Override public long changeEnergy(long n){return 0;}
            @Override public long getEnergyStored(){return 0;}
            @Override public long getEnergyCapacity(){return 0;}
            @Override public long getInputVoltage(){return targets.stream().mapToLong(t->t.getKey().getInputVoltage()).max().orElse(0);}
            @Override public long getInputAmperage(){return Long.MAX_VALUE;}
        };
    }
}
