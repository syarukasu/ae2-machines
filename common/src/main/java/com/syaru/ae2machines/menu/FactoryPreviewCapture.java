package com.syaru.ae2machines.menu;

import com.syaru.ae2machines.block.*;
import com.syaru.ae2machines.platform.PlatformData;
import com.syaru.ae2machines.runtime.NativeWorldSession;
import com.syaru.ae2machines.world.FactoryJournal;
import java.io.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;

/** Bounded menu-owned scan; never loads a cell, forces a chunk or executes a block entity. */
public final class FactoryPreviewCapture {
    private final long request,total;
    private final int mode;
    private final ServerLevel world;
    private final Iterator<BlockPos> positions;
    private final Iterator<CompoundTag> stored;
    private final String status;
    private long scanned;
    private int page,omitted,unloaded;
    private CompoundTag pending;
    private boolean finished;
    private FactoryPreviewCapture(long request,int mode,ServerLevel world,Iterator<BlockPos> positions,Iterator<CompoundTag> stored,long total,String status){
        this.request=request;this.mode=mode;this.world=world;this.positions=positions;this.stored=stored;this.total=total;this.status=status;
    }
    public static FactoryPreviewCapture create(long request,int mode,BaseAccelerator controller,BaseAccelerator bay){
        var level=(ServerLevel)controller.getLevel();
        if(mode==FactoryPreviewData.STRUCTURE){var positions=ComputationStructure.previewPositions(controller);
            return new FactoryPreviewCapture(request,mode,level,positions.iterator(),null,positions.size(),"structure");
        }
        if(bay==null || !bay.hasFactory())return empty(request,mode,"no_cell");
        if(bay.factoryHandle() instanceof NativeWorldSession session){
            var first=session.previewFirst();var last=session.previewLast();
            long volume=Math.multiplyExact(Math.multiplyExact((long)last.getX()-first.getX()+1,(long)last.getY()-first.getY()+1),(long)last.getZ()-first.getZ()+1);
            if(volume<0 || volume>FactoryPreviewData.MAX_BLOCKS)return empty(request,mode,"unavailable");
            return new FactoryPreviewCapture(request,mode,session.world(),BlockPos.betweenClosed(first,last).iterator(),null,volume,"live");
        }
        // Detached/legacy cells use their saved layout; do not open or resume them to draw a GUI.
        var blueprint=FactoryJournal.open(level,bay.factoryId()).read().getCompound("blueprint");
        var list=blueprint.getList("blocks",Tag.TAG_COMPOUND);
        var entries=new ArrayList<CompoundTag>();for(var tag:list)entries.add(((CompoundTag)tag).copy());
        if(entries.size()>FactoryPreviewData.MAX_BLOCKS)return empty(request,mode,"unavailable");
        return new FactoryPreviewCapture(request,mode,level,null,entries.iterator(),entries.size(),"saved_layout");
    }
    public static FactoryPreviewCapture empty(long request,int mode,String status){return new FactoryPreviewCapture(request,mode,null,null,Collections.emptyIterator(),0,status);}
    public boolean finished(){return finished;}
    public CompoundTag next(){
        if(finished)throw new IllegalStateException("Preview capture already finished");
        var entries=new ListTag();int bytes=0,work=0;
        while(work<4096 && entries.size()<128){
            CompoundTag entry;
            if(pending!=null){entry=pending;pending=null;}
            else if(positions!=null && positions.hasNext()){
                var pos=positions.next().immutable();scanned++;work++;
                if(!world.hasChunkAt(pos)){unloaded++;continue;}
                var state=world.getBlockState(pos);if(state.isAir())continue;
                entry=new CompoundTag();entry.putLong("pos",pos.asLong());entry.putInt("state",Block.getId(state));
                var be=world.getBlockEntity(pos);
                if(be!=null && !(be instanceof BaseAccelerator))try{
                    var visual=PlatformData.visualTag(be);
                    if(!visual.isEmpty())entry.put("visual",visual.copy());
                }catch(RuntimeException failure){omitted++;}
            }else if(stored!=null && stored.hasNext()){
                var saved=stored.next();scanned++;work++;
                entry=new CompoundTag();entry.putLong("pos",saved.getLong("pos"));
                var state=NbtUtils.readBlockState(world.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK),saved.getCompound("state"));
                if(state.isAir())continue;entry.putInt("state",Block.getId(state));
            }else break;
            int size=size(entry);
            if(size>65536){entry.remove("visual");omitted++;size=size(entry);}
            if(bytes+size>131072 && !entries.isEmpty()){pending=entry;break;}
            entries.add(entry);bytes+=size;
        }
        finished=pending==null && (positions==null || !positions.hasNext()) && (stored==null || !stored.hasNext());
        var data=new CompoundTag();data.putLong("request",request);data.putInt("mode",mode);data.putInt("page",page++);
        data.putString("status",status);data.putLong("scanned",scanned);data.putLong("total",total);
        data.putInt("omitted",omitted);data.putInt("unloaded",unloaded);data.putBoolean("done",finished);data.put("blocks",entries);
        return data;
    }
    private static int size(CompoundTag tag){
        try{var output=new ByteArrayOutputStream();NbtIo.write(tag,new DataOutputStream(output));return output.size();}
        catch(IOException failure){throw new UncheckedIOException(failure);}
    }
}
