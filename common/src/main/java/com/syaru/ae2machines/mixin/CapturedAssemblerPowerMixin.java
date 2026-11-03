package com.syaru.ae2machines.mixin;

import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.blockentity.crafting.MolecularAssemblerBlockEntity;
import com.syaru.ae2machines.runtime.CapturedAssembler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Virtual work shares the physical assembler's power grid; native userPower still charges every craft. */
@Mixin(value=MolecularAssemblerBlockEntity.class,remap=false)
public abstract class CapturedAssemblerPowerMixin {
    @Redirect(method="userPower",at=@At(value="INVOKE",target="Lappeng/api/networking/IManagedGridNode;getGrid()Lappeng/api/networking/IGrid;"))
    private IGrid machines$powerGrid(IManagedGridNode node) {
        return (Object)this instanceof CapturedAssembler assembler && assembler.isVirtualWorker()?assembler.powerGrid():node.getGrid();
    }
}
