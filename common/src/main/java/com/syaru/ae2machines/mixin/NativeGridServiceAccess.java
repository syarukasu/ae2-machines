package com.syaru.ae2machines.mixin;
import appeng.me.Grid;
import appeng.me.helpers.GridServiceContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(value=Grid.class,remap=false)
public interface NativeGridServiceAccess {
    @Accessor("services") GridServiceContainer machines$timeServices();
}
