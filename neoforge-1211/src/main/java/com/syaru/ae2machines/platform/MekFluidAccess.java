package com.syaru.ae2machines.platform;

import com.syaru.ae2machines.compat.mek.*;
import com.syaru.ae2machines.world.NativeCellWorlds;
import java.math.BigInteger;
import java.util.*;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.common.content.boiler.BoilerMultiblockData;
import mekanism.common.content.network.FluidNetwork;
import mekanism.common.content.network.transmitter.MechanicalPipe;
import mekanism.common.lib.multiblock.IMultiblock;
import mekanism.common.lib.transmitter.ConnectionType;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;

public final class MekFluidAccess {
    private MekFluidAccess(){}
    public static boolean pooledTargets(Collection<net.neoforged.neoforge.capabilities.BlockCapabilityCache<net.neoforged.neoforge.fluids.capability.IFluidHandler,net.minecraft.core.Direction>> targets){
        for(var target:targets){
            var block=target.level().getBlockEntity(target.pos());
            if(block instanceof mekanism.common.tile.transmitter.TileEntityMechanicalPipe pipe){
                if(!pipe.getTransmitter().hasTransmitterNetwork() || !owned(pipe.getTransmitter().getTransmitterNetwork()))return false;
                bind(pipe.getTransmitter().getTransmitterNetwork());
            }else if(!pooled(block))return false;
        }
        return true;
    }
    public static long steamNeeded(Object boiler){return ((BoilerMultiblockData)boiler).steamTank.getNeeded();}
    public static boolean owned(FluidNetwork network){return network.getWorld()!=null && !network.getWorld().isClientSide && NativeCellWorlds.owns(network.getWorld());}
    public static void bind(FluidNetwork network){if(owned(network))((MekWideFluidTank)network.fluidTank).machines$fluidCapacity(()->BigInteger.valueOf(network.getCapacity()));}
    public static boolean pooled(BlockEntity block){
        if(block==null || !NativeCellWorlds.owns(block.getLevel()))return false;
        List<IExtendedFluidTank> tanks=block instanceof IMultiblock<?> multi?multi.getMultiblock().getFluidTanks(null):
                block instanceof TileEntityMekanism machine?machine.getFluidTanks(null):List.of();
        return !tanks.isEmpty() && tanks.stream().allMatch(t->t instanceof MekWideFluidTank w && w.machines$wideFluid());
    }
    public static BigInteger pullUnit(MechanicalPipe pipe){
        int q=MekTransportParallel.lanes(pipe);if(q<=1)return BigInteger.ONE;
        for(var side:net.minecraft.core.Direction.values()){
            if(!pipe.isConnectionType(side,ConnectionType.PULL))continue;
            var block=pipe.getTransmitterTile().getLevel().getBlockEntity(pipe.getTransmitterTile().getBlockPos().relative(side));
            if(pooled(block))continue;
            // This native source's infinite mode has no consumable state to multiply.
            var capability=pipe.getAcceptorCache().getConnectedAcceptor(side);
            if(block==null || !block.getClass().getName().equals("net.blay09.mods.cookingforblockheads.tile.SinkBlockEntity")
                    || capability==null || capability.getTanks()!=1 || capability.getFluidInTank(0).getAmount()!=Integer.MAX_VALUE)return BigInteger.ONE;
        }
        if(pipe.hasTransmitterNetwork())bind(pipe.getTransmitterNetwork());
        return MekFluidUnits.covering(BigInteger.valueOf(pipe.tier.getPipePullAmount()).multiply(BigInteger.valueOf(q)));
    }
    public static void set(IExtendedFluidTank tank,FluidStack type,BigInteger amount){
        if(amount.signum()<0)throw new IllegalArgumentException("Negative fluid amount");
        if(!(tank instanceof MekWideFluidTank)){
            tank.setStackUnchecked(amount.signum()==0?FluidStack.EMPTY:type.copyWithAmount(amount.intValueExact()));return;
        }
        MekFluidUnits.call(BigInteger.ONE,()->{
            tank.setStackUnchecked(amount.signum()==0?FluidStack.EMPTY:type.copyWithAmount(1));
            ((MekWideFluidTank)tank).machines$fluidAmount(amount);return null;
        });
    }
    public static void saveShares(FluidNetwork network){
        if(!owned(network))return;
        var amount=MekWideFluidTank.amount(network.fluidTank);var type=network.fluidTank.getFluid();
        var pipes=new ArrayList<>(network.getTransmitters());pipes.sort(Comparator.comparingLong(p->p.getTransmitterTile().getBlockPos().asLong()));
        for(int i=0;i<pipes.size();i++){
            var pipe=pipes.get(i);var share=i==pipes.size()-1?amount:amount.min(BigInteger.valueOf(pipe.getCapacity()));
            var access=(MekWideFluidPipe)pipe;
            boolean changed=!access.machines$fluidShare().equals(share) || share.signum()>0 && !FluidStack.isSameFluidSameComponents(pipe.saveShare,type);
            pipe.saveShare=share.signum()==0?FluidStack.EMPTY:type.copyWithAmount(share.min(MekFluidUnits.INT_MAX).intValueExact());
            access.machines$fluidShare(share);amount=amount.subtract(share);
            if(changed)pipe.getTransmitterTile().setChanged();
        }
        if(amount.signum()!=0)throw new IllegalStateException("Exact fluid network has no save owner");
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    public static void mergeCaches(List<IExtendedFluidTank> into,List<IExtendedFluidTank> from,List<?> rejects){
        if(java.util.stream.Stream.concat(into.stream(),from.stream()).noneMatch(t->MekWideFluidTank.amount(t).compareTo(MekFluidUnits.INT_MAX)>0)){
            mekanism.common.util.StorageUtils.mergeFluidTanks(into,from,(List)rejects);return;
        }
        var types=into.stream().map(t->t.getFluid().copy()).toArray(FluidStack[]::new);
        var amounts=into.stream().map(MekWideFluidTank::amount).toArray(BigInteger[]::new);
        for(var source:from){
            if(source.isEmpty())continue;int target=-1;
            for(int i=0;i<types.length;i++)if(!types[i].isEmpty() && FluidStack.isSameFluidSameComponents(types[i],source.getFluid())){target=i;break;}
            if(target<0)for(int i=0;i<types.length;i++)if(types[i].isEmpty()){target=i;types[i]=source.getFluid().copy();break;}
            if(target<0)throw new IllegalStateException("Exact cache merge has no compatible fluid slot; inventories retained");
            amounts[target]=amounts[target].add(MekWideFluidTank.amount(source));
        }
        for(int i=0;i<into.size();i++)set(into.get(i),types[i],amounts[i]);
    }
}
