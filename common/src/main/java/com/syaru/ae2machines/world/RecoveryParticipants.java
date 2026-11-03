package com.syaru.ae2machines.world;

import appeng.api.networking.crafting.*;
import appeng.blockentity.crafting.CraftingBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.*;

/** Explicit persistence contracts, not permission for every subclass or external cell backend. */
public final class RecoveryParticipants {
    private RecoveryParticipants() {}
    public static final String ADVANCED_CPU="net.pedroksl.advanced_ae.common.entities.AdvCraftingBlockEntity";
    public static final String REQUESTER="com.almostreliable.merequester.requester.RequesterBlockEntity";
    public static boolean addonBlock(BlockEntity block) {
        return block.getClass().getName().equals(ADVANCED_CPU) || block.getClass().getName().equals(REQUESTER);
    }
    public static boolean itemBackedCell(ItemStack stack) {
        return stack.isEmpty() || Set.of("appeng.items.storage.BasicStorageCell",
                "gripe._90.megacells.item.cell.BulkCellItem","me.ramidzkh.mekae2.item.ChemicalStorageCell").contains(stack.getItem().getClass().getName());
    }
    /** Inspect the backend already owned by the Drive; do not construct a second cache. */
    public static boolean itemBackedCell(appeng.blockentity.storage.DriveBlockEntity drive,int slot){
        if(itemBackedCell(drive.getInternalInventory().getStackInSlot(slot)))return true;
        var backend=drive.getOriginalCellInventory(slot);
        return backend!=null && backend.getClass()==appeng.me.cells.BasicCellInventory.class;
    }
    public static boolean linksCovered(Set<BlockEntity> participants) {
        var requesters=new HashSet<UUID>();var cpus=new ArrayList<ICraftingLink>();
        for(var block:participants) {
            if(block.getClass().getName().equals(REQUESTER))
                for(var link:((ICraftingRequester)block).getRequestedJobs())if(!link.isCanceled() && !link.isDone())requesters.add(link.getCraftingID());
            if(block instanceof CraftingBlockEntity cpu && cpu.getCluster()!=null)cpus.add(cpu.getCluster().craftingLogic.getLastLink());
            if(block.getClass().getName().equals(ADVANCED_CPU))
                cpus.addAll(com.syaru.ae2machines.compat.advanced.AdvancedCpuRecovery.links(block));
        }
        for(var link:cpus)if(link!=null && !link.isStandalone() && !link.isCanceled() && !link.isDone()
                && !requesters.contains(link.getCraftingID()))return false;
        return true;
    }
}
