package com.syaru.ae2machines.mixin;

import com.lowdragmc.lowdraglib.syncdata.blockentity.IAsyncAutoSyncBlockEntity;
import com.lowdragmc.lowdraglib.async.IAsyncLogic;
import com.syaru.ae2machines.world.NativeCellWorlds;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** A queued display sync must not load chunks from a retired cell world. */
@Pseudo
@Mixin(targets="com.lowdragmc.lowdraglib.async.AsyncThreadData",remap=false)
public abstract class LdlibCellSyncMixin {
    @Redirect(method="searchingTask",at=@At(value="INVOKE",target="Lcom/lowdragmc/lowdraglib/async/IAsyncLogic;asyncTick(J)V"),remap=false)
    private void ae2machines$cellDisplaySync(IAsyncLogic logic,long period) {
        if(logic instanceof IAsyncAutoSyncBlockEntity sync
                && sync.getSelf().getLevel() instanceof ServerLevel level && NativeCellWorlds.owns(level)) {
            var block=sync.getSelf();
            if(!sync.useAsyncThread() || block.isRemoved() || sync.isAsyncSyncing())return;
            sync.setAsyncSyncing(true);
            level.getServer().execute(()->{
                try {
                    if(!block.isRemoved() && level.getServer().getLevel(level.dimension())==level
                            && level.hasChunkAt(block.getBlockPos()) && level.getBlockEntity(block.getBlockPos())==block)
                        sync.defaultServerTick();
                } finally {sync.setAsyncSyncing(false);}
            });
        }else {
            logic.asyncTick(period);
        }
    }
}
