package com.syaru.ae2machines.runtime;

import com.syaru.ae2machines.engine.PowerDistribution;
import com.syaru.ae2machines.platform.*;
import com.syaru.ae2machines.world.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Dispatches real payments to native, sided capabilities; owns no energy reservoir. */
public final class NativeWorldPower {
    public record Fe(Object identity, PowerDistribution.Receiver transfer,java.util.function.IntSupplier stored) {
        /** Faces of one account are alternatives, not additional energy reservoirs. */
        public static Fe faces(Object identity,List<Fe> faces) {
            if(faces.isEmpty())return null;
            var ports=List.copyOf(faces);
            return new Fe(identity,(amount,simulate)->{
                Fe best=null;long accepted=0;
                for(var port:ports) {
                    long possible=port.transfer.receive(amount,true);
                    if(possible<0 || possible>amount)throw new IllegalStateException("Invalid native FE simulation");
                    if(possible>accepted){best=port;accepted=possible;}
                }
                return best==null?0:simulate?accepted:best.transfer.receive(amount,false);
            },()->ports.stream().mapToInt(port->port.stored.getAsInt()).max().orElse(0));
        }
    }
    private final ServerLevel world;
    private final BlockPos min,max;
    private final boolean internalRouting;
    private final List<BlockPos> endpoints;
    public NativeWorldPower(ServerLevel world,BlockPos min,BlockPos max) {
        this(world,min,max,false,List.of());
    }
    public NativeWorldPower(ServerLevel world,BlockPos min,BlockPos max,boolean internalRouting,List<BlockPos> endpoints){
        this.world=world;this.min=min;this.max=max;this.internalRouting=internalRouting;this.endpoints=List.copyOf(endpoints);
    }
    public List<BlockEntity> blocks() {
        var result=new ArrayList<BlockEntity>();
        for(int x=min.getX()>>4;x<=max.getX()>>4;x++)for(int z=min.getZ()>>4;z<=max.getZ()>>4;z++) {
            var chunk=world.getChunkSource().getChunkNow(x,z);
            if(chunk==null)continue;
            for(var be:chunk.getBlockEntities().values()) {
                var p=be.getBlockPos();
                if(!be.isRemoved() && p.getX()>=min.getX() && p.getX()<=max.getX()
                        && p.getY()>=min.getY() && p.getY()<=max.getY() && p.getZ()>=min.getZ() && p.getZ()<=max.getZ())result.add(be);
            }
        }
        result.sort(Comparator.comparingLong(be->be.getBlockPos().asLong()));
        return result;
    }
    public int receiveFE(int offered,long tick,boolean simulate) {
        return transferFE(offered,tick,simulate,false);
    }
    public int receiveAeFE(int offered,long tick,boolean simulate) {
        if(offered<=0)return 0;
        return Math.toIntExact(PowerDistribution.receive(offered,tick,simulate,fePorts(false,true).stream().map(Fe::transfer).toList()));
    }
    public int extractFE(int requested,long tick,boolean simulate) {
        return transferFE(requested,tick,simulate,true);
    }
    private int transferFE(int amount,long tick,boolean simulate,boolean extraction) {
        if(amount<=0)return 0;
        return Math.toIntExact(PowerDistribution.receive(amount,tick,simulate,fePorts(extraction).stream().map(Fe::transfer).toList()));
    }
    public int storedFE() {
        long total=0;
        for(var port:fePorts(true))total=Math.min(Integer.MAX_VALUE,total+Math.max(0,port.stored().getAsInt()));
        return (int)total;
    }
    private List<Fe> fePorts(boolean extraction) {
        return fePorts(extraction,false);
    }
    private List<Fe> fePorts(boolean extraction,boolean aeOnly) {
        var identities=new ArrayList<Object>();
        var groups=new IdentityHashMap<Object,List<Fe>>();
        var targets=internalRouting?endpointTargets(com.syaru.ae2machines.block.EnergyPortBlock.Mode.FE):blocks().stream().map(be->new Target(be,null)).toList();
        for(var target:targets) {
            var be=target.block();
            if(!internalRouting && NativeWorldBatches.owns(be))continue;
            if(be instanceof com.syaru.ae2machines.block.BaseEnergyPort)continue;
            boolean aeInput=be instanceof appeng.blockentity.networking.EnergyAcceptorBlockEntity
                    || be instanceof appeng.blockentity.networking.ControllerBlockEntity;
            if(aeOnly && !aeInput && !internalRouting)continue;
            if((be instanceof appeng.me.helpers.IGridConnectedBlockEntity || be instanceof appeng.api.parts.IPartHost)
                    && (extraction || !aeInput))continue;
            var port=NativeParallelRuntime.fePort(be,target.side(),extraction);
            if(port==null && !NativeParallelRuntime.owns(be))port=target.side()==null?NativeCellPlatform.fePort(world,be,extraction):NativeCellPlatform.fePort(world,be,extraction,target.side());
            if(port==null)continue;
            Object shared=NativeMachines.mek()?MekStructureStorage.powerIdentity(be):null;
            if(aeInput){
                var grid=((appeng.me.helpers.IGridConnectedBlockEntity)be).getMainNode().getGrid();
                if(grid==null)continue;
                // Native acceptors funnel into one shared AE account; do not count it per face or block.
                shared=grid.getEnergyService();
            }
            // Multiple structure ports/cables expose one account with different side permissions.
            Object identity=shared==null?port.identity():shared;
            if(!groups.containsKey(identity)){identities.add(identity);groups.put(identity,new ArrayList<>());}
            groups.get(identity).add(port);
        }
        return identities.stream().map(id->Fe.faces(id,groups.get(id))).toList();
    }
    public long receiveEU(long voltage,long amps,long tick,boolean simulate) {
        if(!NativeMachines.gt() || voltage<=0 || amps<=0)return 0;
        if(internalRouting)return com.syaru.ae2machines.compat.gt.NativeWorldEu.receiveEndpoints(world,endpointTargets(com.syaru.ae2machines.block.EnergyPortBlock.Mode.EU),voltage,amps,tick,simulate);
        return com.syaru.ae2machines.compat.gt.NativeWorldEu.receive(world,blocks(),voltage,amps,tick,simulate);
    }
    public long voltageEU() {
        if(internalRouting)return NativeMachines.gt()?com.syaru.ae2machines.compat.gt.NativeWorldEu.voltageEndpoints(world,endpointTargets(com.syaru.ae2machines.block.EnergyPortBlock.Mode.EU)):0;
        return NativeMachines.gt()?com.syaru.ae2machines.compat.gt.NativeWorldEu.voltage(world,blocks()):0;
    }
    public long demandEU() {
        if(internalRouting)return NativeMachines.gt()?com.syaru.ae2machines.compat.gt.NativeWorldEu.demandEndpoints(world,endpointTargets(com.syaru.ae2machines.block.EnergyPortBlock.Mode.EU)):0;
        return NativeMachines.gt()?com.syaru.ae2machines.compat.gt.NativeWorldEu.demand(world,blocks()):0;
    }
    public record Target(BlockEntity block,net.minecraft.core.Direction side){}
    private List<Target> endpointTargets(com.syaru.ae2machines.block.EnergyPortBlock.Mode mode){
        var result=new ArrayList<Target>();
        for(var pos:endpoints){
            if(!world.hasChunkAt(pos))continue;var state=world.getBlockState(pos);
            if(!state.is(MachinesMod.ENERGY.get()) || state.getValue(com.syaru.ae2machines.block.EnergyPortBlock.MODE)!=mode)continue;
            var facing=state.getValue(com.syaru.ae2machines.block.EnergyPortBlock.FACING);var target=pos.relative(facing);
            if(!NativeRegionMove.contains(target,min,max) || !world.hasChunkAt(target))continue;
            var be=world.getBlockEntity(target);if(be!=null && !(be instanceof com.syaru.ae2machines.block.BaseEnergyPort))result.add(new Target(be,facing.getOpposite()));
        }
        return result;
    }
}
