package com.syaru.ae2machines.block;

import com.syaru.ae2machines.platform.MachinesMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

/** Empty-hand bay access does not open or redirect the controller menu. */
public final class CellBayInteraction {
    private CellBayInteraction(){}
    public static boolean use(Player player,BlockPos pos,InteractionHand hand){
        if(hand!=InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty() || player.getOffhandItem().is(MachinesMod.CELL.get()))return false;
        var level=player.level();
        if(!(level.getBlockEntity(pos) instanceof BaseAccelerator host)
                || !(host.getBlockState().getBlock() instanceof ComputationHostBlock block) || block.role()!=ComputationHostBlock.Role.BAY
                || !host.hasFactory() && !host.getBlockState().getValue(ComputationHostBlock.FILLED))return false;
        if(!level.isClientSide && !host.ejectFactory(player))player.displayClientMessage(Component.translatable("ae2machines.eject_unavailable"),true);
        return true;
    }
}
