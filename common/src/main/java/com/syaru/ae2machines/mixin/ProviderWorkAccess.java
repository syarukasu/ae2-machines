package com.syaru.ae2machines.mixin;
import appeng.helpers.patternprovider.PatternProviderLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value=PatternProviderLogic.class,remap=false)
public interface ProviderWorkAccess {
    @Invoker("hasWorkToDo") boolean machines$hasWork();
    @Invoker("doWork") boolean machines$work();
}
