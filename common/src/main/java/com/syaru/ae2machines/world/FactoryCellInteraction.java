package com.syaru.ae2machines.world;

import com.syaru.ae2machines.block.BaseAccelerator;
import com.syaru.ae2machines.platform.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;

public final class FactoryCellInteraction {
    private FactoryCellInteraction() {}
    public static InteractionResult use(UseOnContext context) {
        var player=context.getPlayer();if(player==null)return InteractionResult.PASS;
        if(context.getLevel().isClientSide)return InteractionResult.SUCCESS;
        var level=(ServerLevel)context.getLevel();var pos=context.getClickedPos();var stack=context.getItemInHand();
        var data=PlatformData.cellData(stack);
        try {
            if(data.hasUUID("factory")) {
                var journal=FactoryJournal.open(level,data.getUUID("factory"));var saved=journal.read();
                if(saved.getCompound("blueprint").getInt("schema")==4 && saved.getString("state").equals("RESTORED")) {
                    if(RestorationRemainder.has(saved) && level.getBlockState(pos).is(MachinesMod.MARKER.get()) && player.isShiftKeyDown()){
                        RestorationRemainder.recover(level,player,journal);saved=journal.read();
                    }
                    boolean remaining=RestorationRemainder.has(saved);RestorationRemainder.cellData(data,saved);PlatformData.setCellData(stack,data);
                    message(player,remaining?"ae2machines.cell_remainder":"ae2machines.restored");return InteractionResult.SUCCESS;
                }
                if(level.getBlockEntity(pos) instanceof BaseAccelerator accelerator) {
                    var target=com.syaru.ae2machines.block.ComputationStructure.menuHost(accelerator,true);
                    if(target==null)throw new IllegalArgumentException("COMPUTATION_STRUCTURE_INCOMPLETE_OR_FULL");
                    target.insertFactory(data.getUUID("factory"),player);stack.shrink(1);return InteractionResult.SUCCESS;
                }
                if(level.getBlockState(pos).is(MachinesMod.MARKER.get()) && player.isShiftKeyDown()) {
                    FactoryWorld.restore(level,player,data.getUUID("factory"));saved=FactoryJournal.open(level,data.getUUID("factory")).read();
                    boolean remaining=RestorationRemainder.has(saved);RestorationRemainder.cellData(data,saved);
                    PlatformData.setCellData(stack,data);message(player,remaining?"ae2machines.restored_remainder":"ae2machines.restored");return InteractionResult.SUCCESS;
                }
                message(player,"ae2machines.cell_ready");return InteractionResult.SUCCESS;
            }
            if(!level.getBlockState(pos).is(MachinesMod.MARKER.get()))return InteractionResult.PASS;
            if(!appeng.util.Platform.hasPermissions(new appeng.api.util.DimensionalBlockPos(level.getBlockEntity(pos)),player))
                throw new IllegalArgumentException("AE2_PERMISSION_DENIED");
            var region=VirtualSpatialRegion.read(level,pos);var first=region.first();var last=region.last();
            if(player.isShiftKeyDown()) {
                FactoryWorld.capture(level,player,first,last,id->{data.remove("corner");data.putUUID("factory",id);VirtualCellMetadata.describe(data,first,last);PlatformData.setCellData(stack,data);});
                message(player,"ae2machines.captured");
            } else {
                NativeWorldFactory.inspect(level,player,first,last);
                player.displayClientMessage(Component.translatable("ae2machines.region",first.toShortString(),last.toShortString()),false);
                message(player,"ae2machines.capture_ready");
            }
            return InteractionResult.SUCCESS;
        } catch(RuntimeException e) {
            player.displayClientMessage(Component.literal(e.getMessage()==null ? "FACTORY_OPERATION_FAILED":e.getMessage()),false);
            if(!(e instanceof IllegalArgumentException)) org.slf4j.LoggerFactory.getLogger(FactoryCellInteraction.class).error("Factory operation failed",e);
        }
        return InteractionResult.FAIL;
    }
    private static void message(net.minecraft.world.entity.player.Player player,String key) {
        player.displayClientMessage(Component.translatable(key),false);
    }
}
