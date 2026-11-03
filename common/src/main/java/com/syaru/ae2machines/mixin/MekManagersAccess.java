package com.syaru.ae2machines.mixin;

import java.util.Set;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets="mekanism.common.lib.multiblock.MultiblockManager",remap=false)
public interface MekManagersAccess {
    @Accessor("managers") static Set<?> ae2machines$managers(){throw new AssertionError();}
}
