package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.compat.mek.*;
import com.syaru.ae2machines.platform.MekFluidAccess;
import java.math.BigInteger;
import java.util.Set;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.common.tile.transmitter.TileEntityMechanicalPipe;
import mekanism.common.util.FluidUtils;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="mekanism.common.util.FluidUtils",remap=false)
public abstract class MekWideFluidEmitMixin {
    @Inject(method="emit(Ljava/util/Set;Lmekanism/api/fluid/IExtendedFluidTank;Lnet/minecraft/world/level/block/entity/BlockEntity;I)V",at=@At("HEAD"),cancellable=true)
    private static void machines$emit(Set<Direction> sides,IExtendedFluidTank tank,BlockEntity source,int maximum,CallbackInfo ci){
        if(!(tank instanceof MekWideFluidTank wide) || !wide.machines$wideFluid() || !MekFluidUnits.unit().equals(BigInteger.ONE))return;
        int q=MekWorldParallel.lanes(source);if(q<=1)return;
        var offered=wide.machines$fluidAmount().min(BigInteger.valueOf(maximum).multiply(BigInteger.valueOf(q)));
        var unit=MekFluidUnits.covering(offered);if(unit.equals(BigInteger.ONE))return;
        for(var side:sides){
            var block=source.getLevel().getBlockEntity(source.getBlockPos().relative(side));
            if(block==null)continue;
            if(block instanceof TileEntityMechanicalPipe pipe){if(pipe.getTransmitter().hasTransmitterNetwork())MekFluidAccess.bind(pipe.getTransmitter().getTransmitterNetwork());continue;}
            if(!MekFluidAccess.pooled(block))return;
        }
        MekFluidUnits.call(unit,()->{FluidUtils.emit(sides,tank,source,MekFluidUnits.view(offered));return null;});ci.cancel();
    }
}
