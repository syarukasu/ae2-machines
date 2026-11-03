package com.syaru.ae2machines.platform;

import appeng.api.stacks.*;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.SimpleTieredMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.syaru.ae2machines.runtime.*;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import java.util.*;

/** Native GT material handlers only: the circuit and battery are not transferable recipe stock. */
public final class GtMaterialAccess {
    private GtMaterialAccess() {}
    public static BufferedMachine create(BlockEntity be,Runnable changed) {
        var m=GregTechAdapter.machine(be);var ports=new ArrayList<MaterialBufferedMachine.Port>();
        for(var handler:List.of(m.importItems,m.exportItems))
            for(int i=0;i<handler.getSlots();i++)ports.add(item(m,handler,i,handler==m.importItems));
        for(var handler:List.of(m.importFluids,m.exportFluids))
            for(int i=0;i<handler.getTanks();i++)ports.add(fluid(m,handler,i,handler==m.importFluids));
        return new MaterialBufferedMachine(ports,changed,be.getBlockState().getBlock().getName(),
                inputs->GregTechAdapter.discover(be,inputs).map(MachineProfile::recipe));
    }
    private static Direction output(SimpleTieredMachine m,boolean item) {
        return item?m.getOutputFacingItems():m.getOutputFacingFluids();
    }
    private static boolean inputFromOutput(SimpleTieredMachine m,boolean item) {
        return item?m.isAllowInputFromOutputSideItems():m.isAllowInputFromOutputSideFluids();
    }
    private static boolean autoOutput(SimpleTieredMachine m,boolean item) {
        return item?m.isAutoOutputItems():m.isAutoOutputFluids();
    }
    private static FluidStack stack(AEFluidKey key,int amount) {
        return new FluidStack(key.getFluid(),amount,key.getTag());
    }
    private static MaterialBufferedMachine.Port item(SimpleTieredMachine m,NotifiableItemStackHandler handler,int slot,boolean input) {
        return new MaterialBufferedMachine.Port() {
            public boolean input(){return input;}
            public boolean visible(Direction side){return side!=null && (!input || side!=output(m,true) || inputFromOutput(m,true));}
            public boolean allows(AEKey key,Direction side,boolean insert) {
                if(!(key instanceof AEItemKey item) || !visible(side))return false;
                var filter=m.getItemCapFilter(side,insert?IO.IN:IO.OUT);return filter==null || filter.test(item.toStack());
            }
            public boolean ejecting(){return !input && autoOutput(m,true);}
            public boolean ejecting(Direction side){return ejecting() && side==output(m,true);}
            public AEKey key(){return AEItemKey.of(handler.getStackInSlot(slot));}
            public long amount(){return handler.getStackInSlot(slot).getCount();}
            public long insert(AEKey key,long n,boolean simulate,boolean internal) {
                if(!(key instanceof AEItemKey item))return 0;
                int offer=(int)Math.min(Integer.MAX_VALUE,n);var stack=item.toStack(offer);
                var left=internal?handler.insertItemInternal(slot,stack,simulate):handler.insertItem(slot,stack,simulate);
                return offer-left.getCount();
            }
            public long extract(AEKey key,long n,boolean simulate,boolean internal) {
                if(!(key instanceof AEItemKey item) || !item.matches(handler.getStackInSlot(slot)))return 0;
                int offer=(int)Math.min(Integer.MAX_VALUE,n);
                return (internal?handler.extractItemInternal(slot,offer,simulate):handler.extractItem(slot,offer,simulate)).getCount();
            }
        };
    }
    private static MaterialBufferedMachine.Port fluid(SimpleTieredMachine m,NotifiableFluidTank handler,int slot,boolean input) {
        return new MaterialBufferedMachine.Port() {
            public boolean input(){return input;}
            public boolean visible(Direction side){return side!=null && (!input || side!=output(m,false) || inputFromOutput(m,false));}
            public boolean allows(AEKey key,Direction side,boolean insert) {
                if(!(key instanceof AEFluidKey fluid) || !visible(side))return false;
                var filter=m.getFluidCapFilter(side,insert?IO.IN:IO.OUT);return filter==null || filter.test(stack(fluid,1));
            }
            public boolean ejecting(){return !input && autoOutput(m,false);}
            public boolean ejecting(Direction side){return ejecting() && side==output(m,false);}
            public AEKey key(){var stack=handler.getFluidInTank(slot);return stack.isEmpty()?null:AEFluidKey.of(stack);}
            public long amount(){return handler.getFluidInTank(slot).getAmount();}
            public long insert(AEKey key,long n,boolean simulate,boolean internal) {
                if(!(key instanceof AEFluidKey fluid) || !internal && !handler.canCapInput())return 0;
                var offered=stack(fluid,(int)Math.min(Integer.MAX_VALUE,n));
                if(!handler.isFluidValid(slot,offered))return 0;
                if(!handler.isAllowSameFluids())for(int i=0;i<handler.getTanks();i++)
                    if(i!=slot && !handler.getFluidInTank(i).isEmpty() && fluid.equals(AEFluidKey.of(handler.getFluidInTank(i))))return 0;
                return handler.getStorages()[slot].fill(offered,simulate?FluidAction.SIMULATE:FluidAction.EXECUTE);
            }
            public long extract(AEKey key,long n,boolean simulate,boolean internal) {
                if(!key.equals(key()) || !internal && !handler.canCapOutput())return 0;
                return handler.getStorages()[slot].drain((int)Math.min(Integer.MAX_VALUE,n),simulate?FluidAction.SIMULATE:FluidAction.EXECUTE).getAmount();
            }
        };
    }
}
