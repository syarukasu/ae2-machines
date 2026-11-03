package com.syaru.ae2machines.mixin;

import appeng.api.implementations.items.ISpatialStorageCell;
import appeng.blockentity.spatial.SpatialIOPortBlockEntity;
import com.syaru.ae2machines.platform.MachinesMod;
import com.syaru.ae2machines.world.VirtualSpatialCellTransition;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(value=SpatialIOPortBlockEntity.class,remap=false)
public abstract class VirtualSpatialIoMixin {
    @Redirect(method="lambda$transition$1",at=@At(value="INVOKE",target="Lappeng/api/implementations/items/ISpatialStorageCell;doSpatialTransition(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;I)Z"))
    private boolean virtualCell(ISpatialStorageCell item,ItemStack stack,ServerLevel level,BlockPos min,BlockPos max,int playerId){
        if(stack.is(MachinesMod.CELL.get()))return VirtualSpatialCellTransition.move((SpatialIOPortBlockEntity)(Object)this,stack,level,min,max,playerId);
        return item.doSpatialTransition(stack,level,min,max,playerId);
    }
}
