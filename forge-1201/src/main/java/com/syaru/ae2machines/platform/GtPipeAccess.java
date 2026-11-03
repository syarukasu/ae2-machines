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
import net.minecraftforge.items.IItemHandler;
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
        tag.putInt("count",pipe.getTransferredItems());

        tag.putLong("timer",(long)field(pipe,"timer",null,false));
        var transfers=new ListTag();
        pipe.getTransferred().forEach((pos,count)->{var entry=new CompoundTag();entry.putLong("pos",pos.getPos().asLong());entry.putString("side",pos.getFacing().getName());entry.putInt("count",count);transfers.add(entry);});
        tag.put("roundRobin",transfers);return tag;
    }
    public static void restore(ItemPipeBlockEntity pipe,CompoundTag tag,List<ItemRoutePath> paths) {
        if(tag.isEmpty())return;
        pipe.getTransferred().clear();
        require(tag.getInt("count")>=0,"INVALID_PIPE_COUNTER");field(pipe,"transferredItems",tag.getInt("count"),true);

        for(var value:tag.getList("roundRobin",Tag.TAG_COMPOUND)) {
            var entry=(CompoundTag)value;var side=Direction.byName(entry.getString("side"));require(side!=null && entry.getInt("count")>=0,"INVALID_PIPE_ROUND_ROBIN");
            pipe.getTransferred().put(new FacingPos(BlockPos.of(entry.getLong("pos")),side),entry.getInt("count"));
        }
        field(pipe,"timer",Math.min(tag.getLong("timer"),pipe.getLevelTime()),true);
    }
    private static Object field(ItemPipeBlockEntity pipe,String name,Object value,boolean write) {
        try {var f=ItemPipeBlockEntity.class.getDeclaredField(name);f.setAccessible(true);if(write)f.set(pipe,value);return f.get(pipe);}
        catch(ReflectiveOperationException e){throw new IllegalStateException("GT pipe persistence contract changed: "+name,e);}
    }
}

