package com.syaru.ae2machines.platform;

import com.syaru.ae2machines.block.*;
import appeng.blockentity.spatial.SpatialIOPortBlockEntity;
import com.syaru.ae2machines.compat.OptionalAco;
import com.syaru.ae2machines.menu.AcceleratorMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.registries.DeferredRegister;

@net.minecraftforge.fml.common.Mod(MachinesMod.ID)
public final class MachinesMod {
    public static final String ID="ae2machines";
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(Registries.BLOCK,ID);
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(Registries.ITEM,ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,ID);
    private static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,ID);
    private static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,ID);
    private static final java.util.List<java.util.function.Supplier<? extends Item>> COMPUTATION_ITEMS=new java.util.ArrayList<>();
    public static final java.util.function.Supplier<ComputationHostBlock> COMPUTATION_CONTROLLER=computationHost("computation_controller",ComputationHostBlock.Role.CONTROLLER);
    public static final java.util.function.Supplier<ComputationHostBlock> CELL_BAY=computationHost("computation_cell_bay",ComputationHostBlock.Role.BAY);
    public static final java.util.function.Supplier<ComputationHostBlock> CHANNEL_PORT=computationHost("computation_channel_port",ComputationHostBlock.Role.CHANNEL);
    public static final java.util.function.Supplier<ComputationPartBlock> COMPUTATION_CASING=computationPart("computation_casing",ComputationPartBlock.Kind.CASING,0);
    public static final java.util.function.Supplier<ComputationPartBlock> COOLING_UNIT=computationPart("computation_cooling_unit",ComputationPartBlock.Kind.COOLING,0);
    public static final java.util.function.Supplier<ComputationPartBlock> END_CAP=computationPart("computation_end_cap",ComputationPartBlock.Kind.END,0);
    public static final java.util.List<java.util.function.Supplier<ComputationPartBlock>> PARALLEL_PROCESSORS=java.util.stream.IntStream.rangeClosed(1,9).mapToObj(t->computationPart("parallel_processor_t"+t,ComputationPartBlock.Kind.PARALLEL,t)).toList();
    public static final java.util.List<java.util.function.Supplier<ComputationPartBlock>> SPEED_PROCESSORS=java.util.stream.IntStream.rangeClosed(1,9).mapToObj(t->computationPart("speed_processor_t"+t,ComputationPartBlock.Kind.SPEED,t)).toList();
    private static java.util.function.Supplier<ComputationHostBlock> computationHost(String id,ComputationHostBlock.Role role) {
        var block=BLOCKS.register(id,()->new ComputationHostBlock(role));
        COMPUTATION_ITEMS.add(ITEMS.register(id,()->new BlockItem(block.get(),new Item.Properties())));return block;
    }
    private static java.util.function.Supplier<ComputationPartBlock> computationPart(String id,ComputationPartBlock.Kind kind,int tier) {
        var block=BLOCKS.register(id,()->new ComputationPartBlock(kind,tier));
        COMPUTATION_ITEMS.add(ITEMS.register(id,()->new BlockItem(block.get(),new Item.Properties())));return block;
    }
    public static final net.minecraftforge.registries.RegistryObject<AcceleratorBlock> ACCELERATOR=BLOCKS.register("spatial_accelerator",AcceleratorBlock::new);
    public static final net.minecraftforge.registries.RegistryObject<EnergyPortBlock> ENERGY=BLOCKS.register("virtual_energy_port",EnergyPortBlock::new);
    public static final net.minecraftforge.registries.RegistryObject<EnergyPortBlock> COMPUTATION_POWER=BLOCKS.register("computation_power_port",EnergyPortBlock::new);
    public static final net.minecraftforge.registries.RegistryObject<MarkerBlock> MARKER=BLOCKS.register("virtual_spatial_marker",MarkerBlock::new);
    public static final net.minecraftforge.registries.RegistryObject<SpatialEncoderBlock> ENCODER=BLOCKS.register("virtual_spatial_io_port",SpatialEncoderBlock::new);
    public static final net.minecraftforge.registries.RegistryObject<BlockItem> ENCODER_ITEM=ITEMS.register("virtual_spatial_io_port",()->new BlockItem(ENCODER.get(),new Item.Properties()));
    public static final net.minecraftforge.registries.RegistryObject<BlockItem> ACCELERATOR_ITEM=ITEMS.register("spatial_accelerator",()->new BlockItem(ACCELERATOR.get(),new Item.Properties()));
    public static final net.minecraftforge.registries.RegistryObject<BlockItem> ENERGY_ITEM=ITEMS.register("virtual_energy_port",()->new BlockItem(ENERGY.get(),new Item.Properties()));
    public static final net.minecraftforge.registries.RegistryObject<BlockItem> COMPUTATION_POWER_ITEM=ITEMS.register("computation_power_port",()->new BlockItem(COMPUTATION_POWER.get(),new Item.Properties()));
    public static final net.minecraftforge.registries.RegistryObject<BlockItem> MARKER_ITEM=ITEMS.register("virtual_spatial_marker",()->new BlockItem(MARKER.get(),new Item.Properties()));
    public static final net.minecraftforge.registries.RegistryObject<SpatialCellItem> CELL=ITEMS.register("virtual_spatial_cell",SpatialCellItem::new);
    public static final net.minecraftforge.registries.RegistryObject<appeng.items.parts.PartItem<VirtualP2PPart>> P2P=ITEMS.register("virtual_p2p",()->new appeng.items.parts.PartItem<>(new Item.Properties(),VirtualP2PPart.class,VirtualP2PPart::new));
    public static final java.util.function.Supplier<CreativeModeTab> CREATIVE_TAB=TABS.register("main",()->CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.ae2machines"))
            .icon(()->new ItemStack(COMPUTATION_CONTROLLER.get()))
            .displayItems((parameters,output)->creativeItems(output::accept)).build());
    private static void creativeItems(java.util.function.Consumer<Item> output){
        output.accept(MARKER_ITEM.get());output.accept(CELL.get());output.accept(ENCODER_ITEM.get());output.accept(P2P.get());
        COMPUTATION_ITEMS.forEach(item->output.accept(item.get()));
        output.accept(ENERGY_ITEM.get());output.accept(COMPUTATION_POWER_ITEM.get());output.accept(ACCELERATOR_ITEM.get());
    }
    public static final net.minecraftforge.registries.RegistryObject<BlockEntityType<AcceleratorEntity>> ACCELERATOR_ENTITY=ENTITIES.register("spatial_accelerator",()->BlockEntityType.Builder.of(MachinesMod::accelerator,ACCELERATOR.get(),COMPUTATION_CONTROLLER.get(),CELL_BAY.get(),CHANNEL_PORT.get()).build(null));
    public static final net.minecraftforge.registries.RegistryObject<BlockEntityType<EnergyPortEntity>> ENERGY_ENTITY=ENTITIES.register("virtual_energy_port",()->BlockEntityType.Builder.of(EnergyPortEntity::new,ENERGY.get(),COMPUTATION_POWER.get()).build(null));
    public static final net.minecraftforge.registries.RegistryObject<BlockEntityType<appeng.blockentity.spatial.SpatialPylonBlockEntity>> MARKER_ENTITY=ENTITIES.register("virtual_spatial_marker",()->BlockEntityType.Builder.of(MachinesMod::marker,MARKER.get()).build(null));
    public static final net.minecraftforge.registries.RegistryObject<MenuType<AcceleratorMenu>> MENU=MENUS.register("spatial_accelerator",()->net.minecraftforge.common.extensions.IForgeMenuType.create(AcceleratorMenu::fromNetwork));
    public static final net.minecraftforge.registries.RegistryObject<BlockEntityType<SpatialIOPortBlockEntity>> ENCODER_ENTITY=ENTITIES.register("virtual_spatial_io_port",()->BlockEntityType.Builder.of((pos,state)->new SpatialIOPortBlockEntity(MachinesMod.ENCODER_ENTITY.get(),pos,state),ENCODER.get()).build(null));
    private static AcceleratorEntity accelerator(BlockPos pos,BlockState state) {
        if(OptionalAco.exactProcessing())try {
            return (AcceleratorEntity)Class.forName("com.syaru.ae2machines.platform.ExactAcoAcceleratorEntity")
                    .getConstructor(BlockPos.class,BlockState.class).newInstance(pos,state);
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("ACO exact native worker binding failed",failure);}
        if (!OptionalAco.legacyProcessing()) return new AcceleratorEntity(pos,state);
        try {
            return (AcceleratorEntity) Class.forName("com.syaru.ae2machines.platform.AcoAcceleratorEntity")
                    .getConstructor(BlockPos.class,BlockState.class).newInstance(pos,state);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Present ACO API could not bind the processing worker",error);
        }
    }
    private static appeng.blockentity.spatial.SpatialPylonBlockEntity marker(BlockPos pos,BlockState state) {
        return new appeng.blockentity.spatial.SpatialPylonBlockEntity(MARKER_ENTITY.get(),pos,state);
    }
    public MachinesMod() {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST,(net.minecraftforge.event.TickEvent.ServerTickEvent e)->{
            if(e.phase==net.minecraftforge.event.TickEvent.Phase.END)com.syaru.ae2machines.world.NativeCellWorlds.endTick(e.getServer());
        });
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGHEST,(net.minecraftforge.event.TickEvent.ServerTickEvent e)->{
            if(e.phase==net.minecraftforge.event.TickEvent.Phase.START)com.syaru.ae2machines.world.NativeWorldCheckpoint.beforeTick(e.getServer());
        });
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGHEST,(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e)->{
            if(e.getEntity().getServer()!=null)com.syaru.ae2machines.world.NativeWorldCheckpoint.beforeTick(e.getEntity().getServer());
        });
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST,(net.minecraftforge.event.server.ServerStartingEvent e)->com.syaru.ae2machines.world.NativeMoveRecovery.recover(e.getServer()));
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST,(net.minecraftforge.event.TickEvent.LevelTickEvent e)->{
            if(e.phase==net.minecraftforge.event.TickEvent.Phase.END && e.level instanceof net.minecraft.server.level.ServerLevel level && com.syaru.ae2machines.world.NativeCellWorlds.owns(level))
                com.syaru.ae2machines.world.NativeWorldRuntime.get(level).afterTick(e::haveTime);
        });
        var bus = net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(bus);ITEMS.register(bus);ENTITIES.register(bus);MENUS.register(bus);TABS.register(bus);
        appeng.api.parts.PartModels.registerModels(VirtualP2PPart.INPUT,VirtualP2PPart.OUTPUT);
        bus.addListener((net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent e)->e.enqueueWork(()->{
            OptionalAco.initialize();
            appeng.api.networking.GridServices.register(com.syaru.ae2machines.runtime.NativeGridState.class,com.syaru.ae2machines.runtime.NativeGridState.class);
            ACCELERATOR.get().setBlockEntity(AcceleratorEntity.class,ACCELERATOR_ENTITY.get(),null,(level,pos,state,be)->be.tickServer());
            for(var block:java.util.List.of(COMPUTATION_CONTROLLER.get(),CELL_BAY.get(),CHANNEL_PORT.get()))
                block.setBlockEntity(AcceleratorEntity.class,ACCELERATOR_ENTITY.get(),null,(level,pos,state,be)->be.tickServer());
            ENERGY.get().setBlockEntity(EnergyPortEntity.class,ENERGY_ENTITY.get(),null,null);
            COMPUTATION_POWER.get().setBlockEntity(EnergyPortEntity.class,ENERGY_ENTITY.get(),null,null);
            MARKER.get().setBlockEntity(appeng.blockentity.spatial.SpatialPylonBlockEntity.class,MARKER_ENTITY.get(),null,null);
            ENCODER.get().setBlockEntity(SpatialIOPortBlockEntity.class,ENCODER_ENTITY.get(),null,null);
            appeng.blockentity.AEBaseBlockEntity.registerBlockEntityItem(ENCODER_ENTITY.get(),ENCODER_ITEM.get());
            appeng.blockentity.AEBaseBlockEntity.registerBlockEntityItem(ACCELERATOR_ENTITY.get(),ACCELERATOR_ITEM.get());
            appeng.blockentity.AEBaseBlockEntity.registerBlockEntityItem(ENERGY_ENTITY.get(),ENERGY_ITEM.get());
            appeng.blockentity.AEBaseBlockEntity.registerBlockEntityItem(MARKER_ENTITY.get(),MARKER_ITEM.get());
        }));
        
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock e)->{
            if(com.syaru.ae2machines.block.CellBayInteraction.use(e.getEntity(),e.getPos(),e.getHand())) { e.setCancellationResult(InteractionResult.SUCCESS);e.setCanceled(true);return; }
            if(BaseEnergyPort.toggle(e.getEntity(),e.getPos())) { e.setCancellationResult(InteractionResult.SUCCESS);e.setCanceled(true);return; }
            if(e.getLevel().getBlockEntity(e.getPos()) instanceof BaseAccelerator host && ComputationStructure.guiController(host) && !e.getEntity().isShiftKeyDown() && !e.getItemStack().is(CELL.get())) {
                if(e.getEntity() instanceof ServerPlayer player && host.canAccess(player)) {
                    var provider=new SimpleMenuProvider((id,inv,p)->new AcceleratorMenu(id,inv,host),Component.translatable("block.ae2machines.computation_controller"));
                    net.minecraftforge.network.NetworkHooks.openScreen(player,provider,host.getBlockPos());
                }
                e.setCancellationResult(InteractionResult.SUCCESS);e.setCanceled(true);
            }
        });
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.OnDatapackSyncEvent e)->{
            if(e.getPlayer()==null) com.syaru.ae2machines.runtime.FactoryRecipes.changed();
        });
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.server.ServerStoppingEvent e)->com.syaru.ae2machines.world.FactoryRecovery.stopping(e.getServer()));
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.server.ServerStoppedEvent e)->{
            com.syaru.ae2machines.world.FactoryRecovery.stopped(e.getServer());
            com.syaru.ae2machines.world.ExternalHandoffs.stopped(e.getServer());
            com.syaru.ae2machines.world.NativeCellWorlds.stopped(e.getServer());
            ComputationStructure.stopped(e.getServer());
        });
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.level.ChunkDataEvent.Save e)->{
            if(e.getLevel() instanceof net.minecraft.server.level.ServerLevel level)com.syaru.ae2machines.world.ExternalRecovery.chunkSaved(level,e.getChunk().getPos(),e.getData());
        });
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.level.ChunkDataEvent.Load e)->{
            if(e.getLevel() instanceof net.minecraft.server.level.ServerLevel level)com.syaru.ae2machines.world.ExternalRecovery.chunkLoaded(level,e.getChunk().getPos(),e.getData());
        });
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGHEST,(net.minecraftforge.event.level.LevelEvent.Load e)->{
            if(e.getLevel() instanceof net.minecraft.server.level.ServerLevel level)com.syaru.ae2machines.world.ExternalHandoffs.recoverBeforeChunks(level);
        });
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.level.LevelEvent.Save e)->{
            if(e.getLevel() instanceof net.minecraft.server.level.ServerLevel level)com.syaru.ae2machines.world.ExternalRecovery.levelSaved(level);
        });
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.level.LevelEvent.Unload e)->{
            if(e.getLevel() instanceof net.minecraft.server.level.ServerLevel level)com.syaru.ae2machines.world.ExternalRecovery.unload(level);
        });
        if(net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()) ClientRegistration.register(bus);
    }
}
