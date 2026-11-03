package com.syaru.ae2machines.mixin;
import com.syaru.ae2machines.platform.MekNativeEnergy;
import mekanism.api.math.FloatingLong;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

/** FloatingLong division may round equal fractional values below one; only fix owned payments. */
@Pseudo @Mixin(targets="mekanism.api.recipes.cache.CachedRecipe",remap=false)
public abstract class MekOwnedEnergyDivisionMixin {
    @Redirect(method="calculateOperationsThisTick",at=@At(value="INVOKE",target="Lmekanism/api/math/FloatingLong;divideToInt(Lmekanism/api/math/FloatingLong;)I"))
    private int machines$available(FloatingLong stored,FloatingLong price){return MekNativeEnergy.exactAvailability()?MekNativeEnergy.divideAvailable(stored,price):stored.divideToInt(price);}
}
