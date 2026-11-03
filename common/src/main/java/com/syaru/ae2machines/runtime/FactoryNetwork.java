package com.syaru.ae2machines.runtime;

import appeng.api.networking.*;
import appeng.api.parts.IPartHost;
import appeng.api.storage.*;
import appeng.api.storage.cells.StorageCell;
import appeng.api.util.AEColor;
import com.syaru.ae2machines.platform.*;
import com.syaru.ae2machines.world.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import static com.syaru.ae2machines.world.NativeFactoryCompiler.require;

/** AE2 owns pathing, storage capacity/filters and channel allocation. No copy per tick or per Q lane. */
public final class FactoryNetwork implements AutoCloseable {
    private final ServerLevel level;
    private final FactoryJournal journal;
    private final Map<Long,IGridNode> nodes=new HashMap<>();
    private final List<Device> devices=new ArrayList<>();
    private final List<Link> links=new ArrayList<>();
    private CapturedNetworkDevices nativeDevices;
    private ListTag overlays=new ListTag();
    private boolean closed,ready;
    private final Map<Long,IGridNode> providers;
    private final Map<Long,IGridNode> passiveOwners=new HashMap<>();
    private int ejectDelay;
    public FactoryNetwork(ServerLevel level,FactoryJournal journal,Map<Long,IGridNode> providers) {
        this.level=level;this.journal=journal;
        this.providers=Map.copyOf(providers);
        try {
            var record=journal.read();var blueprint=record.getCompound("blueprint");
            var epoch=FactoryRecovery.epoch(level.getServer());
            require(!record.hasUUID("networkEpoch") || epoch.permits(record.getUUID("networkEpoch"))
                    || InternalRecovery.permits(level,record) || ExternalRecovery.permits(level,record),"INTERNAL_STORAGE_REQUIRES_RECONCILIATION");
            for(var value:blueprint.getList("blocks",Tag.TAG_COMPOUND)) {
                var pos=BlockPos.of(((CompoundTag)value).getLong("pos"));
                require(level.hasChunkAt(pos) && level.getBlockState(pos).isAir(),"CAPTURED_SPACE_CHANGED_OR_UNLOADED");
            }
            var topology=blueprint.getCompound("network");
            overlays=topology.getList("overlays",Tag.TAG_COMPOUND);
            if(topology.getInt("version")>=2)nativeDevices=new CapturedNetworkDevices(level,blueprint,record.getCompound("networkNative"),this::persist,journal::atomic,providers);
            var savedCells=journal.read().getCompound("networkCells");
            for(var value:topology.getList("nodes",Tag.TAG_COMPOUND)) {
                var data=(CompoundTag)value;long pos=data.getLong("pos");
                long id=data.contains("id")?data.getLong("id"):pos;
                if(data.getString("kind").equals("provider")) {require(providers.containsKey(pos),"MISSING_CAPTURED_PROVIDER");nodes.put(id,providers.get(pos));}
                else if(data.getString("kind").equals("native")) {
                    var node=nativeDevices.node(pos,data.getString("port"));require(node!=null,"MISSING_NATIVE_NODE");nodes.put(id,node);
                }
                else {
                    var device=new Device(data,savedCells);devices.add(device);device.node.create(level,null);
                    require(device.node.getNode().getMaxChannels()==data.getInt("channels"),"CHANNEL_CAPACITY_CHANGED");
                    require(nodes.put(id,device.node.getNode())==null,"DUPLICATE_INTERNAL_NODE");
                }
            }
            for(var value:topology.getList("edges",Tag.TAG_COMPOUND)) {
                var edge=(CompoundTag)value;GridHelper.createConnection(Objects.requireNonNull(nodes.get(edge.getLong("a"))),Objects.requireNonNull(nodes.get(edge.getLong("b"))));
            }
            for(var value:topology.getList("boundaries",Tag.TAG_COMPOUND))links.add(new Link((CompoundTag)value));
            ready=true;persist();
        } catch(RuntimeException e) { close();throw e; }
    }
    public void tick() {
        if(closed)return;
        for(var link:links)link.connect();
        if(nativeDevices!=null) {
            nativeDevices.tick();
            if(ejectDelay==0){nativeDevices.eject(providers,passiveOwners);ejectDelay=10;}else ejectDelay--;
        }
    }
    public boolean online(IGridNode provider) {
        if(closed || !provider.isActive())return false;
        var reachable=Collections.newSetFromMap(new IdentityHashMap<IGrid,Boolean>());reachable.add(provider.getGrid());
        boolean changed;do {changed=false;for(var value:overlays){var edge=(CompoundTag)value;var a=nodes.get(edge.getLong("a"));var b=nodes.get(edge.getLong("b"));if(a==null || b==null)continue;
            if(reachable.contains(a.getGrid()))changed|=reachable.add(b.getGrid());if(reachable.contains(b.getGrid()))changed|=reachable.add(a.getGrid());}}while(changed);
        return links.stream().anyMatch(link->link.connected!=null && reachable.contains(link.connected.getGrid()) && link.tunnelActive());
    }
    public void persist() {
        if(!ready || closed)return;
        if(journal.deferCheckpoint(this,this::persist))return;
        var record=journal.read();snapshot(record);journal.write(record);
    }
    public BufferedMachine machine(long pos){return nativeDevices==null?null:nativeDevices.machine(pos);}
    public void configureFurnace(long pos,java.math.BigInteger count){if(nativeDevices!=null)nativeDevices.configureFurnace(pos,count);}
    public boolean furnacesRestorable(){return nativeDevices==null || nativeDevices.furnacesRestorable();}
    public CapturedAssembler assembler(long pos){return nativeDevices==null?null:nativeDevices.assembler(pos);}
    public boolean assemblerBusy(long pos){
        var be=assembler(pos);if(be==null)return false;
        if(be.parallelBusy())return true;
        if(PlatformData.saveBlock(be).contains("myPlan"))return true;
        // CPU completion can precede the assembler's container-return drain.
        if(be.acceptsPlans())for(int slot=0;slot<10;slot++)if(!be.getInternalInventory().getStackInSlot(slot).isEmpty())return true;
        return false;
    }
    public void tickNative(long pos){if(nativeDevices!=null)nativeDevices.tickNative(pos);}
    public void tickNative(long pos,java.math.BigInteger speed){if(nativeDevices!=null)nativeDevices.tickNative(pos,speed);}
    public void bindPassive(long machine,IGridNode node,long anchor) {
        var target=nodes.get(anchor);require(target!=null,"MISSING_PASSIVE_GRID_ANCHOR");GridHelper.createConnection(node,target);
        passiveOwners.put(machine,node);
    }
    public boolean owns(IGridNode node){return nodes.containsValue(node);}
    public void snapshot(CompoundTag record) {
        if(!ready || closed)return;
        var cells=new CompoundTag();
        for(var device:devices)if(device.drive) {
            var list=new ListTag();
            for(var cell:device.cells) {
                cell.inventory.persist();var saved=new CompoundTag();saved.putInt("slot",cell.slot);
                saved.put("item",PlatformData.saveItem(cell.stack,level));list.add(saved);
            }
            cells.put(Long.toString(device.pos),list);
        }
        record.put("networkCells",cells);if(nativeDevices!=null)record.put("networkNative",nativeDevices.save());record.putUUID("networkEpoch",FactoryRecovery.epoch(level.getServer()).current());
    }
    public void restoreCellsToBlueprint() {
        persist();var record=journal.read();var blueprint=record.getCompound("blueprint");
        for(var device:devices)if(device.drive)for(var value:blueprint.getList("blocks",Tag.TAG_COMPOUND)) {
            var block=(CompoundTag)value;if(block.getLong("pos")!=device.pos)continue;
            var state=NbtUtils.readBlockState(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK),block.getCompound("state"));
            var be=(appeng.blockentity.storage.DriveBlockEntity)((net.minecraft.world.level.block.EntityBlock)state.getBlock()).newBlockEntity(BlockPos.of(device.pos),state);
            require(be!=null,"MISSING_DRIVE");be.setLevel(level);PlatformData.loadBlock(be,block.getCompound("data"));
            for(var cell:device.cells)be.getInternalInventory().setItemDirect(cell.slot,cell.stack.copy());
            block.put("data",PlatformData.saveBlock(be));
        }
        if(nativeDevices!=null)nativeDevices.restore(blueprint);journal.write(record);
    }
    @Override public void close() { if(closed)return;closed=true;if(nativeDevices!=null)nativeDevices.close();for(var device:devices)device.node.destroy();for(var link:links)link.disconnect(); }
    private record Cell(int slot,ItemStack stack,StorageCell inventory) {}
    private final class Device implements IStorageProvider {
        final long pos;final boolean drive;final int priority;
        final List<Cell> cells=new ArrayList<>();final IManagedGridNode node;
        Device(CompoundTag data,CompoundTag savedCells) {
            pos=data.getLong("pos");drive=data.getString("kind").equals("drive");priority=data.getInt("priority");
            var flags=new ArrayList<GridFlags>();for(var flag:data.getList("flags",Tag.TAG_STRING))flags.add(GridFlags.valueOf(flag.getAsString()));
            node=GridHelper.createManagedNode(this,new IGridNodeListener<Device>() {
                @Override public void onSaveChanges(Device owner,IGridNode ignored) {}
                @Override public void onStateChanged(Device owner,IGridNode ignored,State reason) { if(owner.drive)IStorageProvider.requestUpdate(owner.node); }
            }).setInWorldNode(false).setFlags(flags.toArray(GridFlags[]::new)).setGridColor(AEColor.valueOf(data.getString("color")))
                    .setIdlePowerUsage(data.getDouble("idleAE"));
            node.setOwningPlayerId(data.getInt("owner"));
            if(drive) {
                var list=savedCells.contains(Long.toString(pos))?savedCells.getList(Long.toString(pos),Tag.TAG_COMPOUND):data.getList("cells",Tag.TAG_COMPOUND);
                var slots=new HashSet<Integer>();
                for(var value:list) {
                    var cell=(CompoundTag)value;int slot=cell.getInt("slot");require(slot>=0 && slot<10 && slots.add(slot),"INVALID_CELL_SLOT");
                    var stack=PlatformData.loadItem(cell.getCompound("item"),level);var inventory=StorageCells.getCellInventory(stack,FactoryNetwork.this::persist);
                    require(inventory!=null,"MISSING_CELL_ADAPTER");cells.add(new Cell(slot,stack,inventory));
                }
                node.addService(IStorageProvider.class,this);
            }
        }
        @Override public void mountInventories(IStorageMounts mounts) {
            if(node.isOnline())for(var cell:cells)mounts.mount(cell.inventory,priority);
        }
    }
    private final class Link {
        final CompoundTag data;IGridNode connected;IGridConnection connection;
        Link(CompoundTag data){this.data=data;}
        VirtualP2PPart tunnel() {
            var pos=BlockPos.of(data.getLong("boundary"));
            if(!level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof IPartHost host))return null;
            var part=host.getPart(Direction.byName(data.getString("side")));
            return part instanceof VirtualP2PPart p && p.isOutput() && p.getFrequency()==data.getShort("frequency")?p:null;
        }
        boolean tunnelActive(){var tunnel=tunnel();return tunnel!=null && tunnel.getMainNode().isActive() && tunnel.getExternalFacingNode()==connected;}
        void connect() {
            var tunnel=tunnel();var next=tunnel==null?null:tunnel.getExternalFacingNode();
            if(next==connected)return;disconnect();
            if(next!=null){connection=GridHelper.createConnection(Objects.requireNonNull(nodes.get(data.getLong("node"))),next);connected=next;}
        }
        void disconnect(){if(connection!=null)connection.destroy();connection=null;connected=null;}
    }
}
