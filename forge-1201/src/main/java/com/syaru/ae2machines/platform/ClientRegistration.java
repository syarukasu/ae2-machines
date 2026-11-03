package com.syaru.ae2machines.platform;
import com.syaru.ae2machines.client.AcceleratorScreen;
public final class ClientRegistration {
    private ClientRegistration() {}
    public static void register(net.minecraftforge.eventbus.api.IEventBus bus) {
        bus.addListener((net.minecraftforge.client.event.ModelEvent.RegisterGeometryLoaders e)->e.register("virtual_pylon",(net.minecraftforge.client.model.geometry.IGeometryLoader<VirtualPylonGeometry>)(json,context)->new VirtualPylonGeometry()));
        bus.addListener((net.minecraftforge.client.event.ModelEvent.RegisterGeometryLoaders e)->e.register("computation_casing",(net.minecraftforge.client.model.geometry.IGeometryLoader<ComputationCasingGeometry>)(json,context)->new ComputationCasingGeometry()));
        bus.addListener((net.minecraftforge.client.event.ModelEvent.RegisterAdditional e)->{
            for(int i=0;i<com.syaru.ae2machines.client.ComputationCasingTiles.COUNT;i++)e.register(PlatformData.id(com.syaru.ae2machines.client.ComputationCasingTiles.model(i)));
        });
        bus.addListener((net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent e)->e.enqueueWork(()->{
            net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(MachinesMod.MARKER.get(),net.minecraft.client.renderer.RenderType.cutout());
            net.minecraft.client.renderer.item.ItemProperties.register(MachinesMod.CELL.get(),PlatformData.id("ae2machines:filled"),
                    (stack,level,entity,seed)->PlatformData.cellData(stack).hasUUID("factory") ? 1 : 0);
        }));
        bus.addListener((net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event)->event.enqueueWork(()->net.minecraft.client.gui.screens.MenuScreens.register(MachinesMod.MENU.get(),AcceleratorScreen::new)));
    }
}
