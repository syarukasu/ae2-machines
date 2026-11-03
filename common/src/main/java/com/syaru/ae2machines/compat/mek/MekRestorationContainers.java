package com.syaru.ae2machines.compat.mek;

import com.syaru.ae2machines.mixin.MekHeatAccess;
import com.syaru.ae2machines.platform.PlatformData;
import java.math.*;
import java.util.List;
import mekanism.api.heat.IHeatCapacitor;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.lib.multiblock.MultiblockData;
import net.minecraft.world.level.block.entity.BlockEntity;
import static com.syaru.ae2machines.compat.mek.MekRestorationStorage.*;

public final class MekRestorationContainers {
    private MekRestorationContainers(){}
    public static void slots(Ports ports,BlockEntity owner,List<IInventorySlot> slots){
        for(int i=0;i<slots.size();i++){
            var slot=slots.get(i);
            ports.amount(owner,"item",i,slot,()->BigInteger.valueOf(slot.getCount()),
                    payload->{var stack=PlatformData.loadItem(payload,owner.getLevel());return BigInteger.valueOf(slot instanceof MekVirtualSlot virtual?virtual.machines$nativeLimit(stack):slot.getLimit(stack));},
                    ()->{var stack=slot.getStack().copy();stack.setCount(1);return PlatformData.saveItem(stack,owner.getLevel());},
                    (payload,amount)->{if(amount.signum()==0)slot.setEmpty();else{var stack=PlatformData.loadItem(payload,owner.getLevel());stack.setCount(amount.intValueExact());slot.setStack(stack);}});
        }
    }
    public static void heat(Ports ports,BlockEntity owner,List<IHeatCapacitor> capacitors){
        for(int i=0;i<capacitors.size();i++){
            var heat=capacitors.get(i);
            int q=heat instanceof MekStructureHeat virtual && virtual.machines$scaledHeat()?MekWorldParallel.lanes(owner):1;
            ports.heat(owner,"heat",i,heat,()->decimal(heat.getHeat()),
                    ()->heat instanceof MekHeatAccess access?decimal(access.ae2machines$pendingHeat()):BigDecimal.ZERO,
                    ()->decimal(heat.getHeatCapacity()),q,
                    (stored,pending)->{heat.setHeat(stored.doubleValue());if(heat instanceof MekHeatAccess access)access.ae2machines$pendingHeat(pending.doubleValue());else if(pending.signum()!=0)throw new IllegalArgumentException("NATIVE_PENDING_HEAT_UNSUPPORTED");});
        }
    }
    public static void plasma(Ports ports,BlockEntity owner,MultiblockData data){
        if(!(data instanceof MekFusionState fusion))return;
        ports.heat(owner,"plasma",0,data,
                ()->decimal(fusion.machines$plasmaTemperature()).multiply(BigDecimal.valueOf(100L*MekStructureParallel.lanes(data))),
                ()->BigDecimal.ZERO,()->BigDecimal.valueOf(100L*MekStructureParallel.lanes(data)),MekStructureParallel.lanes(data),
                (heat,pending)->{if(pending.signum()!=0)throw new IllegalArgumentException("INVALID_PLASMA_PENDING_HEAT");fusion.machines$restorePlasma(heat.divide(BigDecimal.valueOf(100)).doubleValue(),1);});
    }
}
