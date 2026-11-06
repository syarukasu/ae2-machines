package com.syaru.ae2machines.mixin;
import com.syaru.ae2machines.world.NativeWorldRuntime;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntity.class)
public abstract class NativeTopologyBlockEntityMixin {
    @Inject(method={"setRemoved","clearRemoved"},at=@At("RETURN"))
    private void machines$topologyChanged(CallbackInfo ci){
        var block=(BlockEntity)(Object)this;
        if(block.getLevel() instanceof ServerLevel level && NativeWorldRuntime.owns(level)){
            var pos=block.getBlockPos();
            var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
            // Isolated native recipe representatives do not alter the resident factory topology.
            if(chunk!=null && chunk.getBlockEntities().get(pos)==block)NativeWorldRuntime.get(level).invalidateEquipment(pos);
        }
    }
}
