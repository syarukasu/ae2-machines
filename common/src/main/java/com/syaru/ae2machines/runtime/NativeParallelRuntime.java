package com.syaru.ae2machines.runtime;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import com.syaru.ae2machines.api.NativeParallelMachine;
import com.syaru.ae2machines.api.NativeParallelMachine.*;
import com.syaru.ae2machines.platform.PlatformData;
import com.syaru.ae2machines.world.*;
import java.math.BigInteger;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.*;
import static com.syaru.ae2machines.world.NativeFactoryCompiler.require;

/** Identical native states share a representative and an exact multiplicity. */
public final class NativeParallelRuntime {
    public record Snapshot(int lanes,BigInteger nativeTicks,BigInteger worldTicks,BigInteger energyReceived,
            BigInteger energyExtracted,BigInteger consumed,BigInteger generated,Assets assets,boolean quarantined){}
    public record ExactSnapshot(BigInteger lanes,BigInteger nativeTicks,BigInteger worldTicks,BigInteger energyReceived,
            BigInteger energyExtracted,BigInteger consumed,BigInteger generated,Assets assets,boolean quarantined){}
    public record Grouping(BigInteger copies,int representatives,BigInteger nativeCalls){}
    private final NativeWorldSession session;
    private final FactoryJournal journal;
    private final Map<BlockPos,Pool> pools=new LinkedHashMap<>();
    private boolean loading=true,quarantined;
    public NativeParallelRuntime(NativeWorldSession session,FactoryJournal journal){
        this.session=session;this.journal=journal;
        var saved=journal.read().getCompound("genericNativeParallel");
        if(!saved.isEmpty()){
            require(saved.getInt("schema")==1 || saved.getInt("schema")==2,"UNKNOWN_GENERIC_NATIVE_SCHEMA");quarantined=saved.getBoolean("quarantined");
            for(var value:saved.getList("machines",Tag.TAG_COMPOUND)){
                var tag=(CompoundTag)value;var pos=BlockPos.of(tag.getLong("pos"));
                var source=session.world().getBlockEntity(pos);
                require(source instanceof NativeParallelMachine,"NATIVE_PARALLEL_CONTRACT_MISSING");
                require(image(source).equals(tag.getCompound("primary")),"NATIVE_PARALLEL_PRIMARY_WORLD_MISMATCH");
                var pool=new Pool(source);pool.load(tag);require(pool.count().equals(session.runtime().lanes()),"NATIVE_PARALLEL_SAVED_SETTING_MISMATCH");require(pools.put(pos,pool)==null,"DUPLICATE_NATIVE_PARALLEL_MACHINE");
            }
        }
        loading=false;
    }
    public static NativeParallelRuntime get(BlockEntity source){
        if(source==null || !(source.getLevel() instanceof net.minecraft.server.level.ServerLevel world) || !NativeCellWorlds.owns(world))return null;
        return NativeWorldRuntime.get(world).generic();
    }
    public static boolean owns(BlockEntity source){var r=get(source);return r!=null && !r.session.closed() && r.pools.containsKey(source.getBlockPos()) && r.pools.get(source.getBlockPos()).source==source && r.session.world().getBlockEntity(source.getBlockPos())==source;}
    public static boolean tickOwned(BlockEntity source){
        var r=get(source);if(r==null || !owns(source))return false;
        if(r.session.isOnline() && !r.quarantined)try{r.mutate(()->{r.pools.get(source.getBlockPos()).tick();return null;});}
        catch(RuntimeException error){org.slf4j.LoggerFactory.getLogger(NativeParallelRuntime.class).error("Native Q paused after restoring owned states at {}",source.getBlockPos(),error);}
        return true;
    }
    public static MEStorage storage(BlockEntity source,Direction side){var r=get(source);return r==null || !owns(source)?null:r.pools.get(source.getBlockPos()).storage(side);}
    public static NativeWorldPower.Fe fePort(BlockEntity source,net.minecraft.core.Direction side,boolean extraction){
        var r=get(source);if(r==null || !owns(source))return null;var p=r.pools.get(source.getBlockPos());
        if(side!=null && !(extraction?p.contract.energyOutput(side):p.contract.energyInput(side)))return null;
        if(side==null && java.util.Arrays.stream(net.minecraft.core.Direction.values()).noneMatch(d->extraction?p.contract.energyOutput(d):p.contract.energyInput(d)))return null;
        var unit=p.contract.unitsPerFE();require(unit.signum()>0,"INVALID_NATIVE_FE_RATIO");
        return new NativeWorldPower.Fe(p,(amount,simulate)->{
            var accepted=r.energy(p,BigInteger.valueOf(amount).multiply(unit),simulate,extraction,side);
            require(accepted.remainder(unit).signum()==0,"NATIVE_FE_FRACTIONAL_ACCEPTANCE");return accepted.divide(unit).longValueExact();
        },()->p.assets().energy().divide(unit).min(BigInteger.valueOf(Integer.MAX_VALUE)).intValueExact());
    }
    public Snapshot snapshot(BlockPos pos){var p=exactSnapshot(pos);return new Snapshot(p.lanes().intValueExact(),p.nativeTicks(),p.worldTicks(),p.energyReceived(),p.energyExtracted(),p.consumed(),p.generated(),p.assets(),p.quarantined());}
    public ExactSnapshot exactSnapshot(BlockPos pos){var p=Objects.requireNonNull(pools.get(pos));return new ExactSnapshot(p.count(),p.nativeTicks,p.worldTicks,p.received,p.extracted,p.consumed,p.generated,p.assets(),quarantined);}
    public Grouping grouping(BlockPos pos){var p=Objects.requireNonNull(pools.get(pos));return new Grouping(p.count(),p.groups.size(),p.nativeCalls);}
    public boolean quarantined(){return quarantined;}
    public boolean intact(){return pools.values().stream().allMatch(p->!p.source.isRemoved() && session.world().getBlockEntity(p.source.getBlockPos())==p.source);}
    public BigInteger insertEnergy(BlockPos pos,BigInteger amount,boolean simulate){return energy(Objects.requireNonNull(pools.get(pos)),amount,simulate,false);}
    public BigInteger extractEnergy(BlockPos pos,BigInteger amount,boolean simulate){return energy(Objects.requireNonNull(pools.get(pos)),amount,simulate,true);}
    public BigInteger insertEnergy(BlockPos pos,Direction side,BigInteger amount,boolean simulate){var p=Objects.requireNonNull(pools.get(pos));return side!=null && !p.contract.energyInput(side)?BigInteger.ZERO:energy(p,amount,simulate,false,side);}
    public BigInteger extractEnergy(BlockPos pos,Direction side,BigInteger amount,boolean simulate){var p=Objects.requireNonNull(pools.get(pos));return side!=null && !p.contract.energyOutput(side)?BigInteger.ZERO:energy(p,amount,simulate,true,side);}
    public boolean hasTargets(){return session.nativeBlocks().stream().anyMatch(NativeParallelMachine.class::isInstance);}
    public boolean needsInitialization(){return session.nativeBlocks().stream().anyMatch(be->be instanceof NativeParallelMachine && !pools.containsKey(be.getBlockPos()));}
    public boolean canConfigure(BigInteger count){
        if(count.signum()<1 || quarantined)return false;
        try{
            for(var be:session.nativeBlocks())if(be instanceof NativeParallelMachine){
                var p=pools.get(be.getBlockPos());if(p==null)p=new Pool(be);
                require(count.equals(BigInteger.ONE) || p.contract.independentCopies(be),"NATIVE_PARALLEL_GROUP_CONTRACT_REQUIRED");
                if(count.compareTo(p.count())>0)p.empty();
                var removable=BigInteger.ZERO;for(var g:p.groups)if(g.lane()!=be && p.audit(g.lane()).empty())removable=removable.add(g.copies());
                require(p.count().subtract(count).compareTo(removable)<=0,"DRAIN_NATIVE_PARALLEL_LANES_BEFORE_SHRINK");
            }
            return true;
        }catch(RuntimeException unsupported){return false;}
    }
    public void configure(BigInteger count){
        require(canConfigure(count),"NATIVE_PARALLEL_CONFIGURATION_UNSUPPORTED");
        mutate(()->{
            for(var be:session.nativeBlocks())if(be instanceof NativeParallelMachine){
                var p=pools.computeIfAbsent(be.getBlockPos(),ignored->new Pool(be));var before=p.assets();
                var difference=count.subtract(p.count());
                if(difference.signum()>0)p.groups.add(new Group(p.empty(),difference));
                else if(difference.signum()<0){
                    var left=difference.negate();var reduced=new ArrayList<Group>();
                    for(var g:p.groups){var remove=g.lane()!=be && p.audit(g.lane()).empty()?left.min(g.copies()):BigInteger.ZERO;left=left.subtract(remove);if(g.copies().compareTo(remove)>0)reduced.add(new Group(g.lane(),g.copies().subtract(remove)));}
                    require(left.signum()==0,"DRAIN_NATIVE_PARALLEL_LANES_BEFORE_SHRINK");p.groups=reduced;
                }
                require(p.assets().equals(before),"NATIVE_PARALLEL_RESIZE_CHANGED_ASSETS");
            }
            return null;
        });
    }
    private BigInteger energy(Pool p,BigInteger offered,boolean simulate,boolean extraction){
        return energy(p,offered,simulate,extraction,null);
    }
    private BigInteger energy(Pool p,BigInteger offered,boolean simulate,boolean extraction,Direction side){
        require(offered.signum()>=0,"NEGATIVE_NATIVE_ENERGY");
        if(session.closed() || quarantined)return BigInteger.ZERO;
        if(simulate)return inspect(()->p.energy(offered,true,extraction,side));
        if(inspect(()->p.energy(offered,true,extraction,side)).signum()>0)NativeWorldRuntime.get(session.world()).externalPower();
        return mutate(()->p.energy(offered,false,extraction,side));
    }
    private <T>T mutate(Supplier<T> action){
        if(!session.world().getServer().isSameThread())throw new IllegalStateException("Native Q requires server thread");
        require(!session.closed() && intact(),"NATIVE_PARALLEL_OWNERSHIP_CHANGED");
        NativeWorldCheckpoint.beforeOwnedWork(session.world().getServer(),journal.id());
        require(!quarantined,"NATIVE_PARALLEL_QUARANTINED");var before=saveTag();var result=new Object[1];
        try{journal.atomic(()->{result[0]=action.get();for(var p:pools.values()){p.merge();p.markPrimaryDirty();}save();});}
        catch(RuntimeException|Error error){
            // Restore live owned states too, not just the journal's before-image.
            restore(before);quarantined=true;save();throw error;
        }
        @SuppressWarnings("unchecked")T typed=(T)result[0];return typed;
    }
    private void restore(CompoundTag before){
        var positions=new HashSet<BlockPos>();
        for(var value:before.getList("machines",Tag.TAG_COMPOUND)){var tag=(CompoundTag)value;var pos=BlockPos.of(tag.getLong("pos"));positions.add(pos);pools.get(pos).load(tag);}
        pools.keySet().retainAll(positions);
        for(var pool:pools.values())pool.publishPrimaryState();
    }
    private <T>T inspect(Supplier<T> action){
        if(!session.world().getServer().isSameThread())throw new IllegalStateException("Native Q requires server thread");
        var before=saveTag();
        try{var result=action.get();require(before.equals(saveTag()),"NATIVE_PARALLEL_SIMULATION_MUTATED");return result;}
        catch(RuntimeException|Error error){NativeWorldCheckpoint.beforeOwnedWork(session.world().getServer(),journal.id());restore(before);quarantined=true;save();throw error;}
    }
    public void save(){if(loading)return;var record=journal.read();record.put("genericNativeParallel",saveTag());journal.write(record);}
    private CompoundTag saveTag(){
        var tag=new CompoundTag();tag.putInt("schema",2);tag.putBoolean("quarantined",quarantined);
        var list=new ListTag();pools.values().forEach(p->list.add(p.save()));tag.put("machines",list);return tag;
    }
    public static boolean restorable(net.minecraft.server.level.ServerLevel world,CompoundTag saved){
        if(saved.getInt("schema")!=1 && saved.getInt("schema")!=2)return false;
        for(var value:saved.getList("machines",Tag.TAG_COMPOUND)){
            var tag=(CompoundTag)value;var source=world.getBlockEntity(BlockPos.of(tag.getLong("pos")));
            if(!(source instanceof NativeParallelMachine provider))return false;
            if(!image(source).equals(tag.getCompound("primary")))return false;
            var contract=provider.nativeParallelContract();
            if(contract==null || !contract.localStateOnly() || !contract.id().equals(tag.getString("contract")) || contract.revision()!=tag.getInt("revision"))return false;
            var states=BigInteger.ONE;
            for(var group:tag.getList("extra",Tag.TAG_COMPOUND)){
                var count=multiplicity((CompoundTag)group,"count");states=states.add(count);
                var lane=create(source,((CompoundTag)group).getCompound("state"));if(!NativeParallelContext.call(lane,()->contract.audit(lane)).empty())return false;
            }
            if(!states.equals(multiplicity(tag,"lanes")))return false;
        }
        return !saved.getBoolean("quarantined");
    }
    private static BlockEntity create(BlockEntity source,CompoundTag data){
        require(source.getBlockState().getBlock() instanceof EntityBlock,"NATIVE_PARALLEL_NO_ENTITY_BLOCK");
        var state=net.minecraft.nbt.NbtUtils.readBlockState(source.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK),data.getCompound("state"));
        require(state.getBlock()==source.getBlockState().getBlock(),"NATIVE_PARALLEL_BLOCK_TYPE_CHANGED");
        var lane=((EntityBlock)state.getBlock()).newBlockEntity(source.getBlockPos(),state);
        require(lane!=null && lane.getType()==source.getType(),"NATIVE_PARALLEL_MACHINE_TYPE_CHANGED");
        try(var ignored=NativeParallelContext.enter(lane)){lane.setLevel(source.getLevel());PlatformData.loadBlock(lane,data.getCompound("data").copy());}return lane;
    }
    private static CompoundTag nativeData(BlockEntity lane){return NativeParallelContext.call(lane,()->PlatformData.saveBlock(lane));}
    private static CompoundTag image(BlockEntity lane){return NativeParallelContext.call(lane,()->{var tag=new CompoundTag();tag.put("state",net.minecraft.nbt.NbtUtils.writeBlockState(lane.getBlockState()));tag.put("data",PlatformData.saveBlock(lane));return tag;});}
    private static void apply(BlockEntity lane,CompoundTag tag){NativeParallelContext.call(lane,()->{lane.setBlockState(net.minecraft.nbt.NbtUtils.readBlockState(lane.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK),tag.getCompound("state")));PlatformData.loadBlock(lane,tag.getCompound("data"));return null;});}
    private record Group(BlockEntity lane,BigInteger copies){Group{require(copies.signum()>0,"NONPOSITIVE_NATIVE_GROUP");}}
    @FunctionalInterface private interface NativeTransfer {BigInteger apply(BlockEntity lane,BigInteger amount,boolean simulate);}
    private final class Pool {
        final BlockEntity source;final Contract contract;List<Group> groups=new ArrayList<>();
        BigInteger nativeTicks=BigInteger.ZERO,nativeCalls=BigInteger.ZERO,worldTicks=BigInteger.ZERO,received=BigInteger.ZERO,extracted=BigInteger.ZERO,consumed=BigInteger.ZERO,generated=BigInteger.ZERO;
        Pool(BlockEntity source){
            require(!(source instanceof appeng.me.helpers.IGridConnectedBlockEntity) && !(source instanceof appeng.api.parts.IPartHost),"NATIVE_PARALLEL_GRID_OWNER_NOT_LOCAL");
            require(!NativeWorldMachine.supports(source) && !(NativeMachines.mek() && (
                    com.syaru.ae2machines.compat.mek.MekWorldParallel.supportsWorld(source)
                    || com.syaru.ae2machines.compat.mek.MekStructureParallel.supports(source)
                    || com.syaru.ae2machines.compat.mek.MekEvaporationParallel.supports(source)
                    || com.syaru.ae2machines.compat.mek.MekTransportParallel.supports(source))),"NATIVE_PARALLEL_AGGREGATE_OVERLAP");
            this.source=source;contract=Objects.requireNonNull(((NativeParallelMachine)source).nativeParallelContract());
            require(contract.id()!=null && !contract.id().isBlank() && contract.revision()>0 && contract.localStateOnly(),"NATIVE_PARALLEL_LOCAL_CONTRACT_REQUIRED");
            require(contract.energyUnit()!=null && !contract.energyUnit().isBlank(),"NATIVE_PARALLEL_ENERGY_UNIT_REQUIRED");groups.add(new Group(source,BigInteger.ONE));
        }
        BigInteger count(){return groups.stream().map(Group::copies).reduce(BigInteger.ZERO,BigInteger::add);}
        Assets audit(BlockEntity lane){return NativeParallelContext.call(lane,()->Objects.requireNonNull(contract.audit(lane)));}
        BlockEntity empty(){
            var before=image(source);var data=new CompoundTag();
            try{NativeParallelContext.call(source,()->{data.put("state",net.minecraft.nbt.NbtUtils.writeBlockState(contract.emptyBlockState(source)));data.put("data",contract.emptyLane(source));return null;});require(before.equals(image(source)),"NATIVE_PARALLEL_EMPTY_FACTORY_MUTATED_SOURCE");}
            catch(RuntimeException|Error error){apply(source,before);throw error;}
            var empty=create(source,data);require(audit(empty).empty(),"NATIVE_PARALLEL_EMPTY_STATE_HAS_ASSETS_OR_WORK");return empty;
        }
        Assets assets(){
            var items=new HashMap<AEKey,BigInteger>();var energy=BigInteger.ZERO;boolean idle=true;
            for(var g:groups){var a=audit(g.lane());a.materials().forEach((k,n)->items.merge(k,n.multiply(g.copies()),BigInteger::add));energy=energy.add(a.energy().multiply(g.copies()));idle&=a.idle();}
            return new Assets(items,energy,idle);
        }
        @SuppressWarnings({"rawtypes","unchecked"})void tick(){
            require(count().equals(BigInteger.ONE) || contract.independentCopies(source),"NATIVE_PARALLEL_GROUP_CONTRACT_REQUIRED");
            for(var g:groups){var lane=g.lane();
                var before=nativeData(lane);var a=audit(lane);
                try(var ignored=NativeParallelContext.enter(lane)){
                    var ticker=((EntityBlock)lane.getBlockState().getBlock()).getTicker(lane.getLevel(),lane.getBlockState(),lane.getType());
                    require(ticker!=null,"NATIVE_PARALLEL_NATIVE_TICKER_MISSING");
                    ((BlockEntityTicker)ticker).tick(lane.getLevel(),lane.getBlockPos(),lane.getBlockState(),lane);
                }
                var after=nativeData(lane);var b=audit(lane);var work=contract.energyWork(before,a,after,b);
                require(a.energy().subtract(work.consumed()).add(work.generated()).equals(b.energy()),"NATIVE_PARALLEL_ENERGY_IMBALANCE");
                consumed=consumed.add(work.consumed().multiply(g.copies()));generated=generated.add(work.generated().multiply(g.copies()));nativeTicks=nativeTicks.add(g.copies());nativeCalls=nativeCalls.add(BigInteger.ONE);
            }
            publishPrimaryState();
            worldTicks=worldTicks.add(BigInteger.ONE);
        }
        void markPrimaryDirty(){session.world().getChunkAt(source.getBlockPos()).setUnsaved(true);}
        void publishPrimaryState(){if(session.world().getBlockState(source.getBlockPos())!=source.getBlockState())session.world().setBlock(source.getBlockPos(),source.getBlockState(),2);markPrimaryDirty();}
        MEStorage storage(Direction side){return new MEStorage(){
            @Override public net.minecraft.network.chat.Component getDescription(){return source.getBlockState().getBlock().getName();}
            @Override public long insert(AEKey key,long amount,Actionable mode,IActionSource source){return transfer(key,amount,mode,source,true,side);}
            @Override public long extract(AEKey key,long amount,Actionable mode,IActionSource source){return transfer(key,amount,mode,source,false,side);}
            @Override public void getAvailableStacks(KeyCounter out){
                if(session.closed())return;
                var totals=inspect(()->{
                    var amounts=new HashMap<AEKey,BigInteger>();
                    for(var g:groups)NativeParallelContext.call(g.lane(),()->{var storage=contract.storage(g.lane(),side);if(storage!=null)for(var e:storage.getAvailableStacks()){
                        require(e.getLongValue()>=0,"NEGATIVE_NATIVE_AVAILABLE_STACK");amounts.merge(e.getKey(),BigInteger.valueOf(e.getLongValue()).multiply(g.copies()),BigInteger::add);
                    }return null;});
                    return amounts;
                });
                totals.forEach((k,n)->out.add(k,n.min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact()));
            }
        };}
        long transfer(AEKey key,long amount,Actionable mode,IActionSource src,boolean insert,Direction side){
            if(amount<=0 || session.closed() || quarantined)return 0;
            Supplier<Long> transfer=()->distribute(BigInteger.valueOf(amount),insert,mode.isSimulate(),(lane,offered,simulate)->{
                var storage=NativeParallelContext.call(lane,()->contract.storage(lane,side));if(storage==null)return BigInteger.ZERO;
                var before=audit(lane);var image=nativeData(lane);long requested=offered.longValueExact();
                var action=simulate?Actionable.SIMULATE:Actionable.MODULATE;
                long n=NativeParallelContext.call(lane,()->insert?storage.insert(key,requested,action,src):storage.extract(key,requested,action,src));
                require(n>=0 && n<=requested,"NATIVE_PARALLEL_INVALID_TRANSFER");
                if(simulate)require(image.equals(nativeData(lane)),"NATIVE_PARALLEL_SIMULATION_MUTATED");
                else{
                    var expected=new HashMap<>(before.materials());expected.merge(key,BigInteger.valueOf(insert?n:-n),BigInteger::add);expected.values().removeIf(v->v.signum()==0);
                    var after=audit(lane);require(expected.equals(after.materials()) && before.energy().equals(after.energy()),"NATIVE_PARALLEL_MATERIAL_TRANSFER_IMBALANCE");
                }
                return BigInteger.valueOf(n);
            }).longValueExact();
            return mode.isSimulate()?inspect(transfer):mutate(transfer);
        }
        BigInteger energy(BigInteger amount,boolean simulate,boolean extraction,Direction side){
            var accepted=distribute(amount,!extraction,simulate,(lane,offered,simulated)->{
                var before=audit(lane);var image=nativeData(lane);
                var n=NativeParallelContext.call(lane,()->extraction?contract.extractEnergy(lane,side,offered,simulated):contract.insertEnergy(lane,side,offered,simulated));
                require(n!=null && n.signum()>=0 && n.compareTo(offered)<=0,"NATIVE_PARALLEL_INVALID_ENERGY_TRANSFER");
                if(simulated)require(image.equals(nativeData(lane)),"NATIVE_PARALLEL_SIMULATION_MUTATED");
                else {var after=audit(lane);require(after.energy().equals(extraction?before.energy().subtract(n):before.energy().add(n)) && before.materials().equals(after.materials()),"NATIVE_PARALLEL_ENERGY_TRANSFER_IMBALANCE");}
                return n;
            });
            if(!simulate){if(extraction)extracted=extracted.add(accepted);else received=received.add(accepted);}
            return accepted;
        }
        BigInteger distribute(BigInteger amount,boolean balanced,boolean simulate,NativeTransfer action){
            require(count().equals(BigInteger.ONE) || contract.independentCopies(source),"NATIVE_PARALLEL_GROUP_CONTRACT_REQUIRED");
            var left=amount;var remaining=count();var updated=new ArrayList<Group>();
            for(var g:groups){
                if(left.signum()==0){updated.add(g);remaining=remaining.subtract(g.copies());continue;}
                var offered=balanced?left.min(left.add(remaining).subtract(BigInteger.ONE).divide(remaining).multiply(g.copies()))
                        :left.min(action.apply(g.lane(),left,true).multiply(g.copies()));
                var parts=offered.divideAndRemainder(g.copies());var before=simulate?null:image(g.lane());
                var high=parts[1];var low=g.copies().subtract(high);
                left=left.subtract(portion(g,high,parts[0].add(BigInteger.ONE),before,simulate,action,updated));
                left=left.subtract(portion(g,low,parts[0],before,simulate,action,updated));
                remaining=remaining.subtract(g.copies());
            }
            if(!simulate)groups=updated;
            return amount.subtract(left);
        }
        BigInteger portion(Group original,BigInteger copies,BigInteger offered,CompoundTag before,boolean simulate,NativeTransfer action,List<Group> out){
            if(copies.signum()==0)return BigInteger.ZERO;
            if(offered.signum()==0){out.add(new Group(original.lane(),copies));return BigInteger.ZERO;}
            var lane=simulate || original.lane()==source?original.lane():create(source,before);
            var expected=action.apply(lane,offered,true);
            if(!simulate){var actual=action.apply(lane,offered,false);require(actual.equals(expected),"NATIVE_PARALLEL_SIMULATION_DIVERGED");out.add(new Group(lane,copies));}
            return expected.multiply(copies);
        }
        void merge(){
            var updated=new ArrayList<Group>();var equal=new LinkedHashMap<CompoundTag,Group>();
            for(var g:groups){if(g.lane()==source){require(g.copies().equals(BigInteger.ONE),"NATIVE_PRIMARY_MULTIPLIED");updated.add(g);continue;}
                var key=image(g.lane());var old=equal.get(key);equal.put(key,old==null?g:new Group(old.lane(),old.copies().add(g.copies())));
            }
            updated.addAll(equal.values());groups=updated;
        }
        CompoundTag save(){
            var tag=new CompoundTag();tag.putLong("pos",source.getBlockPos().asLong());tag.putString("contract",contract.id());tag.putInt("revision",contract.revision());
            put(tag,"lanes",count());tag.put("primary",image(source));
            var extra=new ListTag();for(var group:groups)if(group.lane()!=source){var g=new CompoundTag();g.put("state",image(group.lane()));put(g,"count",group.copies());extra.add(g);}tag.put("extra",extra);
            put(tag,"nativeTicks",nativeTicks);put(tag,"nativeCalls",nativeCalls);put(tag,"worldTicks",worldTicks);put(tag,"received",received);put(tag,"extracted",extracted);put(tag,"consumed",consumed);put(tag,"generated",generated);return tag;
        }
        void load(CompoundTag tag){
            require(contract.id().equals(tag.getString("contract")) && contract.revision()==tag.getInt("revision"),"NATIVE_PARALLEL_CONTRACT_CHANGED");
            var declared=multiplicity(tag,"lanes");require(declared.equals(BigInteger.ONE) || contract.independentCopies(source),"NATIVE_PARALLEL_GROUP_CONTRACT_REQUIRED");
            apply(source,tag.getCompound("primary"));groups=new ArrayList<>();groups.add(new Group(source,BigInteger.ONE));
            for(var value:tag.getList("extra",Tag.TAG_COMPOUND)){var group=(CompoundTag)value;groups.add(new Group(create(source,group.getCompound("state")),multiplicity(group,"count")));}
            require(count().equals(declared),"NATIVE_PARALLEL_STATE_COUNT_CHANGED");merge();
            nativeTicks=number(tag,"nativeTicks");worldTicks=number(tag,"worldTicks");received=number(tag,"received");extracted=number(tag,"extracted");consumed=number(tag,"consumed");generated=number(tag,"generated");
            nativeCalls=tag.contains("nativeCalls")?number(tag,"nativeCalls"):nativeTicks;
            assets();
        }
    }
    private static void put(CompoundTag tag,String key,BigInteger n){tag.putByteArray(key,n.toByteArray());}
    private static BigInteger multiplicity(CompoundTag tag,String key){var n=tag.contains(key,Tag.TAG_BYTE_ARRAY)?number(tag,key):BigInteger.valueOf(tag.getInt(key));require(n.signum()>0,"INVALID_NATIVE_GROUP_COUNT");return n;}
    private static BigInteger number(CompoundTag tag,String key){var n=new BigInteger(tag.getByteArray(key));require(n.signum()>=0,"NEGATIVE_NATIVE_PARALLEL_COUNTER");return n;}
}
