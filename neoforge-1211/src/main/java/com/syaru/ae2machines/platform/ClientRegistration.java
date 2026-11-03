package com.syaru.ae2machines.platform;
import com.syaru.ae2machines.client.AcceleratorScreen;
public final class ClientRegistration {
    private ClientRegistration() {}
    public static void register(net.neoforged.bus.api.IEventBus bus) {
        bus.addListener((net.neoforged.neoforge.client.event.ModelEvent.RegisterGeometryLoaders e)->e.register(PlatformData.id("ae2machines:virtual_pylon"),(net.neoforged.neoforge.client.model.geometry.IGeometryLoader<VirtualPylonGeometry>)(json,context)->new VirtualPylonGeometry()));
        bus.addListener((net.neoforged.neoforge.client.event.ModelEvent.RegisterGeometryLoaders e)->e.register(PlatformData.id("ae2machines:computation_casing"),(net.neoforged.neoforge.client.model.geometry.IGeometryLoader<ComputationCasingGeometry>)(json,context)->new ComputationCasingGeometry()));
        bus.addListener((net.neoforged.neoforge.client.event.ModelEvent.RegisterAdditional e)->{
            for(int i=0;i<com.syaru.ae2machines.client.ComputationCasingTiles.COUNT;i++)e.register(net.minecraft.client.resources.model.ModelResourceLocation.standalone(PlatformData.id(com.syaru.ae2machines.client.ComputationCasingTiles.model(i))));
        });
        bus.addListener((net.neoforged.fml.event.lifecycle.FMLClientSetupEvent e)->e.enqueueWork(()->{
            net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(MachinesMod.MARKER.get(),net.minecraft.client.renderer.RenderType.cutout());
            net.minecraft.client.renderer.item.ItemProperties.register(MachinesMod.CELL.get(),PlatformData.id("ae2machines:filled"),
                    (stack,level,entity,seed)->PlatformData.cellData(stack).hasUUID("factory") ? 1 : 0);
        }));
        bus.addListener((net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event)->event.register(MachinesMod.MENU.get(),AcceleratorScreen::new));
    }
}
