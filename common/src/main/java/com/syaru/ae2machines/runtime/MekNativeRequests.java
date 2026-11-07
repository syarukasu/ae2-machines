package com.syaru.ae2machines.runtime;

import appeng.api.stacks.AEKey;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import com.syaru.ae2machines.api.VariableProcessingTarget.*;
import com.syaru.ae2machines.compat.mek.*;
import com.syaru.ae2machines.engine.ExactAmounts;
import com.syaru.ae2machines.engine.ReceiptPartition;
import com.syaru.ae2machines.platform.*;
import com.syaru.ae2machines.world.FactoryJournal;
import java.math.BigInteger;
import java.util.*;
import mekanism.common.tile.prefab.TileEntityConfigurableMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;

/** Owned variable work is separate from fixed-coefficient NativeWorkLedger. */
public final class MekNativeRequests {
    public enum Route { CALLER, EXACT, V2 }
    public record ClaimedReceipt(State state,Map<AEKey,BigInteger> outputs,String detail){}
    private final NativeWorldSession session;
    private final FactoryJournal journal;
    private final Map<UUID,Entry> entries=new LinkedHashMap<>();
    private final Map<BlockPos,List<Entry>> byMachine=new HashMap<>(),byProvider=new HashMap<>();
    private final Map<UUID,String> forgotten=new LinkedHashMap<>();
    private boolean loading=true,loadedQuarantine;
    private boolean tickBundle,dirty;
    private long nativeSteps,checkpoints;
    private long recipeRevision=FactoryRecipes.revision();
    public MekNativeRequests(NativeWorldSession session,FactoryJournal journal){
        this.session=session;this.journal=journal;var saved=journal.read().getCompound("variableMekRequests");
        if(!saved.isEmpty() && saved.getInt("schema")!=1)throw new IllegalStateException("Unknown variable Mek request schema");
        for(var value:saved.getList("entries",Tag.TAG_COMPOUND)){var e=new Entry((CompoundTag)value);if(entries.put(e.id,e)!=null)throw new IllegalStateException("Duplicate variable request");index(e);}
        for(var value:saved.getList("forgotten",Tag.TAG_COMPOUND)){var tag=(CompoundTag)value;forgotten.put(tag.getUUID("id"),tag.getString("digest"));}
        loading=false;if(loadedQuarantine)save();
    }
    public boolean busy(BlockPos pos){return occupied(byMachine.get(pos));}
    private boolean occupied(List<Entry> candidates){if(candidates!=null)for(var e:candidates)if(e.occupies())return true;return false;}
    private void index(Entry e){byMachine.computeIfAbsent(e.pos,p->new ArrayList<>()).add(e);byProvider.computeIfAbsent(e.provider,p->new ArrayList<>()).add(e);}
    private void unindex(Entry e){remove(byMachine,e.pos,e);remove(byProvider,e.provider,e);}
    private void remove(Map<BlockPos,List<Entry>> index,BlockPos pos,Entry e){var list=index.get(pos);if(list!=null){list.remove(e);if(list.isEmpty())index.remove(pos);}}
    public boolean active(){for(var e:entries.values())if(e.occupies())return true;return false;}
    public boolean requiresWorldTicks(){for(var e:entries.values())if(e.occupies() || e.state!=State.QUARANTINED && e.credit.signum()>0)return true;return false;}
    public boolean hasId(UUID id){return entries.containsKey(id) || forgotten.containsKey(id);}
    public boolean owns(UUID id,String digest){var e=entries.get(id);return e!=null && e.digest.equals(digest);}
    public boolean callerOwns(UUID id,String digest){var e=entries.get(id);return e!=null && e.route==Route.CALLER && e.digest.equals(digest);}
    public boolean forgotten(UUID id,String digest){return digest.equals(forgotten.get(id));}
    public boolean providerBusy(BlockPos pos){return occupied(byProvider.get(pos));}
    private record Candidate(BlockPos provider,TileEntityConfigurableMachine machine){}
    private List<Candidate> candidates(appeng.api.crafting.IPatternDetails pattern){
        var result=new ArrayList<Candidate>();
        if(pattern==null || pattern.getClass()!=appeng.crafting.pattern.AEProcessingPattern.class)return result;
        for(var block:session.nativeBlocks())if(block instanceof PatternProviderBlockEntity provider)
            for(var registered:provider.getLogic().getAvailablePatterns())if(registered.getDefinition().equals(pattern.getDefinition()))
                for(var side:provider.getTargets()){
                    var be=session.world().getBlockEntity(provider.getBlockPos().relative(side));
                    if(MekVariableWork.supports(be))result.add(new Candidate(provider.getBlockPos(),(TileEntityConfigurableMachine)be));
                }
        return result;
    }
    public boolean supportsExact(appeng.api.crafting.IPatternDetails pattern){
        if(!session.isOnline())return false;
        var per=PatternProgram.inputTotals(pattern);var out=PatternProgram.outputTotals(pattern);
        return candidates(pattern).stream().anyMatch(c->MekVariableWork.guarantees(c.machine,per,out));
    }
    public boolean acceptExact(Request request,Map<AEKey,BigInteger> expected){return accept(request,Route.EXACT,"",expected);}
    public boolean acceptV2(Request request,Map<AEKey,BigInteger> expected,String fingerprint){return accept(request,Route.V2,fingerprint,expected);}
    public boolean accept(Request request){
        return accept(request,Route.CALLER,"",Map.of());
    }
    private boolean accept(Request request,Route route,String fingerprint,Map<AEKey,BigInteger> expected){
        flush();
        var old=entries.get(request.id());if(old!=null)return old.matches(request) && old.route==route && old.fingerprint.equals(fingerprint) && old.expected.equals(expected) && old.state!=State.CANCELLED;
        if(session.host().migratingAcceleration())return false;
        if(session.batches().hasId(request.id()))return false;
        if(forgotten.containsKey(request.id()) || !session.isOnline() || request.executions().bitLength()>1048576)return false;
        // V2 owns CPU inputs; refuse custody when its output cannot be committed safely.
        if(route==Route.V2 && !com.syaru.ae2machines.world.ExternalHandoffs.supportsNativeOutput(session))return false;
        var per=PatternProgram.inputTotals(request.pattern());
        if(!ExactAmounts.multiply(per,request.executions()).equals(request.ownedInputs()))return false;
        if(route!=Route.CALLER && !ExactAmounts.multiply(PatternProgram.outputTotals(request.pattern()),request.executions()).equals(expected))return false;
        for(var candidate:candidates(request.pattern())){
                    var machine=candidate.machine;
                    if(session.batches().busy(machine.getBlockPos()) || busy(machine.getBlockPos()))continue;
                    if(machine.getInventorySlots(null).stream().anyMatch(s->!s.isEmpty()) || machine.getFluidTanks(null).stream().anyMatch(t->!t.isEmpty())
                            || !MekChemicalWork.chemicalsEmpty(machine) || machine.getControlType()!=mekanism.common.tile.interfaces.IRedstoneControl.RedstoneControl.DISABLED)continue;
                    if(java.util.stream.IntStream.range(0,MekRecipeMachines.processes(machine)).anyMatch(i->MekWorldParallel.progress(machine,i)>0))continue;
                    if(route!=Route.CALLER && !MekVariableWork.guarantees(machine,per,PatternProgram.outputTotals(request.pattern())))continue;
                    final Entry e;try{e=new Entry(request,machine,candidate.provider,per,route,fingerprint,expected);}catch(MekVariableWork.NoMatchingRecipe unmatched){continue;}
                    journal.atomic(()->{entries.put(e.id,e);index(e);save();});return true;
                }
        return false;
    }
    public Optional<ClaimedReceipt> exactReceipt(UUID id,String digest){
        flush();var e=entries.get(id);if(e==null || e.route!=Route.EXACT || !e.digest.equals(digest))return Optional.empty();
        var state=e.state;
        if(state==State.ACKNOWLEDGED && !e.completed.equals(e.total))state=State.CANCELLED;
        if(state==State.OUTPUT_READY || state==State.ACKNOWLEDGED)e.partition();
        return Optional.of(new ClaimedReceipt(state,state==State.OUTPUT_READY || state==State.ACKNOWLEDGED?e.expected:Map.of(),"actual_native_claimed_receipt"));
    }
    public void sourceAcknowledged(UUID id){var e=entries.get(id);if(e!=null && e.route==Route.V2 && !e.sourceAcknowledged){e.sourceAcknowledged=true;save();}}
    public Optional<CompoundTag> v2Receipt(UUID id){var e=entries.get(id);if(e==null || e.route!=Route.V2)return Optional.empty();var tag=new CompoundTag();tag.putString("digest",e.digest);tag.putString("pattern",e.fingerprint);tag.putLong("executions",e.total.longValueExact());return Optional.of(tag);}
    public void dispatchOutputs(){
        for(var e:List.copyOf(entries.values()))if((e.state==State.OUTPUT_READY || e.route==Route.EXACT && e.state==State.ACKNOWLEDGED) && e.sourceAcknowledged){
            if(e.route==Route.EXACT)deliverAmounts(e,e.partition().surplus());
            else if(e.route==Route.V2)deliver(e.id,e.digest);
        }
    }
    public Optional<Snapshot> snapshot(UUID id,String digest){flush();var e=entries.get(id);return e==null || !e.digest.equals(digest)?Optional.empty():Optional.of(e.snapshot());}
    public static Optional<Snapshot> durableSnapshot(net.minecraft.server.level.ServerLevel level,UUID factory,UUID id,String digest){
        var saved=FactoryJournal.open(level,factory).read().getCompound("variableMekRequests");
        for(var value:saved.getList("entries",Tag.TAG_COMPOUND)){
            var tag=(CompoundTag)value;if(!tag.getUUID("id").equals(id) || !tag.getString("digest").equals(digest))continue;
            java.util.function.Function<String,Map<AEKey,BigInteger>> read=key->RuntimeCodec.readAmounts(tag.getList(key,Tag.TAG_COMPOUND),k->PlatformData.loadKey(k,level));
            var state=State.valueOf(tag.getString("state"));
            return Optional.of(new Snapshot(id,digest,state==State.ACKNOWLEDGED?state:State.QUARANTINED,positive(tag,"total"),number(tag,"completed"),read.apply("consumed"),read.apply("produced"),state==State.RUNNING?Map.of():read.apply("pool"),read.apply("delivered"),number(tag,"used"),number(tag,"credit"),"J/10000"));
        }
        return Optional.empty();
    }
    private static Optional<CompoundTag> savedEntry(net.minecraft.server.level.ServerLevel level,UUID factory,UUID id){
        var saved=FactoryJournal.open(level,factory).read().getCompound("variableMekRequests");
        for(var value:saved.getList("entries",Tag.TAG_COMPOUND)){var tag=(CompoundTag)value;if(tag.getUUID("id").equals(id))return Optional.of(tag);}
        return Optional.empty();
    }
    public static Optional<ClaimedReceipt> durableExactReceipt(net.minecraft.server.level.ServerLevel level,UUID factory,UUID id,String digest){
        return savedEntry(level,factory,id).filter(tag->tag.getString("route").equals(Route.EXACT.name()) && tag.getString("digest").equals(digest)).map(tag->{
            var state=State.valueOf(tag.getString("state"));
            if(state==State.ACKNOWLEDGED && !number(tag,"completed").equals(positive(tag,"total")))return new ClaimedReceipt(State.CANCELLED,Map.of(),"durable_unstarted_cancellation");
            if(state!=State.ACKNOWLEDGED)return new ClaimedReceipt(State.QUARANTINED,Map.of(),"native_world_reconciliation_required");
            var expected=RuntimeCodec.readAmounts(tag.getList("expected",Tag.TAG_COMPOUND),k->PlatformData.loadKey(k,level));
            var produced=RuntimeCodec.readAmounts(tag.getList("produced",Tag.TAG_COMPOUND),k->PlatformData.loadKey(k,level));ReceiptPartition.of(produced,expected);
            return new ClaimedReceipt(state,expected,"durable_actual_native_claimed_receipt");
        });
    }
    public static Optional<CompoundTag> savedV2Receipt(net.minecraft.server.level.ServerLevel level,UUID factory,UUID id){
        return savedEntry(level,factory,id).filter(tag->tag.getString("route").equals(Route.V2.name())).map(tag->{var receipt=new CompoundTag();receipt.putString("digest",tag.getString("digest"));receipt.putString("pattern",tag.getString("fingerprint"));receipt.putLong("executions",positive(tag,"total").longValueExact());return receipt;});
    }
    public static void acknowledgeSavedSource(net.minecraft.server.level.ServerLevel level,UUID factory,UUID id){
        var world=level.getServer().getLevel(com.syaru.ae2machines.world.NativeCellWorlds.key(factory));
        if(world!=null && com.syaru.ae2machines.world.NativeWorldRuntime.get(world).variable()!=null){com.syaru.ae2machines.world.NativeWorldRuntime.get(world).variable().sourceAcknowledged(id);return;}
        var journal=FactoryJournal.open(level,factory);var record=journal.read();
        for(var value:record.getCompound("variableMekRequests").getList("entries",Tag.TAG_COMPOUND)){
            var tag=(CompoundTag)value;if(tag.getUUID("id").equals(id) && tag.getString("route").equals(Route.V2.name())){tag.putBoolean("sourceAcknowledged",true);record.remove("nativeCheckpoint");journal.write(record);return;}
        }
    }
    public boolean cancel(UUID id,String digest){return cancel(id,digest,false);}
    public boolean cancelExact(UUID id,String digest){return cancel(id,digest,true);}
    private boolean cancel(UUID id,String digest,boolean exact){flush();var e=entries.get(id);if(e==null || !e.digest.equals(digest) || (e.route==Route.EXACT)!=exact)return false;if(e.state==State.CANCELLED)return true;
        if(e.state!=State.RUNNING || e.used.signum()!=0 || e.completed.signum()!=0 || !e.consumed().isEmpty())return false;
        e.work.returnInputs().forEach((key,n)->e.pool.merge(key,n,BigInteger::add));e.state=State.CANCELLED;save();return true;}
    public boolean acknowledge(UUID id,String digest){return acknowledge(id,digest,false);}
    public boolean acknowledgeExact(UUID id,String digest){return acknowledge(id,digest,true);}
    private boolean acknowledge(UUID id,String digest,boolean exact){flush();var e=entries.get(id);if(e==null || !e.digest.equals(digest) || (e.route==Route.EXACT)!=exact)return false;if(e.state==State.ACKNOWLEDGED)return true;
        if(exact && !e.completed.equals(e.total))return false;
        if(e.state!=State.OUTPUT_READY && e.state!=State.CANCELLED)return false;
        if(e.route==Route.EXACT){if(e.state==State.OUTPUT_READY)e.partition();else if(!e.delivered.isEmpty())return false;}
        else if(!e.delivered.isEmpty() && !e.delivered.equals(e.receipt()))return false;
        e.state=State.ACKNOWLEDGED;save();return true;}
    public boolean deliver(UUID id,String digest){
        flush();
        var e=entries.get(id);if(e==null || !e.digest.equals(digest) || !session.isOnline())return false;
        if(e.route==Route.EXACT)return false;
        if(e.state==State.ACKNOWLEDGED)return true;if(e.state!=State.OUTPUT_READY && e.state!=State.CANCELLED)return false;
        return deliverAmounts(e,e.receipt()) && acknowledge(id,digest);
    }
    private boolean deliverAmounts(Entry e,Map<AEKey,BigInteger> outputs){
        ReceiptPartition.of(outputs,e.delivered);
        if(e.delivered.equals(outputs))return true;
        var transfer=com.syaru.ae2machines.world.ExternalHandoffs.beginNativeOutput(session,journal);if(transfer==null)return false;
        try{
            var grid=session.mainGrid();
            if(grid==null || !(session.world().getBlockEntity(e.provider) instanceof PatternProviderBlockEntity provider)
                    || provider.getMainNode().getGrid()!=grid)return false;
            var inventory=grid.getStorageService().getInventory();var source=new appeng.me.helpers.MachineSource(provider);
            for(var output:outputs.entrySet()){
                var remaining=output.getValue().subtract(e.delivered.getOrDefault(output.getKey(),BigInteger.ZERO));if(remaining.signum()==0)continue;
                long amount=remaining.min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact();
                long accepted=inventory.insert(output.getKey(),amount,appeng.api.config.Actionable.MODULATE,source);
                if(accepted<0 || accepted>amount)throw new IllegalStateException("Variable output over-accepted");
                if(accepted>0)e.delivered.merge(output.getKey(),BigInteger.valueOf(accepted),BigInteger::add);
            }
            save();transfer.commit();return e.delivered.equals(outputs);
        }catch(RuntimeException failure){throw new com.syaru.ae2machines.world.ExternalHandoffs.PersistenceFailure(failure);}
    }
    public boolean forget(UUID id,String digest){flush();if(forgotten.containsKey(id))return forgotten.get(id).equals(digest);var e=entries.get(id);
        if(e==null || !e.digest.equals(digest) || e.state!=State.ACKNOWLEDGED || e.credit.signum()!=0)return false;
        if(e.route==Route.EXACT && e.completed.equals(e.total) && !e.delivered.equals(e.partition().surplus()))return false;
        entries.remove(id);unindex(e);forgotten.put(id,digest);save();return true;}
    public BigInteger receiveFE(BigInteger offered,boolean simulate){
        if(!simulate)flush();
        ExactAmounts.nonnegative(offered);var left=offered;
        for(var e:entries.values())if(e.state==State.RUNNING && e.sourceAcknowledged && e.work.perOperation().signum()>0){
            var unit=e.work.unit();var demand=e.work.perOperation().multiply(BigInteger.valueOf(e.window())).multiply(session.runtime().speed()).subtract(e.credit).max(BigInteger.ZERO);
            var packet=e.work.quantum().divide(unit.gcd(e.work.quantum()));var packetUnits=unit.multiply(packet);
            var n=demand.add(packetUnits).subtract(BigInteger.ONE).divide(packetUnits).multiply(packet).min(left.divide(packet).multiply(packet));
            if(!simulate && n.signum()>0){e.credit=e.credit.add(n.multiply(unit));save();}
            left=left.subtract(n);if(left.signum()==0)break;
        }
        return offered.subtract(left);
    }
    public void tick(){
        if(com.syaru.ae2machines.world.NativeCircuitOptions.ENABLED && entries.isEmpty())return;
        if(!session.isOnline())return;
        if(recipeRevision!=FactoryRecipes.revision()){
            for(var e:entries.values())if(e.state==State.RUNNING && !e.work.recipesUnchanged()){e.state=State.QUARANTINED;save();}
            recipeRevision=FactoryRecipes.revision();
        }
        for(var e:entries.values())if(e.state!=State.RUNNING && e.state!=State.QUARANTINED && e.credit.signum()>0){
            var machine=session.world().getBlockEntity(e.pos);var profile=NativeWorldMachine.idleProfile(machine);var stored=NativeWorldMachine.energy(machine);
            var refund=e.credit.min(profile.capacity().subtract(stored).max(BigInteger.ZERO));
            if(refund.signum()>0)journal.atomic(()->{NativeWorldMachine.energy(machine,stored.add(refund));e.credit=e.credit.subtract(refund);save();});
        }
        for(var e:entries.values())if(e.state==State.RUNNING && e.sourceAcknowledged){
            try{
                if(session.internalEndpoints()){
                    var machine=session.world().getBlockEntity(e.pos);var stored=NativeWorldMachine.energy(machine);
                    var demand=e.work.perOperation().multiply(BigInteger.valueOf(e.window())).multiply(session.runtime().speed()).subtract(e.credit).max(BigInteger.ZERO);
                    var draw=stored.min(demand);
                    if(draw.signum()>0)journal.atomic(()->{NativeWorldMachine.energy(machine,stored.subtract(draw));e.credit=e.credit.add(draw);save();});
                }
                var result=e.work.stepWide(e.pool,e.per,e.total.subtract(e.completed),session.runtime().lanes(),e.credit);
                nativeSteps++;
                e.credit=e.credit.subtract(result.used());e.used=e.used.add(result.used());e.completed=e.completed.add(result.completed());
                if(e.completed.compareTo(e.total)>0)throw new IllegalStateException("Native request over-completed");
                result.outputs().forEach((key,n)->e.produced.merge(key,n,BigInteger::add));
                if(e.completed.equals(e.total)){e.work.returnInputs().forEach((key,n)->e.pool.merge(key,n,BigInteger::add));if(e.route!=Route.CALLER)e.partition();e.state=State.OUTPUT_READY;}
                e.consumed();dirty=true;
                if(tickBundle)journal.stageOwnedCheckpoint(this,this::save);else flush();
            }catch(RuntimeException|Error failure){e.state=State.QUARANTINED;save();throw failure;}
        }
    }
    /** Only private scratch work is batched. External world/ME transactions never enter this scope. */
    public void beginTickBundle(){if(tickBundle)throw new IllegalStateException("Nested variable tick bundle");tickBundle=true;}
    public void endTickBundle(){try{flush();}finally{tickBundle=false;}}
    public void flush(){if(dirty)save();}
    public long nativeSteps(){return nativeSteps;}
    public long checkpoints(){return checkpoints;}
    public void save(){
        if(loading)return;journal.removeStagedCheckpoint(this);if(journal.deferCheckpoint(this,this::save))return;
        var saved=new CompoundTag();saved.putInt("schema",1);var list=new ListTag();entries.values().forEach(e->list.add(e.save()));saved.put("entries",list);
        var old=new ListTag();forgotten.forEach((id,digest)->{var tag=new CompoundTag();tag.putUUID("id",id);tag.putString("digest",digest);old.add(tag);});saved.put("forgotten",old);
        var record=journal.read();record.put("variableMekRequests",saved);record.remove("nativeCheckpoint");journal.write(record);dirty=false;checkpoints++;
    }
    private final class Entry {
        final UUID id;final String digest;final BlockPos pos,provider;final BigInteger total;final Map<AEKey,BigInteger> initial,per,expected;
        final Route route;final String fingerprint;boolean sourceAcknowledged;
        final AEKey definition;final String machineType;final appeng.api.stacks.GenericStack primary;
        final Map<AEKey,BigInteger> pool=new LinkedHashMap<>(),produced=new LinkedHashMap<>(),delivered=new LinkedHashMap<>();
        BigInteger completed=BigInteger.ZERO,used=BigInteger.ZERO,credit=BigInteger.ZERO;State state=State.RUNNING;
        final MekVariableWork work;
        CompoundTag stoppedWork;
        Entry(Request request,TileEntityConfigurableMachine machine,BlockPos provider,Map<AEKey,BigInteger> per,Route route,String fingerprint,Map<AEKey,BigInteger> expected){
            id=request.id();digest=request.digest();pos=machine.getBlockPos();total=request.executions();initial=request.ownedInputs();this.per=per;pool.putAll(initial);
            this.provider=provider;this.route=route;this.fingerprint=fingerprint;this.expected=ExactAmounts.freeze(expected);sourceAcknowledged=route!=Route.V2;
            definition=request.pattern().getDefinition();machineType=net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(machine.getType()).toString();
            primary=primary(request.pattern());
            work=MekVariableWork.create(machine,new CompoundTag(),pool,per,primary,total,session.runtime().lanes(),this::available);
        }
        Entry(CompoundTag tag){
            id=tag.getUUID("id");digest=tag.getString("digest");pos=BlockPos.of(tag.getLong("pos"));total=positive(tag,"total");completed=number(tag,"completed");used=number(tag,"used");credit=number(tag,"credit");state=State.valueOf(tag.getString("state"));
            initial=amounts(tag,"initial");per=amounts(tag,"per");pool.putAll(amounts(tag,"pool"));produced.putAll(amounts(tag,"produced"));delivered.putAll(amounts(tag,"delivered"));
            provider=tag.contains("provider")?BlockPos.of(tag.getLong("provider")):pos;route=tag.contains("route")?Route.valueOf(tag.getString("route")):Route.CALLER;
            fingerprint=tag.getString("fingerprint");expected=amounts(tag,"expected");sourceAcknowledged=route!=Route.V2 || tag.getBoolean("sourceAcknowledged");
            definition=PlatformData.loadKey(tag.getCompound("definition"),session.world());machineType=tag.getString("machineType");
            var declared=amounts(tag,"primary");if(declared.size()!=1)throw new IllegalStateException("Owned primary output missing");var expected=declared.entrySet().iterator().next();primary=new appeng.api.stacks.GenericStack(expected.getKey(),expected.getValue().longValueExact());
            var source=session.world().getBlockEntity(pos);
            if(!MekVariableWork.supports(source) || !net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(source.getType()).toString().equals(machineType))throw new IllegalStateException("Owned variable machine changed");
            var machine=(TileEntityConfigurableMachine)source;
            if(session.internalEndpoints() && occupies() && tag.contains("sourceNative"))PlatformData.loadBlock(machine,tag.getCompound("sourceNative"));
            if(state==State.QUARANTINED)stoppedWork=tag.getCompound("work").copy();
            var before=new LinkedHashMap<>(pool);MekVariableWork restored;
            try{restored=MekVariableWork.create(machine,tag.getCompound("work"),pool,per,primary,state==State.RUNNING?total.subtract(completed):BigInteger.ZERO,session.runtime().lanes(),this::available);}
            catch(MekVariableWork.RecipeChanged|MekVariableWork.NoMatchingRecipe changed){
                pool.clear();pool.putAll(before);state=State.QUARANTINED;loadedQuarantine=true;stoppedWork=tag.getCompound("work").copy();var stopped=stoppedWork.copy();stopped.putBoolean("terminal",true);
                restored=MekVariableWork.create(machine,stopped,pool,per,primary,BigInteger.ZERO,session.runtime().lanes(),this::available);
            }
            work=restored;
            consumed();
        }
        int window(){return work!=null && work.aggregated()?1:total.subtract(completed).min(session.runtime().lanes()).min(BigInteger.valueOf(Integer.MAX_VALUE)).intValueExact();}
        int available(){return Math.max(0,total.subtract(completed).min(BigInteger.valueOf(Integer.MAX_VALUE)).intValueExact()-(work==null?0:work.reserved()));}
        boolean matches(Request request){return digest.equals(request.digest()) && definition.equals(request.pattern().getDefinition()) && primary.equals(primary(request.pattern())) && total.equals(request.executions()) && initial.equals(request.ownedInputs()) && per.equals(PatternProgram.inputTotals(request.pattern()));}
        Map<AEKey,BigInteger> consumed(){
            var remaining=new LinkedHashMap<>(pool);work.inputs().forEach((key,n)->remaining.merge(key,n,BigInteger::add));var result=new LinkedHashMap<AEKey,BigInteger>();
            for(var entry:initial.entrySet()){var n=entry.getValue().subtract(remaining.getOrDefault(entry.getKey(),BigInteger.ZERO));if(n.signum()<0)throw new IllegalStateException("Variable input grew");if(n.signum()>0)result.put(entry.getKey(),n);}
            if(!initial.keySet().containsAll(remaining.keySet()))throw new IllegalStateException("Unowned variable input");return Map.copyOf(result);
        }
        Snapshot snapshot(){return new Snapshot(id,digest,state,total,completed,consumed(),produced,state==State.OUTPUT_READY || state==State.CANCELLED || state==State.ACKNOWLEDGED?pool:Map.of(),delivered,used,credit,"J/10000");}
        Map<AEKey,BigInteger> receipt(){var result=new LinkedHashMap<>(produced);pool.forEach((key,n)->result.merge(key,n,BigInteger::add));return result;}
        ReceiptPartition<AEKey> partition(){ReceiptPartition.of(produced,expected);return ReceiptPartition.of(receipt(),expected);}
        boolean occupies(){return state==State.RUNNING || state==State.OUTPUT_READY || state==State.QUARANTINED || route==Route.EXACT && state==State.ACKNOWLEDGED && completed.equals(total) && !delivered.equals(partition().surplus());}
        CompoundTag save(){var tag=new CompoundTag();tag.putUUID("id",id);tag.putString("digest",digest);tag.putLong("pos",pos.asLong());tag.putString("state",state.name());
            if(session.internalEndpoints() && occupies())tag.put("sourceNative",PlatformData.saveBlock(session.world().getBlockEntity(pos)));
            tag.putLong("provider",provider.asLong());tag.putString("route",route.name());tag.putString("fingerprint",fingerprint);tag.putBoolean("sourceAcknowledged",sourceAcknowledged);put(tag,"expected",expected);
            tag.put("definition",PlatformData.saveKey(definition,session.world()));tag.putString("machineType",machineType);
            put(tag,"primary",Map.of(primary.what(),BigInteger.valueOf(primary.amount())));
            put(tag,"total",total);put(tag,"completed",completed);put(tag,"used",used);put(tag,"credit",credit);put(tag,"initial",initial);put(tag,"per",per);put(tag,"pool",pool);put(tag,"produced",produced);put(tag,"delivered",delivered);put(tag,"consumed",consumed());var nativeState=stoppedWork==null?work.save():stoppedWork.copy();nativeState.putBoolean("terminal",state!=State.RUNNING);tag.put("work",nativeState);return tag;}
    }
    private Map<AEKey,BigInteger> amounts(CompoundTag tag,String key){return RuntimeCodec.readAmounts(tag.getList(key,Tag.TAG_COMPOUND),k->PlatformData.loadKey(k,session.world()));}
    private static appeng.api.stacks.GenericStack primary(appeng.api.crafting.IPatternDetails pattern){for(var output:pattern.getOutputs())if(output!=null)return output;throw new IllegalArgumentException("Processing output missing");}
    private void put(CompoundTag tag,String key,Map<AEKey,BigInteger> values){tag.put(key,RuntimeCodec.amounts(values,k->PlatformData.saveKey(k,session.world())));}
    private static void put(CompoundTag tag,String key,BigInteger n){tag.putByteArray(key,n.toByteArray());}
    private static BigInteger number(CompoundTag tag,String key){return ExactAmounts.nonnegative(new BigInteger(tag.getByteArray(key)));}
    private static BigInteger positive(CompoundTag tag,String key){return ExactAmounts.positive(number(tag,key));}
}
