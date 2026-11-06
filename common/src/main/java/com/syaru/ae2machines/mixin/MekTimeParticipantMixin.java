package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.api.NativeTimeParticipant;
import com.syaru.ae2machines.compat.mek.MekQuietTime;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;

@Pseudo
@Mixin(targets="mekanism.common.tile.prefab.TileEntityProgressMachine",remap=false)
public abstract class MekTimeParticipantMixin implements NativeTimeParticipant {
    @Override public Step nativeTimeStep(int maximumTicks){
        var step=MekQuietTime.plan((BlockEntity)(Object)this,maximumTicks);
        return step==null?null:new Step(step.limit(),step.advance(),step.rollback());
    }
}
