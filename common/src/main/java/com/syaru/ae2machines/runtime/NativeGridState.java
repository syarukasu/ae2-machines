package com.syaru.ae2machines.runtime;

import appeng.api.networking.*;
import com.syaru.ae2machines.world.*;
import java.util.*;
import net.minecraft.server.level.ServerLevel;

/** Topology updates maintain this index; paused grids do not scan their nodes every tick. */
public final class NativeGridState implements IGridService,IGridServiceProvider {
    private final Map<IGridNode,ServerLevel> nativeNodes=new IdentityHashMap<>();
    private final Map<ServerLevel,Integer> worlds=new IdentityHashMap<>();
    private int ordinaryNodes;
    public NativeGridState(IGrid grid){}
    @Override public void addNode(IGridNode node,net.minecraft.nbt.CompoundTag saved){addNode(node);}
    public void addNode(IGridNode node) {
        var level=node.getLevel();
        if(level!=null && NativeCellWorlds.owns(level)) {
            nativeNodes.put(node,level);worlds.merge(level,1,Integer::sum);
        }else ordinaryNodes++;
    }
    @Override public void removeNode(IGridNode node) {
        var level=nativeNodes.remove(node);
        if(level==null)ordinaryNodes--;
        else worlds.compute(level,(key,n)->n==1?null:n-1);
    }
    public boolean paused() {
        if(ordinaryNodes!=0 || worlds.isEmpty())return false;
        for(var level:worlds.keySet())if(NativeWorldRuntime.get(level).running())return false;
        return true;
    }
}
