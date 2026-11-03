package com.syaru.ae2machines.platform;
public final class GtEnergyRegistration {
    public static long remainingNativeAmps(com.gregtechceu.gtceu.api.capability.IEnergyContainer container) {
        if(container.getClass()!=com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableEnergyContainer.class && container.getClass()!=com.gregtechceu.gtceu.api.machine.trait.notifiable.RecipeAmperageEnergyContainer.class)return -1;
        var nativeContainer=(com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableEnergyContainer)container;
        var access=(com.syaru.ae2machines.mixin.GtEnergyInputAccess)container;
        long used=access.ae2machines$lastTimeStamp()<nativeContainer.getMachine().getOffsetTimer()?0:access.ae2machines$amps();
        return Math.max(0,container.getInputAmperage()-used);
    }
    public static com.gregtechceu.gtceu.api.capability.IEnergyContainer nativeInput(net.minecraft.server.level.ServerLevel level,
            net.minecraft.world.level.block.entity.BlockEntity be,net.minecraft.core.Direction side) {
        return level.getCapability(com.gregtechceu.gtceu.api.capability.GTCapability.CAPABILITY_ENERGY_CONTAINER,be.getBlockPos(),side);
    }
    private GtEnergyRegistration() {}
    public static void register(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(com.gregtechceu.gtceu.api.capability.GTCapability.CAPABILITY_ENERGY_CONTAINER,
                MachinesMod.ENERGY_ENTITY.get(),(be,side)->new com.syaru.ae2machines.compat.gt.EuInput(be));
    }
}
