package com.syaru.ae2machines.menu;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Display data only. No inventory, recipe, ticking or world-operation interface is exposed. */
public final class FactoryPreviewData {
    public static final int FACTORY=0,STRUCTURE=1,MAX_BLOCKS=262144;
    private FactoryPreviewData(){}
    public record Entry(BlockPos position,BlockState state,CompoundTag visual){
        public Entry{position=position.immutable();visual=visual.copy();}
    }
    public record Snapshot(long request,int mode,String status,List<Entry> blocks,int omitted,int unloaded){
        public Snapshot{blocks=List.copyOf(blocks);}
    }
    public static final class Receiver {
        private long request=-1,scanned,total;
        private int expectedPage,mode,omitted,unloaded;
        private String status="waiting";
        private final Map<BlockPos,Entry> blocks=new LinkedHashMap<>();
        private Snapshot complete;
        public void accept(CompoundTag page){
            if(!page.contains("request"))return;
            long incoming=page.getLong("request");if(incoming<request)return;
            if(incoming>request){request=incoming;expectedPage=0;blocks.clear();complete=null;}
            if(page.getInt("page")<expectedPage)return;
            if(page.getInt("page")!=expectedPage){status="interrupted";return;}
            var list=page.getList("blocks",Tag.TAG_COMPOUND);
            if(list.size()>128 || blocks.size()+list.size()>MAX_BLOCKS){status="invalid";return;}
            expectedPage++;mode=page.getInt("mode");status=page.getString("status");
            scanned=page.getLong("scanned");total=page.getLong("total");omitted=page.getInt("omitted");unloaded=page.getInt("unloaded");
            for(var value:list){var entry=(CompoundTag)value;var pos=BlockPos.of(entry.getLong("pos"));
                blocks.put(pos,new Entry(pos,Block.stateById(entry.getInt("state")),entry.getCompound("visual")));
            }
            if(page.getBoolean("done"))complete=new Snapshot(request,mode,status,new ArrayList<>(blocks.values()),omitted,unloaded);
        }
        public Snapshot complete(){return complete;}
        public long request(){return request;}
        public String status(){return status;}
        public long scanned(){return scanned;}
        public long total(){return total;}
    }
}
