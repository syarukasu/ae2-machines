package com.syaru.ae2machines.runtime;

import appeng.api.behaviors.*;
import appeng.api.config.*;
import appeng.api.networking.*;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.api.storage.*;
import appeng.blockentity.misc.InterfaceBlockEntity;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.helpers.InterfaceLogic;
import appeng.parts.*;
import appeng.parts.automation.*;
import appeng.parts.misc.InterfacePart;
import appeng.parts.networking.*;
import appeng.parts.storagebus.StorageBusPart;
import com.syaru.ae2machines.platform.PlatformData;
import com.syaru.ae2machines.platform.CapturedStorageAccess;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.*;
import static com.syaru.ae2machines.world.NativeFactoryCompiler.require;

/** Native AE2 devices, with adjacent capabilities bound to captured peers instead of empty world positions. */
public final class CapturedNetworkDevices implements AutoCloseable {
    private final Map<Long,BlockEntity> blocks=new LinkedHashMap<>();
    private final List<AEBasePart> parts=new ArrayList<>();
    private final List<InterfaceBlockEntity> interfaces=new ArrayList<>();
    private final List<CapturedAssembler> assemblers=new ArrayList<>();
    private final List<StorageBusPart> storageBuses=new ArrayList<>();
    private final Map<StorageBusPart,RouteState> routes=new IdentityHashMap<>();
    private record RouteState(IGrid from,IGrid to,boolean active,boolean cyclic){}
    private boolean ready,closed;
    private CapturedInventories inventories;
    private final java.util.function.Consumer<Runnable> atomic;
    private final Map<Long,IGridNode> providers;

    public static boolean supported(BlockEntity be) {
        if(NativeAssemblers.supports(be))return true;
        if(be instanceof InterfaceBlockEntity)return be.getClass()==InterfaceBlockEntity.class;
        if(!(be instanceof CableBusBlockEntity host) || !(host.getPart(null) instanceof CablePart))return false;
        for(var side:Direction.values()) {
            var part=host.getPart(side);
            if(part!=null && part.getClass()!=StorageBusPart.class && part.getClass()!=ImportBusPart.class
                    && part.getClass()!=ExportBusPart.class && part.getClass()!=InterfacePart.class
                    && part.getClass()!=QuartzFiberPart.class)return false;
        }
        return true;
    }
    public static Map<String,IGridNode> ports(BlockEntity be) {
        var result=new LinkedHashMap<String,IGridNode>();
        if(be instanceof appeng.blockentity.crafting.MolecularAssemblerBlockEntity assembler)result.put("",assembler.getMainNode().getNode());
        else if(be instanceof InterfaceBlockEntity iface)result.put("",iface.getMainNode().getNode());
        else if(be instanceof CableBusBlockEntity host) {
            result.put("",host.getPart(null).getGridNode());
            for(var side:Direction.values())if(host.getPart(side) instanceof AEBasePart part) {
                result.put(side.getName(),part.getGridNode());
                if(part instanceof QuartzFiberPart)result.put(side.getName()+"/outer",part.getExternalFacingNode());
            }
        }
        return result;
    }
    private static InterfaceLogic logic(BlockEntity be,Direction side) {
        if(be instanceof InterfaceBlockEntity iface)return iface.getInterfaceLogic();
        if(be instanceof CableBusBlockEntity host && host.getPart(side) instanceof InterfacePart iface)return iface.getInterfaceLogic();
        return null;
    }
    private static IGridNode interfaceNode(BlockEntity be,Direction side) {
        if(be instanceof InterfaceBlockEntity iface)return iface.getMainNode().getNode();
        if(be instanceof CableBusBlockEntity host && host.getPart(side) instanceof InterfacePart iface)return iface.getGridNode();
        return null;
    }
    public static void validate(List<BlockEntity> devices) {
        CapturedInventories.validate(devices);
        var byPos=new HashMap<Long,BlockEntity>();for(var be:devices)byPos.put(be.getBlockPos().asLong(),be);
        var buses=new ArrayList<StorageBusPart>();
        for(var be:devices) {
            if(be instanceof InterfaceBlockEntity iface)require(iface.getInterfaceLogic().getRequestedJobs().isEmpty(),"ACTIVE_INTERFACE_JOB");
            if(!(be instanceof CableBusBlockEntity host))continue;
            for(var side:Direction.values()) {
                var part=host.getPart(side);
                if(part instanceof InterfacePart iface)require(iface.getInterfaceLogic().getRequestedJobs().isEmpty(),"ACTIVE_INTERFACE_JOB");
                if(part instanceof ExportBusPart bus)require(bus.getRequestedJobs().isEmpty(),"ACTIVE_EXPORT_JOB");
                if(part instanceof IOBusPart bus)require(bus.getConfigManager().getSetting(Settings.REDSTONE_CONTROLLED)==RedstoneMode.IGNORE,"EXTERNAL_REDSTONE_CONTROL");
                if(part instanceof StorageBusPart || part instanceof IOBusPart) {
                    var target=byPos.get(be.getBlockPos().relative(side).asLong());
                    require(logic(target,side.getOpposite())!=null || CapturedInventories.supported(target) || NativeMachines.buffered(target) || CapturedPipes.supported(target),"UNSUPPORTED_INTERNAL_BUS_TARGET");
                }
                if(part instanceof StorageBusPart bus)buses.add(bus);
            }
        }
        require(!cyclic(buses,byPos),"CYCLIC_INTERNAL_STORAGE");
    }
    private static boolean cyclic(List<StorageBusPart> buses,Map<Long,BlockEntity> byPos) {
        var graph=new IdentityHashMap<IGrid,Set<IGrid>>();
        for(var bus:buses) {
            var target=byPos.get(bus.getBlockEntity().getBlockPos().relative(bus.getSide()).asLong());
            var logic=logic(target,bus.getSide().getOpposite());
            var node=interfaceNode(target,bus.getSide().getOpposite());var from=bus.getGridNode();
            if(logic==null || !logic.getConfig().isEmpty() || node==null || from==null)continue;
            graph.computeIfAbsent(from.getGrid(),key->Collections.newSetFromMap(new IdentityHashMap<>())).add(node.getGrid());
        }
        var visiting=Collections.newSetFromMap(new IdentityHashMap<IGrid,Boolean>());
        var done=Collections.newSetFromMap(new IdentityHashMap<IGrid,Boolean>());
        for(var grid:graph.keySet())if(cycle(grid,graph,visiting,done))return true;
        return false;
    }
    private static boolean cycle(IGrid grid,Map<IGrid,Set<IGrid>> graph,Set<IGrid> visiting,Set<IGrid> done) {
        if(done.contains(grid))return false;if(!visiting.add(grid))return true;
        for(var next:graph.getOrDefault(grid,Set.of()))if(cycle(next,graph,visiting,done))return true;
        visiting.remove(grid);done.add(grid);return false;
    }
    public CapturedNetworkDevices(ServerLevel level,CompoundTag blueprint,CompoundTag saved,Runnable persist,
                                  java.util.function.Consumer<Runnable> atomic,Map<Long,IGridNode> providers) {
        this.atomic=Objects.requireNonNull(atomic);
        this.providers=Map.copyOf(providers);
        try {
            inventories=new CapturedInventories(level,blueprint,saved.getCompound("inventories"),()->{if(ready && !closed)persist.run();},this::pipeEndpoint);
            var nativePositions=new HashSet<Long>();
            for(var value:blueprint.getCompound("network").getList("nodes",Tag.TAG_COMPOUND)) {
                var node=(CompoundTag)value;if(node.getString("kind").equals("native"))nativePositions.add(node.getLong("pos"));
            }
            for(var value:blueprint.getList("blocks",Tag.TAG_COMPOUND)) {
                var record=(CompoundTag)value;var pos=BlockPos.of(record.getLong("pos"));
                if(!nativePositions.contains(pos.asLong()))continue;
                var state=NbtUtils.readBlockState(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK),record.getCompound("state"));
                if(!(state.getBlock() instanceof EntityBlock factory))continue;
                var template=factory.newBlockEntity(pos,state);
                BlockEntity be;
                if(NativeAssemblers.supports(template)) {
                    var assembler=new CapturedAssembler(template.getType(),pos,state,persist,this.atomic,()->ready && !closed,
                            (target,side)->inventories.at(target,side));
                    assemblers.add(assembler);be=assembler;
                } else if(template instanceof InterfaceBlockEntity && template.getClass()==InterfaceBlockEntity.class) {
                    be=new InterfaceBlockEntity(template.getType(),pos,state) {
                        @Override public void saveChanges(){if(ready && !closed)persist.run();}
                        @Override public void markForUpdate(){}
                    };
                } else if(template instanceof CableBusBlockEntity)be=new CableBusBlockEntity(template.getType(),pos,state) {
                    @Override public void markForSave(){if(ready && !closed)persist.run();}
                    @Override public void markForUpdate(){}
                    @Override public void notifyNeighbors(){}
                    @Override public void partChanged(){}
                };
                else throw new IllegalArgumentException("UNSUPPORTED_CAPTURED_NODE");
                be.setLevel(level);var data=saved.contains(Long.toString(pos.asLong()))?saved.getCompound(Long.toString(pos.asLong())):record.getCompound("data");
                if(be instanceof CapturedAssembler assembler)assembler.loadCaptured(data);else PlatformData.loadBlock(be,data);
                require(be instanceof InterfaceBlockEntity || supported(be),"UNSUPPORTED_CAPTURED_PART");blocks.put(pos.asLong(),be);
            }
            for(var be:blocks.values()) {
                if(be instanceof InterfaceBlockEntity iface) {
                    iface.getMainNode().setInWorldNode(false);interfaces.add(iface);
                } else if(be instanceof CableBusBlockEntity host) {
                    parts.add((AEBasePart)host.getPart(null));
                    for(var side:Direction.values())if(host.getPart(side) instanceof AEBasePart part)parts.add(part);
                }
            }
            for(var part:parts) {
                part.getMainNode().setInWorldNode(false);
                if(part instanceof QuartzFiberPart) ((IManagedGridNode)field(QuartzFiberPart.class,part,"outerNode",null,false)).setInWorldNode(false);
                if(part instanceof StorageBusPart bus) {
                    storageBuses.add(bus);
                    field(StorageBusPart.class,bus,"adjacentStorageAccessor",CapturedStorageAccess.create(bus,()->exposed(bus)),true);
                } else if(part instanceof ImportBusPart bus)field(ImportBusPart.class,bus,"importStrategy",(StackImportStrategy)context->importItems(bus,context),true);
                else if(part instanceof ExportBusPart bus)field(ExportBusPart.class,bus,"exportStrategy",new InternalExport(bus),true);
            }
            for(var iface:interfaces)iface.getMainNode().create(level,null);
            for(var assembler:assemblers)assembler.getMainNode().create(level,null);
            for(var part:parts)part.addToWorld();
            ready=true;
        } catch(RuntimeException failure){close();throw failure;}
    }
    private static Object field(Class<?> type,Object object,String name,Object value,boolean write) {
        try {var field=type.getDeclaredField(name);field.setAccessible(true);if(write)field.set(object,value);return field.get(object);}
        catch(ReflectiveOperationException e){throw new IllegalStateException("AE2 internal device contract changed: "+name,e);}
    }
    private InterfaceLogic target(AEBasePart part) {
        var be=blocks.get(part.getBlockEntity().getBlockPos().relative(part.getSide()).asLong());
        var node=interfaceNode(be,part.getSide().getOpposite());
        return node!=null && node.isActive()?logic(be,part.getSide().getOpposite()):null;
    }
    private MEStorage pipeEndpoint(long pos,Direction side) {
        if(!ready || closed)return null;
        if(blocks.get(pos) instanceof CapturedAssembler assembler)return assembler.storage();
        var provider=providers.get(pos);
        if(provider!=null && provider.getOwner() instanceof FactorySession session && session.assemblyReturns()!=null)return session.assemblyReturns();
        var be=blocks.get(pos);var node=interfaceNode(be,side);
        var iface=node!=null && node.isActive()?logic(be,side):null;
        // A native pipe sees the Interface's local material buffer, not its entire ME Grid.
        return iface==null?null:iface.getStorage();
    }
    private MEStorage exposed(StorageBusPart bus) {
        if(closed || cyclic(storageBuses,blocks))return null;
        var target=targetStorage(bus,true);if(target==null)return null;
        return new MEStorage() {
            private MEStorage current(){return closed || cyclic(storageBuses,blocks)?null:targetStorage(bus,true);}
            @Override public long insert(AEKey key,long amount,Actionable mode,IActionSource source){var s=current();return s==null?0:s.insert(key,amount,mode,source);}
            @Override public long extract(AEKey key,long amount,Actionable mode,IActionSource source){var s=current();return s==null?0:s.extract(key,amount,mode,source);}
            @Override public void getAvailableStacks(KeyCounter out){var s=current();if(s!=null)s.getAvailableStacks(out);}
            @Override public net.minecraft.network.chat.Component getDescription(){return target.getDescription();}
        };
    }
    private MEStorage targetStorage(AEBasePart part,boolean exposeNetwork) {
        if(closed)return null;
        var iface=target(part);
        if(iface!=null)return exposeNetwork?iface.getInventory():iface.getStorage();
        return inventories.at(part.getBlockEntity().getBlockPos().relative(part.getSide()).asLong(),part.getSide().getOpposite());
    }
    public void tick() {
        atomic.accept(inventories::tick);
        boolean cycle=cyclic(storageBuses,blocks);
        for(var bus:storageBuses) {
            var be=blocks.get(bus.getBlockEntity().getBlockPos().relative(bus.getSide()).asLong());
            var target=interfaceNode(be,bus.getSide().getOpposite());var from=bus.getGridNode();
            var state=new RouteState(from==null?null:from.getGrid(),target==null?null:target.getGrid(),targetStorage(bus,true)!=null,cycle);
            if(!state.equals(routes.put(bus,state)))bus.onNeighborChanged(bus.getLevel(),bus.getBlockEntity().getBlockPos(),bus.getBlockEntity().getBlockPos().relative(bus.getSide()));
        }
    }
    private boolean importItems(ImportBusPart bus,StackTransferContext context) {
        atomic.accept(()->importItemsNow(bus,context));
        return false;
    }
    private void importItemsNow(ImportBusPart bus,StackTransferContext context) {
        var local=targetStorage(bus,false);if(local==null)return;
        var stock=new KeyCounter();local.getAvailableStacks(stock);
        for(var entry:stock) {
            if(!context.hasOperationsLeft())break;
            var key=entry.getKey();if(!context.isKeyTypeEnabled(key.getType()) || context.isInFilter(key)==context.isInverted())continue;
            long limit=(long)context.getOperationsRemaining()*key.getType().getAmountPerOperation();
            var network=context.getInternalStorage().getInventory();var source=context.getActionSource();
            long accepted=network.insert(key,Math.min(limit,entry.getLongValue()),Actionable.SIMULATE,source);
            long extracted=local.extract(key,accepted,Actionable.MODULATE,source);
            long inserted=network.insert(key,extracted,Actionable.MODULATE,source);
            if(inserted<extracted)require(local.insert(key,extracted-inserted,Actionable.MODULATE,source)==extracted-inserted,"INTERNAL_IMPORT_ROLLBACK");
            if(inserted>0)context.reduceOperationsRemaining(Math.max(1,inserted/key.getType().getAmountPerOperation()));
        }
    }
    private long mutate(java.util.function.LongSupplier action) {
        var result=new long[1];atomic.accept(()->result[0]=action.getAsLong());return result[0];
    }
    private final class InternalExport implements StackExportStrategy {
        private final ExportBusPart bus;
        InternalExport(ExportBusPart bus){this.bus=bus;}
        @Override public long push(AEKey key,long amount,Actionable mode) {
            var target=targetStorage(bus,false);
            if(target==null)return 0;
            return mode==Actionable.SIMULATE?target.insert(key,amount,mode,IActionSource.ofMachine(bus))
                    :mutate(()->target.insert(key,amount,mode,IActionSource.ofMachine(bus)));
        }
        @Override public long transfer(StackTransferContext context,AEKey key,long amount) {
            return mutate(()->transferNow(context,key,amount));
        }
        private long transferNow(StackTransferContext context,AEKey key,long amount) {
            var network=context.getInternalStorage().getInventory();var source=context.getActionSource();
            long available=StorageHelper.poweredExtraction(context.getEnergySource(),network,key,amount,source,Actionable.SIMULATE);
            long accepted=push(key,available,Actionable.SIMULATE);if(accepted==0)return 0;
            long extracted=StorageHelper.poweredExtraction(context.getEnergySource(),network,key,accepted,source,Actionable.MODULATE);
            long inserted=push(key,extracted,Actionable.MODULATE);
            if(inserted<extracted)require(network.insert(key,extracted-inserted,Actionable.MODULATE,source)==extracted-inserted,"INTERNAL_EXPORT_ROLLBACK");
            return inserted;
        }
    }
    public IGridNode node(long pos,String port){var be=blocks.get(pos);return be==null?null:ports(be).get(port);}
    public CapturedAssembler assembler(long pos){return blocks.get(pos) instanceof CapturedAssembler a?a:null;}
    public BufferedMachine machine(long pos){return inventories.machine(pos);}
    public void configureFurnace(long pos,java.math.BigInteger count){atomic.accept(()->inventories.configureFurnace(pos,count));}
    public boolean furnacesRestorable(){return inventories.furnacesRestorable();}
    public void tickNative(long pos){atomic.accept(()->inventories.tickNative(pos));}
    public void tickNative(long pos,java.math.BigInteger speed){atomic.accept(()->inventories.tickNative(pos,speed));}
    public void eject(Map<Long,IGridNode> providers,Map<Long,IGridNode> passiveOwners) {
        atomic.accept(()->ejectNow(providers,passiveOwners));
    }
    private void ejectNow(Map<Long,IGridNode> providers,Map<Long,IGridNode> passiveOwners) {
        for(var entry:inventories.machines().entrySet()) {
            var machine=entry.getValue();if(!machine.ejecting())continue;
            var pos=BlockPos.of(entry.getKey());
            IGridNode owner=passiveOwners.get(entry.getKey());
            for(var side:Direction.values())if(providers.containsKey(pos.relative(side).asLong()))owner=providers.get(pos.relative(side).asLong());
            if(owner==null || !owner.isActive() || !(owner.getOwner() instanceof appeng.api.networking.security.IActionHost host))continue;
            var source=IActionSource.ofMachine(host);
            for(var side:Direction.values()) {
                long neighbor=pos.relative(side).asLong();MEStorage target=null;
                var provider=providers.get(neighbor);
                if(provider!=null && provider.isActive())target=provider.getGrid().getStorageService().getInventory();
                else {
                    var logic=logic(blocks.get(neighbor),side.getOpposite());
                    var node=interfaceNode(blocks.get(neighbor),side.getOpposite());
                    if(logic!=null && node!=null && node.isActive())target=logic.getInventory();
                    if(target==null)target=inventories.at(neighbor,side.getOpposite());
                }
                if(target==null)continue;
                var output=machine.storage(side);var stock=new KeyCounter();output.getAvailableStacks(stock);
                for(var stack:stock) {
                    if(!machine.ejecting(stack.getKey(),side))continue;
                    long available=output.extract(stack.getKey(),stack.getLongValue(),Actionable.SIMULATE,source);
                    long accepted=ejectInto(target,machine,stack.getKey(),available,Actionable.SIMULATE,source);
                    if(accepted<=0)continue;
                    long inserted=ejectInto(target,machine,stack.getKey(),accepted,Actionable.MODULATE,source);
                    require(inserted>=0 && inserted<=accepted && output.extract(stack.getKey(),inserted,Actionable.MODULATE,source)==inserted,"NATIVE_EJECTION_MISMATCH");
                }
            }
        }
    }
    private static long ejectInto(MEStorage target,BufferedMachine machine,AEKey key,long amount,Actionable mode,IActionSource source) {
        var color=machine.transportColor();
        return color!=null && target instanceof CapturedPipes.ColoredItemInput pipe
                ?pipe.insertColored(key,amount,mode,color):target.insert(key,amount,mode,source);
    }
    public CompoundTag save(){var result=new CompoundTag();for(var entry:blocks.entrySet())result.put(Long.toString(entry.getKey()),entry.getValue() instanceof CapturedAssembler assembler?assembler.saveCaptured():PlatformData.saveBlock(entry.getValue()));result.put("inventories",inventories.save());return result;}
    public void restore(CompoundTag blueprint){for(var assembler:assemblers)assembler.trimIdleWorkers();var saved=save();for(var value:blueprint.getList("blocks",Tag.TAG_COMPOUND)){var block=(CompoundTag)value;var key=Long.toString(block.getLong("pos"));if(saved.contains(key))block.put("data",saved.getCompound(key));}inventories.restore(blueprint);}
    @Override public void close(){if(closed)return;closed=true;for(var part:parts)part.removeFromWorld();for(var iface:interfaces)iface.getMainNode().destroy();for(var assembler:assemblers)assembler.getMainNode().destroy();}
}
