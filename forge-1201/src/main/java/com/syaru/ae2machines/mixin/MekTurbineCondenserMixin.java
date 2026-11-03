package com.syaru.ae2machines.mixin;

import mekanism.api.chemical.gas.IGasTank;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.ModList;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Extras changes the fluid first; keep the native amount without redirecting the tank call. */
@Pseudo
@Mixin(targets="mekanism.generators.common.content.turbine.TurbineMultiblockData",priority=2000,remap=false)
public abstract class MekTurbineCondenserMixin {
    @Shadow public IGasTank gasTank;
    @Unique private boolean machines$poloniumSteam;
    @Unique private int machines$condensed;
    @Unique private boolean machines$correctExtras;

    @Inject(method="tick",at=@At("HEAD"))
    private void machines$input(Level world,CallbackInfoReturnable<Boolean> ci) {
        machines$correctExtras = com.syaru.ae2machines.world.NativeCellWorlds.owns(world)
                && ModList.get().isLoaded("mekanism_extras");
        machines$condensed = 0;
        machines$poloniumSteam = machines$correctExtras && gasTank.getType().getRegistryName().equals(
                new ResourceLocation("mekanism_extras","polonium_containing_steam"));
    }

    @ModifyArg(method="tick",at=@At(value="INVOKE",
            target="Lnet/minecraftforge/fluids/FluidStack;<init>(Lnet/minecraft/world/level/material/Fluid;I)V"),index=1)
    private int machines$nativeAmount(int amount) {
        machines$condensed = amount;
        return amount;
    }

    @ModifyArg(method="tick",at=@At(value="INVOKE",
            target="Lmekanism/api/fluid/IExtendedFluidTank;setStack(Lnet/minecraftforge/fluids/FluidStack;)V"),index=0)
    private FluidStack machines$condense(FluidStack output) {
        if (machines$correctExtras) {
            if (machines$poloniumSteam) {
                var fluid = BuiltInRegistries.FLUID.get(new ResourceLocation("mekanism_extras","polonium_containing_solution"));
                output = new FluidStack(fluid,machines$condensed / 1000);
            } else {
                output = new FluidStack(output.getRawFluid(),machines$condensed);
            }
        }
        return output;
    }
}
