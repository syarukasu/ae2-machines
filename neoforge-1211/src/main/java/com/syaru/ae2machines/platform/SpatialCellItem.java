package com.syaru.ae2machines.platform;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import com.syaru.ae2machines.world.FactoryCellInteraction;

public final class SpatialCellItem extends Item implements appeng.api.implementations.items.ISpatialStorageCell {
    public SpatialCellItem() { super(new Properties().stacksTo(1)); }
    @Override public boolean isSpatialStorage(net.minecraft.world.item.ItemStack stack){return true;}
    @Override public int getMaxStoredDim(net.minecraft.world.item.ItemStack stack){return 128;}
    @Override public int getAllocatedPlotId(net.minecraft.world.item.ItemStack stack){return -1;}
    @Override public boolean doSpatialTransition(net.minecraft.world.item.ItemStack stack,net.minecraft.server.level.ServerLevel level,net.minecraft.core.BlockPos min,net.minecraft.core.BlockPos max,int playerId){return false;}
    @Override public void appendHoverText(net.minecraft.world.item.ItemStack stack,Item.TooltipContext context,java.util.List<net.minecraft.network.chat.Component> lines,net.minecraft.world.item.TooltipFlag flag) {
        var data=PlatformData.cellData(stack);
        lines.add(net.minecraft.network.chat.Component.translatable(data.getBoolean("restorationRemainder") ? "tooltip.ae2machines.virtual_spatial_cell.remainder" : data.hasUUID("factory") ? "tooltip.ae2machines.virtual_spatial_cell.filled" : "tooltip.ae2machines.virtual_spatial_cell.empty"));
        com.syaru.ae2machines.world.VirtualCellMetadata.append(data,lines);
        if(data.hasUUID("factory")) lines.add(net.minecraft.network.chat.Component.literal(data.getUUID("factory").toString()).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }
    @Override public void inventoryTick(net.minecraft.world.item.ItemStack stack,net.minecraft.world.level.Level level,net.minecraft.world.entity.Entity entity,int slot,boolean selected){
        super.inventoryTick(stack,level,entity,slot,selected);
        if(level instanceof net.minecraft.server.level.ServerLevel server && (server.getGameTime()+slot)%20==0)com.syaru.ae2machines.world.VirtualCellMetadata.refreshLegacy(stack,server);
    }
    @Override public InteractionResult useOn(UseOnContext context) { return FactoryCellInteraction.use(context); }
}
