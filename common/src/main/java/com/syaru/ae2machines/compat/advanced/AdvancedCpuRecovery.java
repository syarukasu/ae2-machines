package com.syaru.ae2machines.compat.advanced;

import appeng.api.networking.crafting.ICraftingLink;
import com.syaru.ae2machines.platform.PlatformData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.pedroksl.advanced_ae.common.cluster.*;
import net.pedroksl.advanced_ae.common.entities.AdvCraftingBlockEntity;
import java.util.*;

/** Loaded only for AdvancedAE. Its own block serializer includes every virtual CPU job. */
public final class AdvancedCpuRecovery {
    private static final java.lang.reflect.Field CLUSTER;
    static {
        try {CLUSTER=AdvCraftingCPU.class.getDeclaredField("cluster");CLUSTER.setAccessible(true);}
        catch(ReflectiveOperationException e){throw new ExceptionInInitializerError(e);}
    }
    private AdvancedCpuRecovery() {}
    public static Map<Long,CompoundTag> before(AdvCraftingCPU cpu) {
        try {
            var cluster=(AdvCraftingCPUCluster)CLUSTER.get(cpu);var blocks=cluster.getBlockEntities();
            var result=new HashMap<Long,CompoundTag>();
            blocks.forEachRemaining(block->result.put(block.getBlockPos().asLong(),PlatformData.saveBlock(block)));
            return result;
        }catch(IllegalAccessException e){throw new IllegalStateException(e);}
    }
    public static List<ICraftingLink> links(BlockEntity block) {
        var cluster=((AdvCraftingBlockEntity)block).getCluster();if(cluster==null)return List.of();
        var links=new ArrayList<ICraftingLink>();
        for(var cpu:cluster.getActiveCPUs()){var link=cpu.craftingLogic.getLastLink();if(link!=null)links.add(link);}
        return links;
    }
}
