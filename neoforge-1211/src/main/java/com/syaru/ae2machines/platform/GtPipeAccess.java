package com.syaru.ae2machines.platform;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import com.gregtechceu.gtceu.common.blockentity.ItemPipeBlockEntity;
import com.gregtechceu.gtceu.common.pipelike.item.*;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.ItemPipeProperties;
import com.gregtechceu.gtceu.utils.FacingPos;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;
import java.util.*;
import static com.syaru.ae2machines.world.NativeFactoryCompiler.require;

public final class GtPipeAccess {
    private static final ThreadLocal<IActionSource> SOURCE=new ThreadLocal<>();
    private GtPipeAccess() {}
    public static ItemRoutePath route(ItemPipeBlockEntity pipe,Direction side,int distance,ItemPipeProperties props,boolean restrictive,MEStorage target) {
        return new ItemRoutePath(pipe,side,distance,props,restrictive,List.of()) {
            @Override public IItemHandler getHandler(Level level){return new IItemHandler() {
                public int getSlots(){return 1;}
                public ItemStack getStackInSlot(int slot){return ItemStack.EMPTY;}
                public ItemStack extractItem(int slot,int amount,boolean simulate){return ItemStack.EMPTY;}
                public int getSlotLimit(int slot){return Integer.MAX_VALUE;}
                public boolean isItemValid(int slot,ItemStack stack){return true;}
                public ItemStack insertItem(int slot,ItemStack stack,boolean simulate) {
                    var source=Objects.requireNonNull(SOURCE.get(),"Missing pipe action source");
                    long n=target.insert(AEItemKey.of(stack),stack.getCount(),simulate?Actionable.SIMULATE:Actionable.MODULATE,source);
                    require(n>=0 && n<=stack.getCount(),"INVALID_PIPE_TARGET_RESULT");
                    var left=stack.copy();left.shrink((int)n);return left;
                }
            };}
        };
    }
    public static MEStorage storage(ItemNetHandler handler,Runnable changed) {
        return new MEStorage() {
            @Override public long insert(AEKey key,long amount,Actionable mode,IActionSource source) {
                if(!(key instanceof AEItemKey item) || amount<=0)return 0;
                int offered=(int)Math.min(Integer.MAX_VALUE,amount);
                var previous=SOURCE.get();SOURCE.set(source);
                try {
                    var remainder=handler.insertItem(0,item.toStack(offered),mode==Actionable.SIMULATE);
                    int accepted=offered-remainder.getCount();require(accepted>=0 && accepted<=offered,"INVALID_NATIVE_PIPE_RESULT");
                    if(mode==Actionable.MODULATE && accepted>0)changed.run();
                    return accepted;
                } finally {if(previous==null)SOURCE.remove();else SOURCE.set(previous);}
            }
            @Override public net.minecraft.network.chat.Component getDescription(){return net.minecraft.network.chat.Component.literal("GT Item Pipe");}
        };
    }
    public static CompoundTag save(ItemPipeBlockEntity pipe,List<ItemRoutePath> paths) {
        var tag=new CompoundTag();

        var transfers=new ListTag();
        pipe.getTransferredItems().forEach((path,count)->{var entry=pathTag(path);entry.putInt("count",count);entry.putBoolean("active",paths.contains(path));transfers.add(entry);});
        tag.put("transfers",transfers);

        tag.putLong("timer",(long)field(pipe,"timer",null,false));
        var globalTransfers=new ListTag();
        pipe.getTransferredGlobalRoundRobin().forEach((pos,count)->{var entry=new CompoundTag();entry.putLong("pos",pos.getPos().asLong());entry.putString("side",pos.getFacing().getName());entry.putInt("count",count);globalTransfers.add(entry);});
        tag.put("roundRobin",globalTransfers);return tag;
    }
    public static void restore(ItemPipeBlockEntity pipe,CompoundTag tag,List<ItemRoutePath> paths) {
        if(tag.isEmpty())return;
        pipe.getTransferredItems().clear();pipe.getTransferredGlobalRoundRobin().clear();

        for(var value:tag.getList("transfers",Tag.TAG_COMPOUND)) {
            var entry=(CompoundTag)value;var identity=entry.copy();identity.remove("count");identity.remove("active");
            var counters=pipe.getTransferredItems();
            var path=entry.getBoolean("active")?paths.stream().filter(p->pathTag(p).equals(identity) && !counters.containsKey(p)).findFirst().orElse(null):null;
            if(path==null) {
                // GT retains old path identities after route-cache invalidation. Their allowance still
                // contributes to the total and decays separately, even when their endpoints are equal.
                var side=Direction.byName(identity.getString("side"));require(side!=null,"INVALID_PIPE_COUNTER_SIDE");
                var pos=BlockPos.of(identity.getLong("pos"));
                path=new ItemRoutePath(pipe,side,identity.getInt("distance"),new ItemPipeProperties(identity.getInt("priority"),identity.getFloat("rate")),identity.getBoolean("restrictive"),List.of()) {
                    @Override public BlockPos getTargetPipePos(){return pos;}
                };
            }
            // GT 8 subtracts the route allowance on each window; negative credit is native state.
            require(entry.contains("count",Tag.TAG_INT),"INVALID_PIPE_COUNTER");counters.put(path,entry.getInt("count"));
        }

        for(var value:tag.getList("roundRobin",Tag.TAG_COMPOUND)) {
            var entry=(CompoundTag)value;var side=Direction.byName(entry.getString("side"));require(side!=null && entry.getInt("count")>=0,"INVALID_PIPE_ROUND_ROBIN");
            pipe.getTransferredGlobalRoundRobin().put(new FacingPos(BlockPos.of(entry.getLong("pos")),side),entry.getInt("count"));
        }
        field(pipe,"timer",Math.min(tag.getLong("timer"),pipe.getLevelTime()),true);
    }
    private static CompoundTag pathTag(ItemRoutePath path) {
        var tag=new CompoundTag();tag.putLong("pos",path.getTargetPipePos().asLong());tag.putString("side",path.getTargetFacing().getName());
        tag.putInt("distance",path.getDistance());tag.putInt("priority",path.getProperties().getPriority());tag.putFloat("rate",path.getProperties().getTransferRate());tag.putBoolean("restrictive",path.isRestrictive());return tag;
    }
    private static Object field(ItemPipeBlockEntity pipe,String name,Object value,boolean write) {
        try {var f=ItemPipeBlockEntity.class.getDeclaredField(name);f.setAccessible(true);if(write)f.set(pipe,value);return f.get(pipe);}
        catch(ReflectiveOperationException e){throw new IllegalStateException("GT pipe persistence contract changed: "+name,e);}
    }
}

