package com.syaru.ae2machines.runtime;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.*;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.me.helpers.MachineSource;
import com.syaru.ae2machines.engine.*;
import com.syaru.ae2machines.platform.*;
import com.syaru.ae2machines.world.*;
import java.math.BigInteger;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import static com.syaru.ae2machines.world.NativeFactoryCompiler.require;

/** Exact owned requests executed by the actual machine in its cell world. No pattern-produced output. */
public final class NativeWorldBatches {
    private static final ThreadLocal<BlockEntity> EXECUTING=new ThreadLocal<>();
    public static boolean executing(BlockEntity block){return EXECUTING.get()==block;}
    public static <T> T nativeScope(BlockEntity block,java.util.function.Supplier<T> action) {
        var previous=EXECUTING.get();EXECUTING.set(block);
        try{return action.get();}finally{if(previous==null)EXECUTING.remove();else EXECUTING.set(previous);}
    }
    private void nativeWork(Entry e,Runnable action) {
        nativeScope(world.getBlockEntity(e.target.machine()),()->{action.run();return null;});
    }
    public record Target(BlockPos provider,BlockPos machine,IPatternDetails pattern,MachineProfile profile) {}
    private final NativeWorldSession session;
    private final ServerLevel world;
    private final FactoryJournal journal;
    private final Map<UUID,Entry> entries=new LinkedHashMap<>();
    private final Map<Long,Entry> occupiedMachines=new HashMap<>();
    private final Map<Long,Integer> occupiedProviders=new HashMap<>();
    private final Map<Long,NormalProvider> normalProviders=new HashMap<>();
    private final Map<UUID,Terminal> terminal=new LinkedHashMap<>();
    private final Set<UUID> pendingEnergy=new LinkedHashSet<>();
    private record Terminal(CompoundTag tag,ProcessingRuntime runtime) {}
    private final Map<AEItemKey,List<Target>> targets=new HashMap<>();
    private final Map<Long,List<Target>> providerTargets=new HashMap<>();
    private final Map<Long,Set<Long>> dependents=new HashMap<>();
    private final Map<Long,Set<Long>> providerDependencies=new HashMap<>();
    private final Set<Long> dirtyProviders=new LinkedHashSet<>();
    private final Map<BlockPos,List<BlockEntity>> structureProviders=new HashMap<>();
    private long indexBuilds,providerBuilds,indexHits;
    public record IndexStatistics(long builds,long providers,long hits){}
    public IndexStatistics indexStatistics(){return new IndexStatistics(indexBuilds,providerBuilds,indexHits);}
    private record Binding(long machine,AEItemKey pattern,long revision) {}
    private final Map<Binding,NativeBatchProcessor> bindings=new HashMap<>();
    private boolean indexed,loading=true,rebindQuarantined;
    private long revision=FactoryRecipes.revision();
    public NativeWorldBatches(NativeWorldSession session,FactoryJournal journal) {
        this.session=session;this.world=session.world();this.journal=journal;
        var record=journal.read();
        for(var value:record.contains("worldBatches")?savedEntries(record.getCompound("worldBatches")):new ListTag()) {
            var tag=(CompoundTag)value;
            require(!entries.containsKey(tag.getUUID("id")) && !terminal.containsKey(tag.getUUID("id")),"DUPLICATE_NATIVE_BATCH");
            var stored=readRuntime(tag);
            if(stored.snapshots().stream().noneMatch(c->c.state()==BatchJob.State.RUNNING || c.state()==BatchJob.State.OUTPUT_READY || c.state()==BatchJob.State.QUARANTINED)) {
                require(terminal.put(tag.getUUID("id"),new Terminal(tag.copy(),stored))==null,"DUPLICATE_NATIVE_BATCH");continue;
            }
            var pattern=PatternDetailsHelper.decodePattern(PlatformData.loadItem(tag.getCompound("pattern"),world),world);
            require(pattern!=null,"NATIVE_BATCH_PATTERN_UNAVAILABLE");
            var target=resolveSaved(tag,pattern);var entry=new Entry(target,tag);
            require(entries.put(entry.id,entry)==null,"DUPLICATE_NATIVE_BATCH");
            occupy(entry);
        }
        terminal.forEach((id,t)->{if(t.runtime().energy().stored().signum()>0)pendingEnergy.add(id);});
        loading=false;
        if(rebindQuarantined || !record.contains("worldBatches"))save();
    }
    private static ListTag savedEntries(CompoundTag saved) {
        require(saved.getInt("schema")==1 && saved.get("entries") instanceof ListTag,"UNKNOWN_NATIVE_BATCH_SCHEMA");
        var list=(ListTag)saved.get("entries");require(list.isEmpty() || list.getElementType()==Tag.TAG_COMPOUND,"INVALID_NATIVE_BATCH_ENTRIES");return list;
    }
    public static boolean restorable(CompoundTag saved,java.util.function.Function<CompoundTag,AEKey> keyReader) {
        for(var value:savedEntries(saved)) {
            var tag=(CompoundTag)value;var runtime=RuntimeCodec.read(tag.getCompound("runtime"),keyReader,ignored->{});
            if(runtime.quarantined() || runtime.energy().stored().signum()>0)return false;
            for(var job:runtime.snapshots())if(job.state()!=BatchJob.State.CANCELLED
                    && !((tag.getBoolean("v2") || tag.getBoolean("normal")) && job.state()==BatchJob.State.ACKNOWLEDGED))return false;
        }
        return true;
    }
    private ProcessingRuntime readRuntime(CompoundTag tag){return RuntimeCodec.read(tag.getCompound("runtime"),key->PlatformData.loadKey(key,world),ignored->{});}
    public static NativeWorldBatches get(BlockEntity be) {
        if(be==null || !(be.getLevel() instanceof ServerLevel level) || !NativeCellWorlds.owns(level))return null;
        return NativeWorldRuntime.get(level).batches();
    }
    public static boolean owns(BlockEntity machine) {var batches=get(machine);return batches!=null && batches.busy(machine.getBlockPos());}
    public static void configurationChanged(BlockEntity machine) {
        var batches=get(machine);if(batches==null)return;
        var pos=machine.getBlockPos();
        var chunk=batches.world.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
        if(chunk!=null && chunk.getBlockEntities().get(pos)==machine)batches.invalidate(pos);
    }
    public static boolean providerBusy(BlockEntity provider) {
        var batches=get(provider);if(batches==null)return false;
        if(batches.session.variable()!=null && batches.session.variable().providerBusy(provider.getBlockPos()))return true;
        if(!batches.occupiedProviders.containsKey(provider.getBlockPos().asLong()))return false;
        return batches.entries.values().stream().filter(e->e.target.provider().equals(provider.getBlockPos()))
                .anyMatch(e->!e.normal || !e.runtime.appendable());
    }
    public static boolean providerOccupied(BlockEntity provider) {
        var batches=get(provider);return batches!=null && (batches.occupiedProviders.containsKey(provider.getBlockPos().asLong())
                || batches.session.variable()!=null && batches.session.variable().providerBusy(provider.getBlockPos()));
    }
    public com.syaru.ae2machines.block.BaseAccelerator host(){return session.host();}
    private long workTick(){return session.host().getLevel().getGameTime();}
    public UUID factoryId(){return journal.id();}
    public boolean busy(BlockPos machine){return occupiedMachines.containsKey(machine.asLong()) || session.variable()!=null && session.variable().busy(machine);}
    private void occupy(Entry e){
        require(e.members.stream().noneMatch(p->occupiedMachines.containsKey(p.asLong())),"DUPLICATE_NATIVE_MACHINE_OWNER");
        for(var pos:e.members)occupiedMachines.put(pos.asLong(),e);
        occupiedProviders.merge(e.target.provider().asLong(),1,Math::addExact);
    }
    private void release(Entry e){for(var pos:e.members)occupiedMachines.remove(pos.asLong(),e);occupiedProviders.computeIfPresent(e.target.provider().asLong(),(p,n)->n==1?null:n-1);}
    public boolean active(){return entries.values().stream().anyMatch(Entry::occupies);}
    public boolean requiresWorldTicks(){return active() || !pendingEnergy.isEmpty();}
    public void invalidate(){
        indexed=false;targets.clear();providerTargets.clear();dependents.clear();providerDependencies.clear();dirtyProviders.clear();
        structureProviders.clear();normalProviders.clear();if(!active())bindings.clear();
    }
    public void invalidateProvider(BlockPos pos){if(indexed)dirtyProviders.add(pos.asLong());}
    public void invalidate(BlockPos pos){
        if(!indexed)return;
        // Structural routes may span multiple hatches. Re-discover their real formed structure.
        if(!structureProviders.isEmpty() || NativeMachines.gt() && GtMultiblockAdapter.supports(world.getBlockEntity(pos))){invalidate();return;}
        dirtyProviders.add(pos.asLong());
        for(var side:Direction.values())dirtyProviders.add(pos.relative(side).asLong());
        dirtyProviders.addAll(dependents.getOrDefault(pos.asLong(),Set.of()));
    }
    public List<ProcessingRuntime> runtimes(){return entries.values().stream().filter(Entry::occupies).map(e->e.runtime).toList();}
    public Optional<Target> target(IPatternDetails pattern) {
        if(pattern==null || pattern.getClass()!=appeng.crafting.pattern.AEProcessingPattern.class)return Optional.empty();
        if(revision!=FactoryRecipes.revision()){revision=FactoryRecipes.revision();invalidate();}
        if(!indexed)index();
        else if(!dirtyProviders.isEmpty()){
            var dirty=List.copyOf(dirtyProviders);dirtyProviders.clear();
            for(long pos:dirty)indexProvider(BlockPos.of(pos));
        }else indexHits++;
        return targets.getOrDefault(pattern.getDefinition(),List.of()).stream().findFirst();
    }
    public boolean supportsExact(IPatternDetails pattern){return target(pattern).map(t->processor(t).independentCopies()).orElse(false);}
    private Optional<Target> target(BlockPos provider,IPatternDetails pattern) {
        target(pattern);
        return targets.getOrDefault(pattern.getDefinition(),List.of()).stream().filter(t->t.provider().equals(provider)).findFirst();
    }
    public CountedCraftingProvider normalProvider(BlockEntity block,IPatternDetails pattern) {
        if(!(block instanceof PatternProviderBlockEntity provider) || !session.isOnline())return null;
        var target=target(provider.getBlockPos(),pattern).orElse(null);
        if(target==null || !((NativeWorldProvider)provider.getLogic()).machines$countedTarget(pattern,target.machine())
                || !processor(target).independentCopies())return null;
        if(!ExternalHandoffs.nativeHandoffActive(journal) && !ExternalHandoffs.supportsNativeOutput(session))return null;
        return normalProviders.computeIfAbsent(provider.getBlockPos().asLong(),ignored->new NormalProvider(provider));
    }
    private final class NormalProvider implements CountedCraftingProvider {
        private final PatternProviderBlockEntity provider;
        NormalProvider(PatternProviderBlockEntity provider){this.provider=provider;}
        @Override public CountedCraftingProvider handoffOwner(){return this;}
        @Override public ServerLevel handoffLevel(){return (ServerLevel)session.host().getLevel();}
        @Override public FactoryJournal handoffJournal(){return journal;}
        @Override public Set<BlockEntity> recoveryParticipants(){return NativeWorldCheckpoint.outputParticipants(session.host(),world);}
        @Override public boolean nativeWorldHandoff(){return true;}
        @Override public List<IPatternDetails> getAvailablePatterns(){return provider.getLogic().getAvailablePatterns();}
        @Override public int getPatternPriority(){return provider.getLogic().getPriority();}
        @Override public boolean isBusy(){return provider.isRemoved() || !session.isOnline() || !provider.getMainNode().isActive() || provider.getLogic().isBusy();}
        @Override public boolean pushPattern(IPatternDetails pattern,KeyCounter[] holders){return pushPatterns(pattern,holders,1);}
        @Override public int normalBatchLimit(IPatternDetails pattern,int limit) {
            if(limit<=0 || isBusy() || session.host().migratingAcceleration())return 0;
            var t=target(provider.getBlockPos(),pattern).orElse(null);if(t==null)return 0;
            if(!((NativeWorldProvider)provider.getLogic()).machines$countedTarget(pattern,t.machine()))return 0;
            var old=occupiedMachines.get(t.machine().asLong());
            if(old!=null && (!old.normal || !old.target.provider().equals(t.provider()) || !old.target.pattern().getDefinition().equals(pattern.getDefinition())))return 0;
            if(old==null && (!idle(t) || busy(t.machine())))return 0;
            var room=old==null?session.runtime().lanes().multiply(session.runtime().speed().divide(t.profile().recipe().duration()).max(BigInteger.ONE))
                    :old.runtime.appendRoom(t.profile().recipe());
            return room.min(BigInteger.valueOf(limit)).intValueExact();
        }
        @Override public boolean pushPatterns(IPatternDetails pattern,KeyCounter[] holders,int executions) {
            if(executions<=0 || normalBatchLimit(pattern,executions)!=executions)return false;
            var t=target(provider.getBlockPos(),pattern).orElseThrow();var recipe=t.profile().recipe();
            var count=BigInteger.valueOf(executions);var provided=new LinkedHashMap<AEKey,BigInteger>();
            for(var holder:holders)for(var input:holder)provided.merge(input.getKey(),BigInteger.valueOf(input.getLongValue()),BigInteger::add);
            if(!provided.equals(ExactAmounts.multiply(recipe.inputs(),count)))return false;
            var old=occupiedMachines.get(t.machine().asLong());
            if(old!=null) {
                boolean[] accepted={false};journal.atomic(()->{
                    accepted[0]=old.runtime.appendLocal(old.id,recipe,count,provided);
                    if(accepted[0])save();
                });
                if(!accepted[0])return false;
            } else {
                var e=new Entry(UUID.randomUUID(),"ae2-normal",t,false,"");e.normal=true;
                journal.atomic(()->{
                    entries.put(e.id,e);occupy(e);
                    require(e.runtime.accept(e.id,e.digest,pattern,count,provided,ExactAmounts.multiply(recipe.outputs(),count)),"NATIVE_NORMAL_ADMISSION_CHANGED");
                    save();
                });
            }
            for(var holder:holders)holder.clear();return true;
        }
    }
    public int preparedRecipeCount(){return bindings.size();}
    private NativeBatchProcessor processor(Target target) {
        var key=new Binding(target.machine().asLong(),target.pattern().getDefinition(),revision);
        return bindings.computeIfAbsent(key,ignored->NativeWorldMachine.processor(world.getBlockEntity(target.machine()),target.profile()));
    }
    public long limit(IPatternDetails pattern,long offered) {
        if(session.host().migratingAcceleration())return 0;
        var t=target(pattern).orElse(null);if(t==null || offered<2 || busy(t.machine()) || !idle(t) || !processor(t).independentCopies())return 0;
        var n=BigInteger.valueOf(offered).min(session.runtime().lanes().multiply(session.runtime().speed().divide(t.profile().recipe().duration()).max(BigInteger.ONE)));
        for(var map:List.of(t.profile().recipe().inputs(),t.profile().recipe().outputs()))for(var amount:map.values())n=n.min(BigInteger.valueOf(Long.MAX_VALUE).divide(amount));
        return n.longValueExact();
    }
    private void index() {
        targets.clear();providerTargets.clear();dependents.clear();providerDependencies.clear();structureProviders.clear();dirtyProviders.clear();indexBuilds++;
        if(NativeMachines.gt())for(var machine:session.nativeBlocks())if(GtMultiblockAdapter.supports(machine))
            for(var provider:GtMultiblockAdapter.worldProviders(machine))structureProviders.computeIfAbsent(provider,ignored->new ArrayList<>()).add(machine);
        for(var block:session.nativeBlocks())if(block.getClass()==PatternProviderBlockEntity.class) {
            indexProvider(block.getBlockPos());
        }
        indexed=true;
    }
    private void indexProvider(BlockPos pos){
        long id=pos.asLong();
        var previous=providerTargets.remove(id);
        if(previous!=null)for(var target:previous){
            var list=targets.get(target.pattern().getDefinition());
            if(list!=null){list.remove(target);if(list.isEmpty())targets.remove(target.pattern().getDefinition());}
            bindings.remove(new Binding(target.machine().asLong(),target.pattern().getDefinition(),revision));
        }
        var oldDependencies=providerDependencies.remove(id);
        if(oldDependencies!=null)for(long dependency:oldDependencies){
            var ids=dependents.get(dependency);ids.remove(id);if(ids.isEmpty())dependents.remove(dependency);
        }
        normalProviders.remove(id);
        var block=world.getBlockEntity(pos);
        if(block==null || block.isRemoved() || block.getClass()!=PatternProviderBlockEntity.class)return;
        providerBuilds++;
        var provider=(PatternProviderBlockEntity)block;
        var found=new ArrayList<Target>();
        providerTargets.put(id,found);
        depend(id,id);
        for(var side:Direction.values())depend(id,pos.relative(side).asLong());
            for(var pattern:provider.getLogic().getAvailablePatterns()) {
                if(pattern.getClass()!=appeng.crafting.pattern.AEProcessingPattern.class)continue;
                final Map<AEKey,BigInteger> in,out;
                try{in=PatternProgram.inputTotals(pattern);out=PatternProgram.outputTotals(pattern);}catch(IllegalArgumentException unsupported){continue;}
                var machines=Collections.newSetFromMap(new IdentityHashMap<BlockEntity,Boolean>());
                for(var side:provider.getTargets()) {
                    var adjacent=world.getBlockEntity(provider.getBlockPos().relative(side));if(adjacent!=null)machines.add(adjacent);
                }
                machines.addAll(structureProviders.getOrDefault(provider.getBlockPos(),List.of()));
                for(var machine:machines) {
                    if(!NativeWorldMachine.supports(machine))continue;
                    boolean multi=NativeMachines.multiblock(machine);
                    if(multi && !GtMultiblockAdapter.worldReaches(machine,provider))continue;
                    final MachineProfile profile;
                    try{profile=NativeWorldMachine.inspect(machine,in).orElse(null);}catch(IllegalArgumentException|ArithmeticException unsupported){continue;}
                    if(profile==null || !profile.recipe().inputs().equals(in) || !profile.recipe().outputs().equals(out))continue;
                    var delta=provider.getBlockPos().subtract(machine.getBlockPos());
                    try{NativeWorldMachine.verifyRoute(machine,Direction.getNearest(delta.getX(),delta.getY(),delta.getZ()),profile);}catch(IllegalArgumentException unsupported){continue;}
                    var target=new Target(provider.getBlockPos(),machine.getBlockPos(),pattern,profile);
                    targets.computeIfAbsent(pattern.getDefinition(),ignored->new ArrayList<>()).add(target);found.add(target);
                    for(var member:NativeWorldMachine.members(machine))depend(id,member.asLong());
                }
            }
    }
    private void depend(long provider,long position){
        dependents.computeIfAbsent(position,ignored->new HashSet<>()).add(provider);
        providerDependencies.computeIfAbsent(provider,ignored->new HashSet<>()).add(position);
    }
    private Target resolveSaved(CompoundTag tag,IPatternDetails pattern) {
        var provider=BlockPos.of(tag.getLong("provider"));var machine=BlockPos.of(tag.getLong("machine"));
        var be=world.getBlockEntity(machine);require(NativeWorldMachine.supports(be),"NATIVE_BATCH_MACHINE_UNAVAILABLE");
        var inputs=PatternProgram.inputTotals(pattern);
        var profile=NativeWorldMachine.inspect(be,inputs).orElseThrow();
        require(profile.recipe().inputs().equals(inputs) && profile.recipe().outputs().equals(PatternProgram.outputTotals(pattern)),"NATIVE_BATCH_RECIPE_CHANGED");
        return new Target(provider,machine,pattern,profile);
    }
    private boolean idle(Target t) {
        return NativeWorldMachine.idle(world.getBlockEntity(t.machine()));
    }
    public boolean accept(UUID id,String digest,IPatternDetails pattern,BigInteger count,Map<AEKey,BigInteger> inputs,Map<AEKey,BigInteger> outputs,boolean v2,String fingerprint) {
        ExactAmounts.positive(count);if(digest==null || digest.isBlank() || count.bitLength()>1048576)return false;
        var done=terminal.get(id);
        if(done!=null) {
            var job=done.runtime().find(id,digest).orElse(null);if(job==null)return false;
            require(done.tag().getBoolean("v2")==v2 && done.tag().getString("fingerprint").equals(fingerprint)
                    && AEItemKey.of(PlatformData.loadItem(done.tag().getCompound("pattern"),world)).equals(pattern.getDefinition())
                    && job.checkpoint().total().equals(count) && ExactAmounts.multiply(job.checkpoint().recipe().inputs(),count).equals(inputs)
                    && ExactAmounts.multiply(job.checkpoint().recipe().outputs(),count).equals(outputs),"NATIVE_BATCH_IDENTITY_CHANGED");
            return job.state()==BatchJob.State.ACKNOWLEDGED;
        }
        var old=entries.get(id);
        if(old!=null) {
            if(old.runtime.find(id,digest).isEmpty())return false;
            require(old.digest.equals(digest) && old.v2==v2 && old.fingerprint.equals(fingerprint) && old.target.pattern().getDefinition().equals(pattern.getDefinition())
                    && old.job().checkpoint().total().equals(count) && ExactAmounts.multiply(old.target.profile().recipe().inputs(),count).equals(inputs)
                    && ExactAmounts.multiply(old.target.profile().recipe().outputs(),count).equals(outputs),"NATIVE_BATCH_IDENTITY_CHANGED");return old.job().state()!=BatchJob.State.CANCELLED;
        }
        if(session.host().migratingAcceleration() || !session.isOnline() || session.variable()!=null && session.variable().hasId(id))return false;
        if(v2 && !com.syaru.ae2machines.world.ExternalHandoffs.supportsNativeOutput(session))return false;
        var target=target(pattern).orElse(null);if(target==null || busy(target.machine()) || !idle(target))return false;
        var recipe=target.profile().recipe();
        if(!ExactAmounts.multiply(recipe.inputs(),count).equals(inputs) || !ExactAmounts.multiply(recipe.outputs(),count).equals(outputs))return false;
        var entry=new Entry(id,digest,target,v2,fingerprint);
        if(!entry.executor.independentCopies())return false;
        try{journal.atomic(()->{
                entries.put(id,entry);
                occupy(entry);
                require(entry.runtime.accept(id,digest,pattern,count,inputs,outputs),"NATIVE_WORLD_BATCH_ADMISSION_CHANGED");
                entry.sourceAcknowledged=!v2;save();
            });
        }catch(RuntimeException failure){entries.remove(id);release(entry);throw failure;}
        return true;
    }
    public Optional<BatchJob<AEKey>> find(UUID id,String digest){var e=entries.get(id);if(e!=null)return e.runtime.find(id,digest);var t=terminal.get(id);return t==null?Optional.empty():t.runtime().find(id,digest);}
    public boolean owns(UUID id,String digest){return find(id,digest).isPresent();}
    public boolean hasId(UUID id){return entries.containsKey(id) || terminal.containsKey(id);}
    public boolean quarantined(UUID id){var e=entries.get(id);return e!=null && (e.runtime.quarantined() || e.deliveryIntent);}
    public boolean receiptReady(UUID id){var e=entries.get(id);return e==null || e.runtime.deliveryReady(id);}
    public boolean acknowledge(UUID id,String digest){var e=entries.get(id);if(e==null){var t=terminal.get(id);return t!=null && t.runtime().acknowledge(id,digest);}if(!e.runtime.acknowledge(id,digest))return false;complete(e);return true;}
    public boolean forget(UUID id,String digest){var t=terminal.get(id);if(t==null || !t.runtime().forget(id,digest))return false;t.tag().put("runtime",RuntimeCodec.write(t.runtime(),key->PlatformData.saveKey(key,world)));save();return true;}
    public boolean cancel(UUID id,String digest){var e=entries.get(id);if(e==null){var t=terminal.get(id);return t!=null && t.runtime().cancel(id,digest);}if(!e.runtime.cancel(id,digest))return false;complete(e);return true;}
    private void complete(Entry e){var tag=e.save();tag.remove("native");terminal.put(e.id,new Terminal(tag,readRuntime(tag)));entries.remove(e.id);release(e);if(e.runtime.energy().stored().signum()>0)pendingEnergy.add(e.id);else if(e.normal)terminal.remove(e.id);returnEnergy();save();}
    /** Return only unspent payment rounding, never recipe energy or a multiplied native charge. */
    private void returnEnergy() {
        for(var id:List.copyOf(pendingEnergy)) {
            var t=terminal.get(id);var machine=world.getBlockEntity(BlockPos.of(t.tag().getLong("machine")));
            if(!NativeWorldMachine.supports(machine) || !t.tag().getString("machineType").equals(net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(machine.getType()).toString()))continue;
            var profile=NativeWorldMachine.idleProfile(machine);var charge=NativeWorldMachine.energy(machine);
            var n=t.runtime().energy().stored().min(profile.capacity().subtract(charge).max(BigInteger.ZERO));
            if(n.signum()>0)journal.atomic(()->{
                t.runtime().energy().consume(n);NativeWorldMachine.energy(machine,charge.add(n));machine.setChanged();
                t.tag().put("runtime",RuntimeCodec.write(t.runtime(),key->PlatformData.saveKey(key,world)));save();
            });
            if(t.runtime().energy().stored().signum()==0){pendingEnergy.remove(id);if(t.tag().getBoolean("normal"))terminal.remove(id);}
        }
    }
    public void sourceAcknowledged(UUID id){var e=entries.get(id);if(e!=null && e.v2 && !e.sourceAcknowledged){e.sourceAcknowledged=true;save();}}
    public Optional<CompoundTag> v2Receipt(UUID id) {
        var e=entries.get(id);if(e==null){var t=terminal.get(id);if(t==null || !t.tag().getBoolean("v2"))return Optional.empty();var job=t.runtime().find(id,t.tag().getString("digest")).orElseThrow();var tag=new CompoundTag();tag.putString("digest",job.digest());tag.putString("pattern",t.tag().getString("fingerprint"));tag.putLong("executions",job.checkpoint().total().longValueExact());return Optional.of(tag);}if(!e.v2)return Optional.empty();
        var tag=new CompoundTag();tag.putString("digest",e.digest);tag.putString("pattern",e.fingerprint);tag.putLong("executions",e.job().checkpoint().total().longValueExact());return Optional.of(tag);
    }
    public BigInteger receiveExactFE(BigInteger offered,long tick,boolean simulate) {
        ExactAmounts.nonnegative(offered);var left=offered;
        final long frame=workTick();
        for(var e:entries.values())if(e.occupies() && e.sourceAcknowledged && !e.runtime.quarantined()) {
            if(e.target.profile().energyUnit().equals(MachineProfile.GT_UNIT))continue;
            var units=e.target.profile().unitsPerFE();
            var quantum=units.divide(units.gcd(e.target.profile().nativeQuantum())).multiply(e.target.profile().nativeQuantum());
            var packet=quantum.divide(units);
            var accepted=e.runtime.roundedEnergyDemand(frame,quantum).divide(units).min(left.divide(packet).multiply(packet));
            if(!simulate && accepted.signum()>0) {
                var n=accepted.multiply(units);atomicNative(e,()->require(e.runtime.receiveRoundedEnergy(n,frame,quantum,false).equals(n),"NATIVE_EXACT_POWER_CHANGED"));
            }
            left=left.subtract(accepted);if(left.signum()==0)break;
        }
        return offered.subtract(left);
    }
    /** Exact EU is still paid in whole native-voltage packets, never by an assumed FE conversion. */
    public BigInteger receiveExactEU(long voltage,BigInteger offered,long tick,boolean simulate) {
        ExactAmounts.nonnegative(offered);if(voltage<=0)return BigInteger.ZERO;
        final long frame=workTick();
        var packet=BigInteger.valueOf(voltage);var left=offered;
        for(var e:entries.values())if(e.occupies() && e.sourceAcknowledged && !e.runtime.quarantined()) {
            var p=e.target.profile();
            if(!p.energyUnit().equals(MachineProfile.GT_UNIT) || voltage>p.voltage() || packet.compareTo(p.recipe().minimumVoltage())<0)continue;
            var n=e.runtime.roundedEnergyDemand(frame,packet).min(left.divide(packet).multiply(packet));
            if(!simulate && n.signum()>0)atomicNative(e,()->require(e.runtime.receiveRoundedEnergy(n,frame,packet,false).equals(n),"NATIVE_EXACT_EU_CHANGED"));
            left=left.subtract(n);if(left.signum()==0)break;
        }
        return offered.subtract(left);
    }
    public long voltageEU(){return entries.values().stream().filter(Entry::occupies).filter(e->e.target.profile().energyUnit().equals(MachineProfile.GT_UNIT)).mapToLong(e->e.target.profile().voltage()).max().orElse(0);}
    public long demandEU(long tick){
        var total=BigInteger.ZERO;
        for(var e:entries.values())if(e.occupies() && e.sourceAcknowledged && !e.runtime.quarantined() && e.target.profile().energyUnit().equals(MachineProfile.GT_UNIT))total=total.add(e.runtime.energyDemand(workTick()));
        // This is the existing long capability's display of demand, not a job/accounting value.
        return total.min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact();
    }
    public void tick() {
        if(!session.isOnline())return;
        if(!pendingEnergy.isEmpty())returnEnergy();
        long tick=workTick();
        for(var e:List.copyOf(entries.values()))if(e.occupies() && e.sourceAcknowledged && !e.runtime.quarantined()) {
            var machine=world.getBlockEntity(e.target.machine());
            var draw=NativeWorldMachine.energy(machine).min(e.runtime.energyDemand(tick));
            atomicNative(e,()->{
                if(draw.signum()>0){NativeWorldMachine.energy(machine,NativeWorldMachine.energy(machine).subtract(draw));e.runtime.receiveEnergy(draw,tick,false);}
                e.runtime.tick(tick);
            });
            if((e.v2 || e.normal) && e.job().state()==BatchJob.State.OUTPUT_READY && e.runtime.deliveryReady(e.id))deliver(e);
        }
    }
    private void atomicNative(Entry e,Runnable action) {
        try{journal.atomic(()->nativeWork(e,()->{
            action.run();
            if(e.completion!=null && e.completion.online() && e.completion.tick(e.runtime))save();
        }));}
        catch(RuntimeException failure){e.runtime.restoreQuarantine(true);throw failure;}
    }
    private void deliver(Entry e) {
        if(e.deliveryIntent){e.runtime.restoreQuarantine(true);return;}
        var provider=(PatternProviderBlockEntity)world.getBlockEntity(e.target.provider());
        if(provider==null || !provider.getMainNode().isActive())return;
        var grid=provider.getMainNode().getGrid();var source=new MachineSource(provider);
        var transfer=com.syaru.ae2machines.world.ExternalHandoffs.beginNativeOutput(session,journal);
        try {
        for(var output:e.job().receipt().entrySet()) {
            var remaining=output.getValue().subtract(e.delivered.getOrDefault(output.getKey(),BigInteger.ZERO));if(remaining.signum()==0)continue;
            long amount=remaining.min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact();
            e.deliveryIntent=true;save();
            long accepted=grid.getStorageService().getInventory().insert(output.getKey(),amount,appeng.api.config.Actionable.MODULATE,source);
            require(accepted>=0 && accepted<=amount,"NATIVE_OUTPUT_OVER_ACCEPTED");
            if(accepted>0)e.delivered.merge(output.getKey(),BigInteger.valueOf(accepted),BigInteger::add);
            e.deliveryIntent=false;save();
        }
        if(transfer!=null)transfer.commit();
        // Returning unused native power is a separate owned-state transition. The ME decision
        // contains the complete delivered count before touching any machine energy container.
        if(e.delivered.equals(e.job().receipt()))acknowledge(e.id,e.digest);
        }catch(RuntimeException failure){
            if(transfer!=null)throw new com.syaru.ae2machines.world.ExternalHandoffs.PersistenceFailure(failure);
            throw failure;
        }
    }
    public void save() {
        if(loading)return;
        if(journal.deferCheckpoint(this,this::save))return;
        var record=journal.read();var saved=new CompoundTag();saved.putInt("schema",1);var list=new ListTag();
        for(var e:entries.values())list.add(e.save());for(var t:terminal.values())list.add(t.tag().copy());saved.put("entries",list);record.put("worldBatches",saved);
        record.remove("nativeCheckpoint");journal.write(record);
    }
    public static Optional<ProcessingRuntime> savedRuntime(ServerLevel source,UUID factory,UUID transaction,String digest) {
        var record=FactoryJournal.open(source,factory).read();if(!record.contains("worldBatches"))return Optional.empty();
        for(var value:savedEntries(record.getCompound("worldBatches"))) {
            var entry=(CompoundTag)value;if(!entry.getUUID("id").equals(transaction) || !entry.getString("digest").equals(digest))continue;
            return Optional.of(RuntimeCodec.read(entry.getCompound("runtime"),key->PlatformData.loadKey(key,source),ignored->{}));
        }
        return Optional.empty();
    }
    public static Optional<CompoundTag> savedV2Receipt(ServerLevel source,UUID factory,UUID transaction) {
        var record=FactoryJournal.open(source,factory).read();require(record.getCompound("blueprint").getInt("schema")==4,"NOT_NATIVE_BATCH_WORLD");
        if(!record.contains("worldBatches"))return Optional.empty();
        for(var value:savedEntries(record.getCompound("worldBatches"))) {
            var entry=(CompoundTag)value;if(!entry.getUUID("id").equals(transaction) || !entry.getBoolean("v2"))continue;
            var runtime=RuntimeCodec.read(entry.getCompound("runtime"),key->PlatformData.loadKey(key,source),ignored->{});
            var job=runtime.find(transaction,entry.getString("digest")).orElseThrow();
            var receipt=new CompoundTag();receipt.putString("digest",entry.getString("digest"));receipt.putString("pattern",entry.getString("fingerprint"));receipt.putLong("executions",job.checkpoint().total().longValueExact());return Optional.of(receipt);
        }
        return Optional.empty();
    }
    public static void acknowledgeSavedSource(ServerLevel source,UUID factory,UUID transaction) {
        var world=source.getServer().getLevel(NativeCellWorlds.key(factory));
        if(world!=null && NativeWorldRuntime.get(world).batches()!=null){NativeWorldRuntime.get(world).batches().sourceAcknowledged(transaction);return;}
        var journal=FactoryJournal.open(source,factory);var record=journal.read();
        if(!record.contains("worldBatches"))return;
        for(var value:savedEntries(record.getCompound("worldBatches"))) {
            var entry=(CompoundTag)value;if(entry.getUUID("id").equals(transaction) && entry.getBoolean("v2"))entry.putBoolean("sourceAcknowledged",true);
        }
        record.remove("nativeCheckpoint");journal.write(record);
    }
    private final class Entry {
        final UUID id;final String digest;final Target target;final boolean v2;final String fingerprint;
        final ProcessingRuntime runtime;boolean sourceAcknowledged=true,deliveryIntent,normal;
        NativeBatchProcessor executor;
        NativeCompletion completion;
        final Set<BlockPos> members;
        final Map<AEKey,BigInteger> delivered=new LinkedHashMap<>();
        Entry(UUID id,String digest,Target target,boolean v2,String fingerprint) {
            this.id=id;this.digest=digest;this.target=target;this.v2=v2;this.fingerprint=fingerprint;
            members=NativeWorldMachine.members(world.getBlockEntity(target.machine()));
            completion=NativeWorldMachine.completion(world.getBlockEntity(target.machine()),new CompoundTag());
            var p=target.profile();runtime=new ProcessingRuntime(new MeteredEnergy(p.energyUnit(),p.capacity(),p.inputRate(),BigInteger.valueOf(p.voltage()),BigInteger.ZERO,false),ignored->NativeWorldBatches.this.save());
            runtime.configure(session.runtime().speed(),session.runtime().lanes());bind(false);
        }
        Entry(Target target,CompoundTag tag) {
            this.id=tag.getUUID("id");this.digest=tag.getString("digest");this.target=target;this.v2=tag.getBoolean("v2");this.fingerprint=tag.getString("fingerprint");
            members=NativeWorldMachine.members(world.getBlockEntity(target.machine()));
            completion=NativeWorldMachine.completion(world.getBlockEntity(target.machine()),tag.getCompound("nativeCompletion"));
            sourceAcknowledged=tag.getBoolean("sourceAcknowledged");deliveryIntent=tag.getBoolean("deliveryIntent");normal=tag.getBoolean("normal");
            runtime=RuntimeCodec.read(tag.getCompound("runtime"),key->PlatformData.loadKey(key,world),ignored->NativeWorldBatches.this.save());
            delivered.putAll(RuntimeCodec.readAmounts(tag.getList("delivered",Tag.TAG_COMPOUND),key->PlatformData.loadKey(key,world)));
            bind(true);runtime.rebaseRecoveredTime(workTick());
            if(deliveryIntent)runtime.restoreQuarantine(true);
        }
        void bind(boolean restored) {
            var program=new PatternProgram(Map.of(target.pattern(),target.profile()),()->session.isOnline() && sourceAcknowledged && (completion==null || completion.online()));
            if(completion!=null)runtime.completionGate(completion::ready);
            var processor=processor(target);
            executor=processor;
            runtime.nativeExecution(recipe->{require(recipe.equals(target.profile().recipe()),"NATIVE_BATCH_RECIPE_CHANGED");return processor;});
            if(restored)runtime.resume(program);else runtime.bind(program);
            if(restored && runtime.snapshots().stream().anyMatch(j->j.state()==BatchJob.State.RUNNING) && !processor.independentCopies()) {
                runtime.restoreQuarantine(true);rebindQuarantined=true;
            }
        }
        BatchJob<AEKey> job(){return runtime.find(id,digest).orElseThrow();}
        boolean occupies(){return runtime.find(id,digest).map(j->j.state()==BatchJob.State.RUNNING || j.state()==BatchJob.State.OUTPUT_READY || j.state()==BatchJob.State.QUARANTINED).orElse(false);}
        CompoundTag save() {
            var tag=new CompoundTag();tag.putUUID("id",id);tag.putString("digest",digest);tag.putBoolean("v2",v2);tag.putBoolean("normal",normal);tag.putString("fingerprint",fingerprint);
            tag.putLong("provider",target.provider().asLong());tag.putLong("machine",target.machine().asLong());
            tag.put("pattern",PlatformData.saveItem(target.pattern().getDefinition().toStack(),world));
            tag.put("runtime",RuntimeCodec.write(runtime,key->PlatformData.saveKey(key,world)));
            if(completion!=null)tag.put("nativeCompletion",completion.save());
            var machine=world.getBlockEntity(target.machine());machine.setChanged();tag.put("native",PlatformData.saveBlock(machine));
            tag.putString("machineType",net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(machine.getType()).toString());
            tag.putBoolean("sourceAcknowledged",sourceAcknowledged);tag.putBoolean("deliveryIntent",deliveryIntent);
            tag.put("delivered",RuntimeCodec.amounts(delivered,key->PlatformData.saveKey(key,world)));return tag;
        }
    }
}
