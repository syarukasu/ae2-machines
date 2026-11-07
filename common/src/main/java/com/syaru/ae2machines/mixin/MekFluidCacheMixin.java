package com.syaru.ae2machines.mixin;

import java.util.List;
import mekanism.api.fluid.IExtendedFluidTank;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Pseudo
@Mixin(targets="mekanism.common.lib.multiblock.MultiblockCache",remap=false)
public abstract class MekFluidCacheMixin {
    @Redirect(method="merge",at=@At(value="INVOKE",target="Lmekanism/common/util/StorageUtils;mergeFluidTanks(Ljava/util/List;Ljava/util/List;Ljava/util/List;)V"))
    private void machines$merge(List<IExtendedFluidTank> into,List<IExtendedFluidTank> from,List<?> rejects){com.syaru.ae2machines.platform.MekFluidAccess.mergeCaches(into,from,rejects);}
}
