package com.syaru.ae2machines.mixin;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(Level.class)
public interface NativeTemporalWeatherAccess {
    @Accessor("rainLevel") float machines$timeRain();
    @Accessor("oRainLevel") float machines$timePreviousRain();
    @Accessor("thunderLevel") float machines$timeThunder();
    @Accessor("oThunderLevel") float machines$timePreviousThunder();
}
