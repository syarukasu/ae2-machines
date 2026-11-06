package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.compat.mek.MekThermalHooks;
import com.syaru.ae2machines.world.*;
import it.unimi.dsi.fastutil.objects.*;
import java.util.function.BooleanSupplier;
import net.minecraft.server.level.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

/** The native unload queues still run. Only repeated autosave scans share an interval boundary. */
@Mixin(ChunkMap.class)
abstract class ThermalAutosaveMixin {
    @Shadow @Final private ServerLevel level;

    @Redirect(method="processUnloads",at=@At(value="INVOKE",target="Lit/unimi/dsi/fastutil/objects/ObjectCollection;iterator()Lit/unimi/dsi/fastutil/objects/ObjectIterator;",remap=false))
    private ObjectIterator<ChunkHolder> machines$intervalAutosave(ObjectCollection<ChunkHolder> original,BooleanSupplier time){
        if(NativeCellWorlds.owns(level) && ((Object)this).getClass()==ChunkMap.class
                && MekThermalHooks.chunkSaveSafe(ChunkMap.class) && MekThermalHooks.chunkSaveSafe(ChunkHolder.class)
                && NativeWorldRuntime.get(level).combineThermalAutosave((ChunkMap)(Object)this,time))return ObjectIterators.emptyIterator();
        return original.iterator();
    }
}
