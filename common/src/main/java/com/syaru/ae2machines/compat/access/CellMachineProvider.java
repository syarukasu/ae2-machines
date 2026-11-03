package com.syaru.ae2machines.compat.access;

import appeng.api.networking.IGrid;
import com.syaru.ae2machines.block.ComputationStructure;
import com.syaru.ae2machines.runtime.NativeWorldSession;
import com.syaru.ae2machines.world.NativeCellLocations;
import com.syaru.meremote.api.VirtualMachineProvider;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Linked by ME Access's service loader only; neither addon becomes mandatory. */
public final class CellMachineProvider implements VirtualMachineProvider {
    @Override public String id(){return "ae2machines:cell";}
    @Override public Stream<Target> targets(ServerPlayer player,IGrid grid){
        return NativeCellLocations.sessions(player.getServer()).stream().filter(s->allowed(player,grid,s))
                .flatMap(s->s.nativeBlocks().stream().filter(be->!be.isRemoved() && s.world().hasChunkAt(be.getBlockPos()))
                    .map(be->target(s,be)));
    }
    @Override public Target resolve(ServerPlayer player,IGrid grid,UUID factory,BlockPos position){
        var session=NativeCellLocations.find(player.getServer(),factory);if(session==null || !allowed(player,grid,session))return null;
        var first=session.previewFirst();var last=session.previewLast();
        if(position.getX()<first.getX() || position.getY()<first.getY() || position.getZ()<first.getZ()
                || position.getX()>last.getX() || position.getY()>last.getY() || position.getZ()>last.getZ()
                || !session.world().hasChunkAt(position))return null;
        var machine=session.world().getBlockEntity(position);return machine==null || machine.isRemoved()?null:target(session,machine);
    }
    @Override public boolean beforeInteraction(ServerPlayer player,IGrid grid,UUID factory,BlockPos position,UUID owner){
        var target=resolve(player,grid,factory,position);if(target==null || !target.owner().equals(owner))return false;
        com.syaru.ae2machines.world.NativeWorldCheckpoint.beforeOwnedWork(player.getServer(),factory);return true;
    }
    private static boolean allowed(ServerPlayer player,IGrid grid,NativeWorldSession session){
        var host=session.host();var level=host.getLevel();
        if(grid==null || player.isSpectator() || !player.mayBuild() || level!=player.serverLevel() || host.isRemoved()
                || !level.hasChunkAt(host.getBlockPos()) || level.getBlockEntity(host.getBlockPos())!=host
                || host.factoryHandle()!=session || !session.id().equals(host.factoryId()) || !host.factoryOnline() || session.mainGrid()!=grid)return false;
        var controller=ComputationStructure.controller(host);
        return controller!=null && !controller.isRemoved() && level.mayInteract(player,controller.getBlockPos())
                && appeng.util.Platform.hasPermissions(new appeng.api.util.DimensionalBlockPos(controller),player)
                && appeng.util.Platform.hasPermissions(new appeng.api.util.DimensionalBlockPos(host),player);
    }
    private static Target target(NativeWorldSession session,BlockEntity machine){
        var host=session.host();var controller=ComputationStructure.controller(host);
        String name=controller.hasCustomName()?controller.getCustomName().getString():controller.getBlockState().getBlock().getName().getString();
        var origin=new Origin(host.getLevel().dimension().location().toString(),controller.getBlockPos(),host.getBlockPos(),
                machine.getBlockPos().subtract(session.previewFirst()),name.substring(0,Math.min(name.length(),128)),"");
        return new Target(session.id(),machine,origin,session.accessOwner());
    }
}
