package com.syaru.ae2machines.runtime;

import appeng.api.storage.MEStorage;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.*;
import java.util.function.BiFunction;

public interface CapturedPipes {
    interface ColoredItemInput extends MEStorage {
        long insertColored(appeng.api.stacks.AEKey key,long amount,appeng.api.config.Actionable mode,int color);
    }
    record Preview(Set<Long> aliases,java.util.function.Function<Direction,MEStorage> storage) {
        public Preview { aliases=Set.copyOf(aliases); }
    }
    MEStorage at(long pos,Direction side);
    CompoundTag save();
    default void tick(){}
    default void restore(CompoundTag blueprint){}
    static boolean supported(BlockEntity be){
        return NativeMachines.gt() && com.syaru.ae2machines.compat.gt.GtItemPipes.supported(be)
                || NativeMachines.mek() && (com.syaru.ae2machines.platform.MekPipeAccess.supported(be)
                || com.syaru.ae2machines.platform.MekTransporterAccess.supported(be));
    }
    static CompoundTag inspect(List<BlockEntity> devices,Set<BlockPos> positions){
        var result=NativeMachines.gt()?com.syaru.ae2machines.compat.gt.GtItemPipes.inspect(devices,positions):new CompoundTag();
        if(NativeMachines.mek()) {
            var mek=com.syaru.ae2machines.platform.MekPipeAccess.inspect(devices,positions);
            if(!mek.isEmpty())result.put("mek",mek);
            var items=com.syaru.ae2machines.platform.MekTransporterAccess.inspect(devices,positions);
            if(!items.isEmpty())result.put("mekItems",items);
        }
        return result;
    }
    static CapturedPipes open(ServerLevel level,CompoundTag blueprint,CompoundTag saved,BiFunction<Long,Direction,MEStorage> target,
                              java.util.function.LongFunction<Preview> preview,Runnable changed){
        var definitions=blueprint.getCompound("pipes");if(definitions.isEmpty())return null;
        CapturedPipes gt=definitions.contains("routes")?new com.syaru.ae2machines.compat.gt.GtItemPipes(level,blueprint,saved,target,changed):null;
        CapturedPipes mek=definitions.contains("mek")?new com.syaru.ae2machines.compat.mek.MekBufferedPipes(level,blueprint,saved.getCompound("mek"),target,changed):null;
        CapturedPipes items=definitions.contains("mekItems")?new com.syaru.ae2machines.compat.mek.MekItemPipes(level,blueprint,saved.getCompound("mekItems"),target,preview,changed):null;
        return new CapturedPipes() {
            public MEStorage at(long pos,Direction side){var a=gt==null?null:gt.at(pos,side);if(a==null && mek!=null)a=mek.at(pos,side);return a!=null?a:items==null?null:items.at(pos,side);}
            public CompoundTag save(){var result=gt==null?new CompoundTag():gt.save();if(mek!=null)result.put("mek",mek.save());if(items!=null)result.put("mekItems",items.save());return result;}
            public void tick(){if(gt!=null)gt.tick();if(mek!=null)mek.tick();if(items!=null)items.tick();}
            public void restore(CompoundTag blueprint){if(gt!=null)gt.restore(blueprint);if(mek!=null)mek.restore(blueprint);if(items!=null)items.restore(blueprint);}
        };
    }
}
