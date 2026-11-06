package com.syaru.ae2machines.runtime;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.*;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionHost;
import appeng.api.parts.IPartHost;
import appeng.api.stacks.*;
import appeng.me.helpers.MachineSource;
import com.syaru.ae2machines.block.BaseAccelerator;
import com.syaru.ae2machines.engine.*;
import com.syaru.ae2machines.platform.*;
import com.syaru.ae2machines.world.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import java.math.BigInteger;
import java.util.*;
import static com.syaru.ae2machines.world.NativeFactoryCompiler.require;

/** One real AE2 node per captured provider, never one per quantum lane. */
public class FactorySession implements CountedCraftingProvider,IActionHost,FactoryHandle {
    protected final BaseAccelerator host;
    private final ServerLevel level;
    private final FactoryJournal journal;
    private final CompoundTag blueprint;
    private final int laneIndex;
    private final List<FactorySession> children=new ArrayList<>();
    private List<FactorySession> powerLanes=List.of(this);
    private FactorySession handoffOwner=this;
    private FactoryNetwork network;
    private boolean ownsNetwork;
    private final IManagedGridNode node;
    private PatternProgram program;
    private final MachineProfile profile;
    private final ProcessingRuntime runtime;
    private final NativeEnergyInput energyInput;
    private IGridConnection connection;
    private IGridNode connectedBoundary;
    private UUID normalJob;
    private UUID normalEpoch;
    private boolean acoBatchJob,acoSourceCommitted;
    private final Map<UUID,CompoundTag> batchReceipts=new LinkedHashMap<>();
    private Map<Recipe<AEKey>,NativeBatchProcessor> preparedRecipes=Map.of();
    private final Map<AEKey,BigInteger> delivered=new LinkedHashMap<>();
    private boolean deliveryIntent,inputIntent,closed;
    private boolean bufferedJob;
    private boolean directNativeWork;
    private boolean vanillaTicked;
    private boolean nativeAssembler;
    private AssemblyProvider assemblyProvider;
    private NativeCompletion completion;
    private long lastReconnect=Long.MIN_VALUE;
    private long recipeRevision=FactoryRecipes.revision();
    public static final class SourceChunkUnavailable extends RuntimeException {
        private SourceChunkUnavailable(){super("CAPTURED_CHUNK_UNLOADED");}
    }
    public static FactorySession open(BaseAccelerator host,UUID id) {
        var level=(ServerLevel)host.getLevel();var journal=FactoryJournal.open(level,id);
        for(var value:journal.read().getCompound("blueprint").getList("blocks",Tag.TAG_COMPOUND))
            if(!level.hasChunkAt(BlockPos.of(((CompoundTag)value).getLong("pos"))))throw new SourceChunkUnavailable();
        var saved=journal.read();var epoch=FactoryRecovery.epoch(level.getServer());
        if(saved.hasUUID("networkEpoch") && !epoch.permits(saved.getUUID("networkEpoch"))) {
            if(ExternalRecovery.waitingForChunks(level,saved))throw new SourceChunkUnavailable();
            require(InternalRecovery.permits(level,saved) || ExternalRecovery.permits(level,saved),"INTERNAL_STORAGE_REQUIRES_RECONCILIATION"
                    +(saved.contains("externalCheckpoint")?":"+ExternalRecovery.failure(level,saved):""));
        }
        var result=new FactorySession[1];
        try {
          journal.atomic(()->{
            var root=openLane(host,id,0,journal);result[0]=root;
            int count=NativeFactoryCompiler.lanes(root.journal.read().getCompound("blueprint")).size();
            for(int i=1;i<count;i++){var child=openLane(host,id,i,root.journal);child.handoffOwner=root;root.children.add(child);}
            var lanes=new ArrayList<FactorySession>();lanes.add(root);lanes.addAll(root.children);root.powerLanes=List.copyOf(lanes);
            if(root.journal.read().getCompound("blueprint").getInt("schema")==3) {
                var providers=new HashMap<Long,IGridNode>();if(!root.blueprint.getBoolean("passive"))providers.put(root.blueprint.getLong("provider"),root.node.getNode());
                for(var child:root.children)if(!child.blueprint.getBoolean("passive"))providers.put(child.blueprint.getLong("provider"),child.node.getNode());
                root.network=new FactoryNetwork(root.level,root.journal,providers);root.ownsNetwork=true;
                if(root.blueprint.getBoolean("passive"))root.network.bindPassive(root.blueprint.getLong("machine"),root.node.getNode(),root.blueprint.getLong("anchor"));
                for(var child:root.children){child.network=root.network;if(child.blueprint.getBoolean("passive"))root.network.bindPassive(child.blueprint.getLong("machine"),child.node.getNode(),child.blueprint.getLong("anchor"));}
                root.bindWork();for(var child:root.children)child.bindWork();
            }
          });
          ExternalRecovery.register(level,result[0]);return result[0];
        } catch(RuntimeException e) { if(result[0]!=null)result[0].close();throw e; }
    }
    private static FactorySession openLane(BaseAccelerator host,UUID id,int lane,FactoryJournal shared) {
        if(!com.syaru.ae2machines.compat.OptionalAco.available()) return new FactorySession(host,id,lane,shared);
        try {
            return (FactorySession)Class.forName("com.syaru.ae2machines.compat.aco.AcoFactorySession")
                    .getConstructor(BaseAccelerator.class,UUID.class,int.class,FactoryJournal.class).newInstance(host,id,lane,shared);
        } catch(java.lang.reflect.InvocationTargetException e) {
            if(e.getCause() instanceof RuntimeException error) throw error;
            throw new IllegalStateException(e.getCause());
        } catch(ReflectiveOperationException e) { throw new IllegalStateException("ACO provider binding failed",e); }
    }

    public FactorySession(BaseAccelerator host,UUID id) { this(host,id,0); }
    public FactorySession(BaseAccelerator host,UUID id,int laneIndex) {
        this(host,id,laneIndex,null);
    }
    public FactorySession(BaseAccelerator host,UUID id,int laneIndex,FactoryJournal shared) {
        this.host=host;this.laneIndex=laneIndex;level=(ServerLevel)host.getLevel();journal=shared==null?FactoryJournal.open(level,id):shared;
        var record=journal.read();blueprint=NativeFactoryCompiler.lanes(record.getCompound("blueprint")).get(laneIndex);
        require(blueprint.getString("dimension").equals(level.dimension().location().toString()),"WRONG_DIMENSION");
        String state=record.getString("state");
        require(state.equals("CAPTURED") || state.equals("ACTIVE") && record.getLong("accelerator")==host.getBlockPos().asLong(),"FACTORY_ALREADY_OWNED");
        for(var entry:blueprint.getList("blocks",Tag.TAG_COMPOUND)) {
            var pos=BlockPos.of(((CompoundTag)entry).getLong("pos"));
            require(level.hasChunkAt(pos) && level.getBlockState(pos).isAir(),"CAPTURED_SPACE_CHANGED_OR_UNLOADED");
        }
        var compiled=NativeFactoryCompiler.rebuild(level,blueprint,this::online);
        program=compiled.program();profile=compiled.energy();
        var flags=blueprint.getList("flags",Tag.TAG_STRING);
        var flagList=new ArrayList<GridFlags>();for(var flag:flags) flagList.add(GridFlags.valueOf(flag.getAsString()));
        node=GridHelper.createManagedNode(this,new IGridNodeListener<FactorySession>() {
            @Override public void onSaveChanges(FactorySession owner,IGridNode ignored) {}
        }).setInWorldNode(false).setFlags(flagList.toArray(GridFlags[]::new))
                .setIdlePowerUsage(blueprint.getDouble("idleAE"));
        if(!blueprint.getBoolean("passive"))node.addService(ICraftingProvider.class,this);
        node.setOwningPlayerId(blueprint.getInt("owner"));
        var lane=readLane(record);
        var epoch=FactoryRecovery.epoch(level.getServer());
        boolean localRecovery=(record.hasUUID("networkEpoch") && !epoch.permits(record.getUUID("networkEpoch"))
                || lane.hasUUID("normalEpoch") && !epoch.permits(lane.getUUID("normalEpoch")))
                && (InternalRecovery.permits(level,record) || ExternalRecovery.permits(level,record));
        runtime=lane.contains("runtime") ? RuntimeCodec.read(lane.getCompound("runtime"),key->PlatformData.loadKey(key,level),r->persist())
                :new ProcessingRuntime(new MeteredEnergy(profile.energyUnit(),profile.capacity(),profile.inputRate(),BigInteger.valueOf(profile.voltage()),profile.stored(),false),r->persist());
        require(runtime.energy().unit().equals(profile.energyUnit())
                && runtime.energy().capacity().equals(profile.capacity())
                && runtime.energy().maxInput().equals(profile.inputRate())
                && !runtime.energy().limitPerTick(),"SAVED_MACHINE_ENERGY_CONFIGURATION_CHANGED");
        bindNative(compiled);
        energyInput=profile.energyUnit().equals(MachineProfile.MEK_UNIT)
                ? MekanismAdapter.energyInput(NativePowerReceiver.demand(runtime,profile.unitsPerFE().divide(profile.unitsPerFE().gcd(profile.nativeQuantum())).multiply(profile.nativeQuantum())),level::getGameTime)
                : new NativeEnergyInput() { public int receive(int n,long t,boolean s){return 0;}public int stored(){return 0;}public int capacity(){return 0;} };
        if(lane.contains("normalJob")) {
            normalJob=lane.getUUID("normalJob");
            acoBatchJob=lane.getBoolean("acoBatchJob");acoSourceCommitted=lane.getBoolean("acoSourceCommitted");
            boolean targetOwned=acoBatchJob && (acoSourceCommitted || lane.getCompound("batchReceipts").contains(normalJob.toString()));
            normalEpoch=lane.hasUUID("normalEpoch")?lane.getUUID("normalEpoch"):null;
            if(normalEpoch==null || !epoch.permits(normalEpoch) && !localRecovery && !targetOwned || lane.getBoolean("deliveryIntent") || lane.getBoolean("inputIntent"))
                runtime.restoreQuarantine(true);
            else normalEpoch=epoch.current();
        }
        for(var entry:lane.getList("delivered",Tag.TAG_COMPOUND)) {
            var saved=(CompoundTag)entry;
            var key=PlatformData.loadKey(saved.getCompound("key"),level);
            var amount=new BigInteger(saved.getByteArray("amount"));
            require(key!=null && amount.signum()>0 && delivered.put(key,amount)==null,"INVALID_DELIVERY_LEDGER");
        }
        require(normalJob!=null || delivered.isEmpty(),"ORPHAN_DELIVERY_LEDGER");
        if(normalJob!=null) {
            var receipt=runtime.find(normalJob,"ae2-normal").orElseThrow().receipt();
            for(var entry:delivered.entrySet()) require(entry.getValue().compareTo(receipt.getOrDefault(entry.getKey(),BigInteger.ZERO))<=0,"EXCESS_DELIVERY_LEDGER");
        }
        bufferedJob=lane.getBoolean("bufferedJob");
        if(blueprint.contains("gtStructure"))completion=GtMultiblockAdapter.completion(level,blueprint.getCompound("gtStructure"),lane.getCompound("nativeCompletion"));
        if(completion!=null)runtime.completionGate(completion::ready);
        require(!bufferedJob || normalJob!=null,"ORPHAN_BUFFERED_JOB");
        for(var key:lane.getCompound("batchReceipts").getAllKeys()) {
            var receipt=lane.getCompound("batchReceipts").getCompound(key);
            require(receipt.getLong("executions")>0 && !receipt.getString("digest").isBlank() && !receipt.getString("pattern").isBlank(),"INVALID_BATCH_RECEIPT");
            batchReceipts.put(UUID.fromString(key),receipt.copy());
        }
        deliveryIntent=lane.getBoolean("deliveryIntent");inputIntent=lane.getBoolean("inputIntent");runtime.resume(program);
        if(localRecovery)runtime.rebaseRecoveredTime(level.getGameTime());
        if(blueprint.getBoolean("assemblyProvider") || blueprint.getBoolean("furnaceProvider")) {
            var provider=blueprint.getList("blocks",Tag.TAG_COMPOUND).stream().map(CompoundTag.class::cast)
                    .filter(b->b.getLong("pos")==blueprint.getLong("provider")).findFirst().orElseThrow();
            assemblyProvider=new AssemblyProvider(level,provider,lane.getCompound("assemblyProvider"),node,this,
                    this::persist,pos->network==null?null:network.assembler(pos),
                    (pos,side)->{
                        if(!blueprint.getBoolean("furnaceProvider") || pos!=blueprint.getLong("machine") || network==null)return null;
                        var machine=network.machine(pos);return machine==null?null:machine.storage(side);
                    });
        }
        persist();node.create(level,null);
    }
    public UUID id() { return journal.id(); }
    public FactorySession handoffOwner(){return handoffOwner;}
    public BaseAccelerator batchTarget(){return host;}
    public int laneIndex(){return laneIndex;}
    public int preparedRecipeCount(){return preparedRecipes.size();}
    public Optional<FactorySession> batchLane(int index){return index==laneIndex?Optional.of(this):children.stream().filter(s->s.laneIndex==index).findFirst();}
    public long transactionalLimit(IPatternDetails pattern,long offered) {
        if(offered<2 || closed || isBusy() || normalJob!=null || vanillaTicked || nativeAssembler || assemblyProvider!=null || blueprint.getBoolean("blocking"))return 0;
        var recipe=runtime.resolve(pattern);if(recipe.isEmpty())return 0;
        var limit=runtime.appendRoom(recipe.get()).min(BigInteger.valueOf(offered));
        for(var amounts:List.of(recipe.get().inputs(),recipe.get().outputs()))for(var amount:amounts.values())
            limit=limit.min(BigInteger.valueOf(Long.MAX_VALUE).divide(amount));
        return limit.longValueExact();
    }
    public Optional<CompoundTag> batchReceipt(UUID id){return Optional.ofNullable(batchReceipts.get(id)).map(CompoundTag::copy);}
    public boolean acceptTransactional(UUID id,String digest,String fingerprint,IPatternDetails pattern,long count,
            Map<AEKey,BigInteger> inputs,Map<AEKey,BigInteger> outputs) {
        var previous=batchReceipts.get(id);
        if(previous!=null){require(previous.getString("digest").equals(digest) && previous.getString("pattern").equals(fingerprint) && previous.getLong("executions")==count,"BATCH_IDENTITY_CHANGED");return true;}
        if(transactionalLimit(pattern,count)!=count)return false;
        var recipe=runtime.resolve(pattern).orElseThrow();var n=BigInteger.valueOf(count);
        if(!ExactAmounts.multiply(recipe.inputs(),n).equals(inputs) || !ExactAmounts.multiply(recipe.outputs(),n).equals(outputs))return false;
        var epoch=FactoryRecovery.epoch(level.getServer());epoch.requireActive();
        try {
            journal.atomic(()->{
                normalJob=id;normalEpoch=epoch.current();
                require(runtime.accept(id,"ae2-normal",pattern,n,inputs,outputs),"NATIVE_BATCH_ADMISSION_CHANGED");
                acoBatchJob=true;acoSourceCommitted=false;
                var receipt=new CompoundTag();receipt.putString("digest",digest);receipt.putLong("executions",count);
                receipt.putString("pattern",fingerprint);
                batchReceipts.put(id,receipt);persist();
            });
            return true;
        }catch(RuntimeException failure){runtime.restoreQuarantine(true);throw failure;}
    }
    public void forgetBatch(UUID id){if(batchReceipts.remove(id)!=null){if(id.equals(normalJob) && acoBatchJob)acoSourceCommitted=true;persist();}}
    public static Optional<CompoundTag> savedBatch(ServerLevel level,UUID factory,int lane,UUID transaction) {
        var record=FactoryJournal.open(level,factory).read();
        int schema=record.getCompound("blueprint").getInt("schema");
        require(schema>=1 && schema<=3 && record.getCompound("blueprint").getString("dimension").equals(level.dimension().location().toString()),"INVALID_BATCH_FACTORY");
        require(schema==1?lane==0:record.getCompound("runtimeLanes").contains(Integer.toString(lane)),"INVALID_BATCH_LANE");
        var state=record.getCompound("blueprint").getInt("schema")==1?record:record.getCompound("runtimeLanes").getCompound(Integer.toString(lane));
        var receipts=state.getCompound("batchReceipts");
        require(receipts.contains(transaction.toString()) || !state.hasUUID("normalJob")
                || !state.getUUID("normalJob").equals(transaction) || state.getBoolean("acoSourceCommitted"),"BATCH_ACCEPTANCE_INCOMPLETE");
        return receipts.contains(transaction.toString())?Optional.of(receipts.getCompound(transaction.toString()).copy()):Optional.empty();
    }
    public static void forgetSavedBatch(ServerLevel level,UUID factory,int index,UUID transaction,BlockPos target) {
        if(level.hasChunkAt(target) && level.getBlockEntity(target) instanceof BaseAccelerator host
                && host.factoryHandle() instanceof FactorySession live && live.id().equals(factory)) {
            live.batchLane(index).orElseThrow().forgetBatch(transaction);return;
        }
        var journal=FactoryJournal.open(level,factory);var record=journal.read();
        var state=record.getCompound("blueprint").getInt("schema")==1?record:record.getCompound("runtimeLanes").getCompound(Integer.toString(index));
        state.getCompound("batchReceipts").remove(transaction.toString());
        if(state.hasUUID("normalJob") && state.getUUID("normalJob").equals(transaction) && state.getBoolean("acoBatchJob"))state.putBoolean("acoSourceCommitted",true);
        journal.write(record);
    }
    public appeng.api.storage.MEStorage assemblyReturns(){return assemblyProvider==null?null:assemblyProvider.getLogic().getReturnInv();}
    public ServerLevel handoffLevel(){return level;}
    public FactoryJournal handoffJournal(){return journal;}
    public Set<net.minecraft.world.level.block.entity.BlockEntity> recoveryParticipants() {
        if(closed || !connected() || children.stream().anyMatch(child->!child.connected()) || ExternalRecovery.assets(journal.read())==null)return Set.of();
        boolean v2SourceOwnsCpu=(normalJob==null || acoBatchJob)
                && children.stream().allMatch(child->child.normalJob==null || child.acoBatchJob)
                && (acoBatchJob || children.stream().anyMatch(child->child.acoBatchJob));
        var result=new HashSet<net.minecraft.world.level.block.entity.BlockEntity>();result.add(host);
        var grids=Collections.newSetFromMap(new IdentityHashMap<IGrid,Boolean>());
        grids.add(node.getGrid());grids.add(host.getMainNode().getGrid());
        for(var child:children)grids.add(child.node.getGrid());
        for(var grid:grids)for(var member:grid.getNodes()) {
            if(network!=null && network.owns(member))continue;
            var owner=member.getOwner();
            if(owner instanceof FactorySession session && session.id().equals(id()))continue;
            net.minecraft.world.level.block.entity.BlockEntity be;
            if(owner instanceof appeng.parts.AEBasePart part)be=part.getBlockEntity();
            else if(owner instanceof net.minecraft.world.level.block.entity.BlockEntity block)be=block;
            else return Set.of();
            if(be.getLevel()!=level || level.getBlockEntity(be.getBlockPos())!=be)return Set.of();
            if(be==host){result.add(be);continue;}
            // ACO's source receipt owns CPU task/energy/output recovery. Its acknowledgement
            // legitimately changes that CPU before this target reconnects; do not replay it.
            if(v2SourceOwnsCpu && be.getClass()==appeng.blockentity.crafting.CraftingBlockEntity.class)continue;
            if(be instanceof appeng.blockentity.networking.CableBusBlockEntity cable) {
                for(var side:Direction.values()) {
                    var part=cable.getPart(side);
                    if(part!=null && !(part instanceof VirtualP2PPart) && !(part instanceof appeng.parts.networking.QuartzFiberPart)
                            && part.getClass()!=appeng.parts.reporting.ItemTerminalPart.class)return Set.of();
                }
            } else if(!RecoveryParticipants.addonBlock(be) && !Set.of("appeng.blockentity.crafting.CraftingBlockEntity","appeng.blockentity.storage.DriveBlockEntity",
                    "appeng.blockentity.networking.EnergyCellBlockEntity","appeng.blockentity.networking.CreativeEnergyCellBlockEntity",
                    "appeng.blockentity.networking.ControllerBlockEntity").contains(be.getClass().getName()))return Set.of();
            if(be instanceof appeng.blockentity.storage.DriveBlockEntity drive)
                for(int slot=0;slot<drive.getInternalInventory().size();slot++) {
                    var stack=drive.getInternalInventory().getStackInSlot(slot);
                    if(!RecoveryParticipants.itemBackedCell(stack))return Set.of();
                }
            result.add(be);
        }
        for(var key:journal.read().getCompound("networkCells").getAllKeys())
            for(var value:journal.read().getCompound("networkCells").getList(key,Tag.TAG_COMPOUND))
                if(!RecoveryParticipants.itemBackedCell(PlatformData.loadItem(((CompoundTag)value).getCompound("item"),level)))return Set.of();
        return RecoveryParticipants.linksCovered(result)?result:Set.of();
    }
    public void checkpointExternal(CompoundTag chunks,ListTag participants) {
        if(closed)return;
        var record=journal.read();var assets=ExternalRecovery.assets(record);if(assets==null)return;
        var checkpoint=new CompoundTag();checkpoint.putInt("schema",1);checkpoint.put("assets",assets);checkpoint.put("chunks",chunks.copy());
        checkpoint.put("participants",participants.copy());
        record.put("externalCheckpoint",checkpoint);journal.write(record);
    }
    public boolean isOnline() { return online() && children.stream().allMatch(FactorySession::isOnline); }
    public ProcessingRuntime runtime() { return runtime; }
    public List<ProcessingRuntime> runtimes() {
        var result=new ArrayList<ProcessingRuntime>();result.add(runtime);for(var child:children)result.add(child.runtime);return List.copyOf(result);
    }
    public boolean canConfigure() { return (assemblyProvider==null || !assemblyProvider.hasWork()) && (!nativeAssembler || network==null || !network.assemblerBusy(blueprint.getLong("machine"))) && runtimes().stream().allMatch(ProcessingRuntime::canConfigure) && children.stream().allMatch(FactorySession::canConfigure); }
    public boolean supportsConfiguration(BigInteger speed,BigInteger lanes) {
        return com.syaru.ae2machines.block.ComputationStructure.accepts(host,speed,lanes)
                && (!vanillaTicked || network==null || runtime.lanes().equals(lanes) || network.furnacesRestorable())
                && children.stream().allMatch(child->child.supportsConfiguration(speed,lanes));
    }
    public void configure(BigInteger speed,BigInteger lanes) {
        ExactAmounts.positive(speed);ExactAmounts.positive(lanes);
        require(supportsConfiguration(speed,lanes),"NATIVE_MACHINE_CONFIGURATION_UNSUPPORTED");
        require(canConfigure(),"FACTORY_HAS_OWNED_WORK");
        journal.atomic(()->{
        for(var r:runtimes()) r.configure(speed,lanes);
        if(network!=null) {
            if(vanillaTicked)network.configureFurnace(blueprint.getLong("machine"),lanes);
            if(nativeAssembler)network.assembler(blueprint.getLong("machine")).trimIdleWorkers();
            for(var child:children)if(child.vanillaTicked)network.configureFurnace(child.blueprint.getLong("machine"),lanes);
            for(var child:children)if(child.nativeAssembler)network.assembler(child.blueprint.getLong("machine")).trimIdleWorkers();
        }
        persist();for(var child:children)child.persist();
        });
    }
    public boolean canDetach() { return !closed && canConfigure() && (network==null || network.furnacesRestorable()) && runtime.snapshots().stream().allMatch(j->j.state()==BatchJob.State.CANCELLED) && normalJob==null && children.stream().allMatch(FactorySession::canDetach); }
    public void detach() {
        require(canDetach(),"FACTORY_HAS_OWNED_WORK");
        if(network!=null)network.restoreCellsToBlueprint();
        saveNativeCharge();for(var child:children)child.saveNativeCharge();
        var record=journal.read();record.putString("state","CAPTURED");record.remove("accelerator");
        journal.write(record);close();
    }
    private void saveNativeCharge() {
        if(completion!=null)completion.restoreTo(blueprint);
        var blocks=blueprint.getList("blocks",Tag.TAG_COMPOUND);
        if(blueprint.contains("gtStructure"))GtMultiblockAdapter.saveCharge(level,blueprint,runtime.energy().stored());
        else for(var entry:blocks) {
            var block=(CompoundTag)entry;if(block.getLong("pos")!=blueprint.getLong("machine")) continue;
            for(var value:journal.read().getCompound("blueprint").getList("blocks",Tag.TAG_COMPOUND)) {
                var latest=(CompoundTag)value;if(latest.getLong("pos")==block.getLong("pos")) {
                    block.put("data",latest.getCompound("data").copy());
                    if(vanillaTicked)block.put("state",latest.getCompound("state").copy());
                }
            }
            // The assembler draws AE directly. A temporary, unready AE node would drop its saved owner.
            if(nativeAssembler)continue;
            var state=NbtUtils.readBlockState(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK),block.getCompound("state"));
            var be=((net.minecraft.world.level.block.EntityBlock)state.getBlock()).newBlockEntity(BlockPos.of(block.getLong("pos")),state);
            require(be!=null,"NATIVE_MACHINE_UNAVAILABLE");be.setLevel(level);PlatformData.loadBlock(be,block.getCompound("data"));
            NativeMachines.setStoredEnergy(be,runtime.energy().stored());block.put("data",PlatformData.saveBlock(be));
        }
        if(assemblyProvider!=null)for(var value:blocks) {
            var block=(CompoundTag)value;
            if(block.getLong("pos")==blueprint.getLong("provider"))block.put("data",assemblyProvider.save());
        }
        var record=journal.read();var all=record.getCompound("blueprint");
        if(all.getInt("schema")==1)record.put("blueprint",blueprint);
        else {
            all.getList("lanes",Tag.TAG_COMPOUND).set(laneIndex,blueprint.copy());
            var allBlocks=all.getList("blocks",Tag.TAG_COMPOUND);
            for(int i=0;i<allBlocks.size();i++) {
                var saved=allBlocks.getCompound(i);
                for(var entry:blocks)if(((CompoundTag)entry).getLong("pos")==saved.getLong("pos"))allBlocks.set(i,entry.copy());
            }
        }
        journal.write(record);
    }
    private CompoundTag readLane(CompoundTag record) {
        if(record.getCompound("blueprint").getInt("schema")==1)return record;
        return record.getCompound("runtimeLanes").getCompound(Integer.toString(laneIndex));
    }
    public void persist() {
        if(journal.deferCheckpoint(this,this::persistNow))return;
        persistNow();
    }
    private void persistNow() {
        var record=journal.read();record.putString("state","ACTIVE");record.putLong("accelerator",host.getBlockPos().asLong());
        var lane=readLane(record);
        lane.put("runtime",RuntimeCodec.write(runtime,key->PlatformData.saveKey(key,level)));
        if(assemblyProvider!=null)lane.put("assemblyProvider",assemblyProvider.save());
        if(normalJob==null) { lane.remove("normalJob");lane.remove("normalEpoch");acoBatchJob=false;acoSourceCommitted=false; }
        else { lane.putUUID("normalJob",normalJob);if(normalEpoch!=null)lane.putUUID("normalEpoch",normalEpoch); }
        lane.putBoolean("inputIntent",inputIntent);
        lane.putBoolean("acoBatchJob",acoBatchJob);lane.putBoolean("acoSourceCommitted",acoSourceCommitted);
        var receipts=new CompoundTag();batchReceipts.forEach((id,receipt)->receipts.put(id.toString(),receipt.copy()));lane.put("batchReceipts",receipts);
        lane.putBoolean("bufferedJob",bufferedJob);
        if(completion!=null)lane.put("nativeCompletion",completion.save());
        var ledger=new ListTag();
        delivered.forEach((key,amount)->{
            if(amount.signum()==0)return;
            var saved=new CompoundTag();saved.put("key",PlatformData.saveKey(key,level));saved.putByteArray("amount",amount.toByteArray());ledger.add(saved);
        });
        lane.put("delivered",ledger);lane.putBoolean("deliveryIntent",deliveryIntent);
        if(lane!=record) {
            var states=record.getCompound("runtimeLanes");states.put(Integer.toString(laneIndex),lane);record.put("runtimeLanes",states);
        }
        if(network!=null)network.snapshot(record);
        journal.write(record);
    }
    private VirtualP2PPart boundary() {
        var pos=BlockPos.of(blueprint.getLong("boundary"));
        if(!level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof IPartHost host)) return null;
        var side=Direction.byName(blueprint.getString("side"));
        if(!(host.getPart(side) instanceof VirtualP2PPart tunnel) || !tunnel.isOutput()
                || tunnel.getFrequency()!=blueprint.getShort("frequency")) return null;
        return tunnel;
    }
    private boolean online() {
        if(acoBatchJob && !acoSourceCommitted)return false;
        return connected();
    }
    private boolean connected() {
        if(closed || node==null || !node.isActive() || !host.networkOnline()) return false;
        if(completion!=null && !completion.online())return false;
        if(blueprint.contains("gtStructure"))for(var value:blueprint.getCompound("gtStructure").getList("returns",Tag.TAG_LONG)) {
            var pos=BlockPos.of(((LongTag)value).getAsLong());
            if(!level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof appeng.blockentity.misc.InterfaceBlockEntity target)
                    || !target.getMainNode().isActive() || target.getMainNode().getGrid()!=node.getGrid())return false;
        }
        if(network!=null)return network.online(node.getNode());
        if(blueprint.getBoolean("network"))return false;
        var endpoint=boundary();
        return endpoint!=null && endpoint.getMainNode().isActive() && connectedBoundary==endpoint.getExternalFacingNode();
    }
    private void bindNative(NativeFactoryCompiler.Compiled compiled) {
        nativeAssembler=NativeAssemblers.supports(compiled.machine());
        if(nativeAssembler)return;
        vanillaTicked=VanillaFurnaces.supports(compiled.machine());
        if(vanillaTicked)return;
        if(NativeMachines.mek() && MekanismAdapter.itemMachine(compiled.machine())) {
            runtime.passiveNativeLanes(BigInteger.valueOf(com.syaru.ae2machines.compat.mek.MekItemMachine.lanes(
                    (mekanism.common.tile.prefab.TileEntityConfigurableMachine)compiled.machine())));
            bindPreparedRecipes(recipe->new com.syaru.ae2machines.compat.mek.MekNativeProcessor(compiled.machine(),recipe));
        }
        else if(NativeMachines.mek() && compiled.machine()!=null && com.syaru.ae2machines.platform.MekChemicalAdapter.supports(compiled.machine()))
            runtime.nativeExecution(recipe->new com.syaru.ae2machines.compat.mek.MekChemicalNativeProcessor(compiled.machine(),recipe));
        else if(NativeMachines.gt() && blueprint.contains("gtStructure"))
            bindPreparedRecipes(recipe->new com.syaru.ae2machines.platform.GtNativeProcessor(level,blueprint.getCompound("gtStructure"),recipe));
        else if(NativeMachines.gt() && compiled.machine()!=null && com.syaru.ae2machines.platform.GregTechAdapter.supports(compiled.machine()))
            bindPreparedRecipes(recipe->new com.syaru.ae2machines.platform.GtNativeProcessor(compiled.machine(),recipe));
        else throw new IllegalArgumentException("NATIVE_EXECUTOR_UNAVAILABLE");
    }
    private void bindPreparedRecipes(java.util.function.Function<Recipe<AEKey>,NativeBatchProcessor> create) {
        // Native helpers own conversion. Reuse their prepared bindings only in this factory/generation.
        var bindings=new HashMap<Recipe<AEKey>,NativeBatchProcessor>();preparedRecipes=bindings;
        for(var pattern:program.patterns())program.resolve(pattern).ifPresent(recipe->bindings.computeIfAbsent(recipe,create));
        runtime.nativeExecution(recipe->bindings.computeIfAbsent(recipe,create));
    }
    public void tick(long tick) {
        tickState(tick);
        if(closed || !hasReadyOutput())return;
        var batch=new DeliveryBatch();
        try {
            // Finish internal inventory changes before taking the external transaction's before-image.
            deliverAll(batch,true);
            deliverAll(batch,false);
            if(batch.handoff!=null)batch.handoff.commit();
        } catch(RuntimeException failure) {
            if(batch.handoff!=null)throw new ExternalHandoffs.PersistenceFailure(failure);
            throw failure;
        }
    }
    private void tickState(long tick) {
        if(closed) return;
        if(ownsNetwork)network.tick();
        for(var child:children)child.tickState(tick);
        if(recipeRevision!=FactoryRecipes.revision()) {
            recipeRevision=FactoryRecipes.revision();
            try {
                var compiled=NativeFactoryCompiler.rebuild(level,blueprint,this::online);var current=compiled.energy();
                require(profile.capacity().equals(current.capacity()) && profile.inputRate().equals(current.inputRate())
                        && profile.unitsPerFE().equals(current.unitsPerFE()),"MACHINE_ENERGY_CONFIGURATION_CHANGED");
                runtime.refresh(compiled.program());program=compiled.program();
                if(!blueprint.getBoolean("passive"))ICraftingProvider.requestUpdate(node);
                bindNative(compiled);
                if(assemblyProvider!=null)assemblyProvider.getLogic().updatePatterns();
            } catch(RuntimeException e) {
                runtime.restoreQuarantine(true);persist();
                org.slf4j.LoggerFactory.getLogger(FactorySession.class).warn("Factory {} stopped after recipe reload",id(),e);
            }
        }
        if(network==null) {
        var endpoint=boundary();var external=endpoint==null ? null:endpoint.getExternalFacingNode();
        if(connectedBoundary!=external) {
            if(connection!=null) connection.destroy();connection=null;connectedBoundary=null;
        }
        if(external!=null && connection==null && tick!=lastReconnect) {
            lastReconnect=tick;
            connection=GridHelper.createConnection(node.getNode(),external);connectedBoundary=external;
        }
        }
        if(vanillaTicked && network!=null && online() && !runtime.quarantined())network.tickNative(blueprint.getLong("machine"),runtime.speed());
        if(assemblyProvider!=null && online() && !runtime.quarantined() && assemblyProvider.hasWork()) {
            var handoff=ExternalHandoffs.begin(this,Map.of());
            try {
                journal.atomic(()->{assemblyProvider.work();persist();});
                if(handoff!=null)handoff.commit();
            }catch(RuntimeException failure){
                if(handoff!=null)throw new ExternalHandoffs.PersistenceFailure(failure);
                throw failure;
            }
        }
        if(normalJob==null && runtime.canConfigure() && online())startBuffered();
        runtime.tick(tick);
        if(completion!=null && !runtime.quarantined() && online()) {
            try {if(completion.tick(runtime))persist();}
            catch(RuntimeException failure){runtime.restoreQuarantine(true);persist();throw failure;}
        }
    }
    private boolean hasReadyOutput() {
        return children.stream().anyMatch(FactorySession::hasReadyOutput)
                || normalJob!=null && runtime.find(normalJob,"ae2-normal").orElseThrow().state()==BatchJob.State.OUTPUT_READY;
    }
    private void deliverAll(DeliveryBatch batch,boolean buffered) {
        for(var child:children)child.deliverAll(batch,buffered);
        if(!closed && bufferedJob==buffered && normalJob!=null && online() && !runtime.quarantined())deliver(batch);
    }
    private static final class DeliveryBatch {
        private ExternalHandoffs.Transaction handoff;
        private boolean attempted;
        ExternalHandoffs.Transaction begin(FactorySession session) {
            if(!attempted) { attempted=true;handoff=ExternalHandoffs.begin(session,Map.of()); }
            if(handoff!=null && !handoff.owns(session))throw new ExternalHandoffs.PersistenceFailure(new IllegalStateException("Cross-factory delivery"));
            return handoff;
        }
    }
    @Override public List<IPatternDetails> getAvailablePatterns() { return assemblyProvider==null?program.patterns():assemblyProvider.getLogic().getAvailablePatterns(); }
    @Override public int getPatternPriority() { return blueprint.getInt("priority"); }
    @Override public boolean isBusy() {
        // Never mix a normally extracted append into the job owned by a V2 source receipt.
        if(!online() || bufferedJob || acoBatchJob && normalJob!=null)return true;
        if(assemblyProvider!=null)return assemblyProvider.getLogic().isBusy();
        return normalJob!=null ? blueprint.getBoolean("blocking") || !runtime.appendable() : !runtime.canConfigure();
    }
    @Override public boolean pushPattern(IPatternDetails pattern,KeyCounter[] holders) {
        return pushPatterns(pattern,holders,1);
    }
    public int normalBatchLimit(IPatternDetails pattern,int limit) {
        if(assemblyProvider!=null)return 0;
        if(limit<2 || isBusy() || blueprint.getBoolean("blocking"))return 0;
        var recipe=runtime.resolve(pattern);
        return recipe.isEmpty()?0:runtime.appendRoom(recipe.get()).min(BigInteger.valueOf(limit)).intValueExact();
    }
    public boolean pushPatterns(IPatternDetails pattern,KeyCounter[] holders,int executions) {
        if(executions<=0 || executions>1 && normalBatchLimit(pattern,executions)!=executions)return false;
        if(isBusy()) return false;
        if(assemblyProvider!=null) {
            boolean[] accepted={false};
            journal.atomic(()->{accepted[0]=assemblyProvider.getLogic().pushPattern(pattern,holders);if(accepted[0])persist();});
            return accepted[0];
        }
        var epoch=FactoryRecovery.epoch(level.getServer());epoch.requireActive();
        var recipe=runtime.resolve(pattern);if(recipe.isEmpty()) return false;
        var provided=new LinkedHashMap<AEKey,BigInteger>();
        for(var holder:holders) for(var entry:holder) provided.merge(entry.getKey(),BigInteger.valueOf(entry.getLongValue()),BigInteger::add);
        var count=BigInteger.valueOf(executions);
        if(!provided.equals(ExactAmounts.multiply(recipe.get().inputs(),count))) return false;
        if(normalJob!=null) {
            inputIntent=true;
            try {
                if(!runtime.appendLocal(normalJob,recipe.get(),count,provided)){inputIntent=false;return false;}
                for(var holder:holders)holder.clear();inputIntent=false;persist();return true;
            }catch(RuntimeException e){runtime.restoreQuarantine(true);throw e;}
        }
        normalJob=UUID.randomUUID();normalEpoch=epoch.current();inputIntent=true;
        try {
            if(!runtime.accept(normalJob,"ae2-normal",pattern,count,provided,ExactAmounts.multiply(recipe.get().outputs(),count))) { normalJob=null;normalEpoch=null;inputIntent=false;return false; }
            for(var holder:holders) holder.clear();
            inputIntent=false;persist();
            return true;
        } catch(RuntimeException e) { runtime.restoreQuarantine(true);throw e; }
    }
    private void deliver(DeliveryBatch batch) {
        var job=runtime.find(normalJob,"ae2-normal").orElseThrow();
        if(job.state()!=BatchJob.State.OUTPUT_READY || !runtime.deliveryReady(normalJob)) return;
        if(bufferedJob) {
            if(directNativeWork)return;
            var buffer=network==null?null:network.machine(blueprint.getLong("machine"));
            if(buffer==null)return;
            try {journal.atomic(()->{
                if(!buffer.putOutputs(job.receipt()))return;
                var finished=normalJob;normalJob=null;normalEpoch=null;bufferedJob=false;
                runtime.finishLocalDelivery(finished);
            });}catch(RuntimeException e){runtime.restoreQuarantine(true);throw e;}
            return;
        }
        var storage=node.getGrid().getStorageService().getInventory();var source=new MachineSource(this);
        boolean canDeliver=false;
        for(var output:job.receipt().entrySet()) {
            var remaining=output.getValue().subtract(delivered.getOrDefault(output.getKey(),BigInteger.ZERO));
            if(remaining.signum()>0 && storage.insert(output.getKey(),remaining.min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact(),Actionable.SIMULATE,source)>0){canDeliver=true;break;}
        }
        if(!canDeliver && !delivered.equals(job.receipt()))return;
        var handoff=batch.begin(this);
        try {
        for(var output:job.receipt().entrySet()) {
            var remaining=output.getValue().subtract(delivered.getOrDefault(output.getKey(),BigInteger.ZERO));
            if(remaining.signum()==0) continue;
            long offered=remaining.min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact();
            long simulated=storage.insert(output.getKey(),offered,Actionable.SIMULATE,source);
            if(simulated==0) continue;
            require(simulated>0 && simulated<=offered,"INVALID_STORAGE_SIMULATION");
            deliveryIntent=true;persist();
            long inserted;
            try { inserted=storage.insert(output.getKey(),simulated,Actionable.MODULATE,source); }
            catch(RuntimeException e) { runtime.restoreQuarantine(true);persist();throw e; }
            if(inserted<0 || inserted>simulated) { runtime.restoreQuarantine(true);persist();throw new IllegalStateException("INVALID_STORAGE_INSERTION"); }
            delivered.merge(output.getKey(),BigInteger.valueOf(inserted),BigInteger::add);
            deliveryIntent=false;persist();
        }
        if(delivered.equals(job.receipt())) {
            var finished=normalJob;normalJob=null;normalEpoch=null;delivered.clear();runtime.finishLocalDelivery(finished);
        }
        } catch(RuntimeException failure) {
            if(handoff!=null)throw new ExternalHandoffs.PersistenceFailure(failure);
            throw failure;
        }
    }
    private void startBuffered() {
        var buffer=network==null?null:network.machine(blueprint.getLong("machine"));
        if(buffer==null)return;
        var nativeRecipe=buffer.discoverRecipe();
        if(nativeRecipe.isPresent()) {
            var recipe=nativeRecipe.get();var count=buffer.availableExecutions(recipe);
            if(count.signum()>0)try {journal.atomic(()->{
                normalJob=UUID.randomUUID();normalEpoch=FactoryRecovery.epoch(level.getServer()).current();bufferedJob=true;
                buffer.takeInputs(recipe,count);
                require(runtime.acceptPassive(normalJob,recipe,count),"PASSIVE_JOB_NOT_ACCEPTED");
            });}catch(RuntimeException e){runtime.restoreQuarantine(true);throw e;}
            return;
        }
        for(var pattern:program.patterns()) {
            var recipe=runtime.resolve(pattern).orElse(null);
            if(recipe==null)continue;
            var count=buffer.availableExecutions(recipe);if(count.signum()==0)continue;
            try {journal.atomic(()->{
                normalJob=UUID.randomUUID();normalEpoch=FactoryRecovery.epoch(level.getServer()).current();bufferedJob=true;
                buffer.takeInputs(recipe,count);
                require(runtime.accept(normalJob,"ae2-normal",pattern,count,ExactAmounts.multiply(recipe.inputs(),count),ExactAmounts.multiply(recipe.outputs(),count)),"BUFFERED_JOB_NOT_ACCEPTED");
            });}catch(RuntimeException e){runtime.restoreQuarantine(true);throw e;}
            return;
        }
    }
    private void assemblerWork(Runnable action) {
        var handoff=ExternalHandoffs.begin(this,Map.of());
        try {
            journal.atomic(()->{action.run();persist();});
            if(handoff!=null)handoff.commit();
        } catch(RuntimeException failure) {
            if(handoff!=null)throw new ExternalHandoffs.PersistenceFailure(failure);
            throw failure;
        }
    }
    private void bindWork() {
        if(vanillaTicked && network!=null)network.configureFurnace(blueprint.getLong("machine"),runtime.lanes());
        if(nativeAssembler && network!=null) {
            var assembler=network.assembler(blueprint.getLong("machine"));
            require(assembler!=null,"MISSING_NATIVE_ASSEMBLER");
            assembler.bindClock(runtime::speed,this::assemblerWork);
            assembler.bindParallel(runtime::lanes);
        }
        var buffer=network==null?null:network.machine(blueprint.getLong("machine"));if(buffer==null)return;
        directNativeWork=buffer.bindWork(new BufferedMachine.WorkAccess() {
            @Override public long insert(Recipe<AEKey> recipe,AEKey key,long amount,boolean simulate) {
                if(!online() || runtime.quarantined() || amount<=0 || recipe.inputs().size()!=1 || !recipe.inputs().containsKey(key)
                        || normalJob!=null && !bufferedJob)return 0;
                var unit=recipe.inputs().get(key);
                var count=BigInteger.valueOf(amount).divide(unit).min(runtime.appendRoom(recipe));
                if(count.signum()==0)return 0;
                if(!simulate) {
                    if(normalJob==null) {
                        normalJob=UUID.randomUUID();normalEpoch=FactoryRecovery.epoch(level.getServer()).current();bufferedJob=true;
                        require(runtime.acceptPassive(normalJob,recipe,count),"PASSIVE_JOB_NOT_ACCEPTED");
                    } else require(runtime.appendLocal(normalJob,recipe,count,ExactAmounts.multiply(recipe.inputs(),count)),"PASSIVE_APPEND_REJECTED");
                }
                return count.multiply(unit).longValueExact();
            }
            @Override public Map<AEKey,BigInteger> outputs() {
                if(normalJob==null || !bufferedJob || !online() || runtime.quarantined())return Map.of();
                var job=runtime.find(normalJob,"ae2-normal").orElseThrow();
                if(job.state()!=BatchJob.State.OUTPUT_READY || !runtime.deliveryReady(normalJob))return Map.of();
                var left=new LinkedHashMap<AEKey,BigInteger>();
                job.receipt().forEach((key,n)->{var rest=n.subtract(delivered.getOrDefault(key,BigInteger.ZERO));if(rest.signum()>0)left.put(key,rest);});
                return Map.copyOf(left);
            }
            @Override public long extract(AEKey key,long amount,boolean simulate) {
                if(amount<=0)return 0;
                long accepted=outputs().getOrDefault(key,BigInteger.ZERO).min(BigInteger.valueOf(amount)).longValueExact();
                if(!simulate && accepted>0) {
                    delivered.merge(key,BigInteger.valueOf(accepted),BigInteger::add);
                    var receipt=runtime.find(normalJob,"ae2-normal").orElseThrow().receipt();
                    if(delivered.equals(receipt)) {
                        var finished=normalJob;normalJob=null;normalEpoch=null;bufferedJob=false;delivered.clear();runtime.finishLocalDelivery(finished);
                    } else persist();
                }
                return accepted;
            }
        });
    }
    public int receiveFE(int offered,long tick,boolean simulate) {
        if(offered<=0 || closed) return 0;
        if(powerLanes.size()==1)return runtime.quarantined()?0:energyInput.receive(offered,tick,simulate);
        var receivers=powerLanes.stream().<PowerDistribution.Receiver>map(lane->(amount,probe)->
                lane.closed || lane.runtime.quarantined()?0:lane.energyInput.receive(Math.toIntExact(amount),tick,probe)).toList();
        return Math.toIntExact(PowerDistribution.receive(offered,tick,simulate,receivers));
    }
    public int storedFE() { return energyInput.stored(); }
    public long receiveEU(long voltage,long amperage,long tick,boolean simulate) {
        if(closed || voltage<=0 || amperage<=0)return 0;
        if(powerLanes.size()==1)return runtime.receivePackets(voltage,amperage,tick,simulate);
        var receivers=powerLanes.stream().<PowerDistribution.Receiver>map(lane->(amount,probe)->
                lane.closed?0:lane.runtime.receivePackets(voltage,amount,tick,probe)).toList();
        return PowerDistribution.receive(amperage,tick,simulate,receivers);
    }
    public long voltageEU() {
        long voltage=profile.voltage();for(var child:children)voltage=Math.max(voltage,child.voltageEU());return voltage;
    }
    public long demandEU() {
        BigInteger amount=BigInteger.ZERO;
        for(var r:runtimes())if(r.energy().unit().equals(MachineProfile.GT_UNIT))amount=amount.add(r.energyDemand(level.getGameTime()));
        return amount.min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact();
    }
    public int capacityFE() {
        long amount=energyInput.capacity();for(var child:children)amount+=child.capacityFE();return (int)Math.min(Integer.MAX_VALUE,amount);
    }
    @Override public IGridNode getActionableNode() { return node.getNode(); }
    @Override public void close() { if(closed)return;closed=true;ExternalRecovery.unregister(level,this);for(var child:children)child.close();if(ownsNetwork)network.close();node.destroy();connection=null;connectedBoundary=null; }
}
