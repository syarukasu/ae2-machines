package com.syaru.ae2machines.platform;
public final class GtEnergyRegistration {
    public static long remainingNativeAmps(com.gregtechceu.gtceu.api.capability.IEnergyContainer container) {
        if(container.getClass()!=com.gregtechceu.gtceu.api.machine.trait.NotifiableEnergyContainer.class
                && container.getClass()!=com.gregtechceu.gtceu.api.machine.trait.RecipeAmperageEnergyContainer.class)return -1;
        var nativeContainer=(com.gregtechceu.gtceu.api.machine.trait.NotifiableEnergyContainer)container;
        var access=(com.syaru.ae2machines.mixin.GtEnergyInputAccess)container;
        long used=access.ae2machines$lastTimeStamp()<nativeContainer.getMachine().getOffsetTimer()?0:access.ae2machines$amps();
        return Math.max(0,container.getInputAmperage()-used);
    }
    public static com.gregtechceu.gtceu.api.capability.IEnergyContainer nativeInput(net.minecraft.server.level.ServerLevel level,
            net.minecraft.world.level.block.entity.BlockEntity be,net.minecraft.core.Direction side) {
        return be.getCapability(com.gregtechceu.gtceu.api.capability.forge.GTCapability.CAPABILITY_ENERGY_CONTAINER,side).orElse(null);
    }
    private GtEnergyRegistration() {}
    public static boolean matches(net.minecraftforge.common.capabilities.Capability<?> cap) {
        return cap==com.gregtechceu.gtceu.api.capability.forge.GTCapability.CAPABILITY_ENERGY_CONTAINER;
    }
}
