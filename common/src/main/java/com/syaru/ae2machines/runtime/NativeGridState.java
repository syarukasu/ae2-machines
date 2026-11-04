package com.syaru.ae2machines.runtime;

import appeng.api.networking.*;
import com.syaru.ae2machines.world.*;
import java.util.*;
import net.minecraft.server.level.ServerLevel;

/** Topology updates maintain this index; paused grids do not scan their nodes every tick. */
public final class NativeGridState implements IGridService,IGridServiceProvider {
    private static final Map<ServerLevel,Set<NativeGridState>> INDEX=new IdentityHashMap<>();
    private final IGrid grid;
    private final Map<IGridNode,ServerLevel> nativeNodes=new IdentityHashMap<>();
    private final Map<ServerLevel,Integer> worlds=new IdentityHashMap<>();
    private int ordinaryNodes;
    public NativeGridState(IGrid grid){this.grid=grid;}
    public static void unload(ServerLevel level){INDEX.remove(level);}
    public static void stopped(net.minecraft.server.MinecraftServer server){INDEX.keySet().removeIf(level->level.getServer()==server);}
    public static boolean indexed(ServerLevel level,IGridNode node){return node==null || node.getGrid().getService(NativeGridState.class).nativeNodes.get(node)==level;}
    public static long nextEvent(ServerLevel level){
        long next=Long.MAX_VALUE;
        for(var service:INDEX.getOrDefault(level,Set.of())){
            var services=((com.syaru.ae2machines.mixin.NativeGridServiceAccess)service.grid).machines$timeServices();
            if(services.levelStartTickServices().length!=0)return Long.MIN_VALUE;
            for(var provider:services.levelEndtickServices())if(provider.getClass()!=appeng.me.service.TickManagerService.class)return Long.MIN_VALUE;
            var manager=(com.syaru.ae2machines.mixin.NativeLevelTickQueue)service.grid.getTickManager();
            var queue=manager.machines$upcoming().get(level);
            if(queue!=null && !queue.isEmpty())next=Math.min(next,queue.peek().getNextTick());
        }
        return next;
    }
    @Override public void addNode(IGridNode node,net.minecraft.nbt.CompoundTag saved){addNode(node);}
    public void addNode(IGridNode node) {
        var level=node.getLevel();
        if(level!=null && NativeCellWorlds.owns(level)) {
            nativeNodes.put(node,level);worlds.merge(level,1,Integer::sum);
            INDEX.computeIfAbsent(level,ignored->Collections.newSetFromMap(new IdentityHashMap<>())).add(this);
        }else ordinaryNodes++;
    }
    @Override public void removeNode(IGridNode node) {
        var level=nativeNodes.remove(node);
        if(level==null)ordinaryNodes--;
        else {
            worlds.compute(level,(key,n)->n==1?null:n-1);
            if(!worlds.containsKey(level)){var services=INDEX.get(level);if(services!=null){services.remove(this);if(services.isEmpty())INDEX.remove(level);}}
        }
    }
    public boolean paused() {
        if(ordinaryNodes!=0 || worlds.isEmpty())return false;
        for(var level:worlds.keySet())if(NativeWorldRuntime.get(level).running())return false;
        return true;
    }
}
