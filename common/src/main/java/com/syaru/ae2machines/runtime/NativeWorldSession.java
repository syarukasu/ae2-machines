package com.syaru.ae2machines.runtime;

import appeng.api.networking.*;
import appeng.api.parts.IPartHost;
import com.syaru.ae2machines.block.BaseAccelerator;
import com.syaru.ae2machines.engine.MeteredEnergy;
import com.syaru.ae2machines.platform.*;
import com.syaru.ae2machines.world.*;
import java.math.BigInteger;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import static com.syaru.ae2machines.world.NativeFactoryCompiler.require;

/** Owns an actual factory world, not an inventory/recipe conversion adapter. */
public final class NativeWorldSession implements FactoryHandle {
    private final BaseAccelerator host;
    private final FactoryJournal journal;
    private final UUID accessOwner=UUID.randomUUID();
    private final CompoundTag blueprint;
    private final ServerLevel world;
    private final ProcessingRuntime runtime;
    private final NativeWorldPower power;
    private final List<net.minecraft.world.level.block.entity.BlockEntity> structures;
    private final List<IGridConnection> connections=new ArrayList<>();
    private NativeWorldRuntime.Lease lease;
    private boolean closed,initializing=true;
    private boolean unwitnessedPower;
    private NativeWorldBatches batches;
    private MekNativeRequests variable;
    private NativeParallelRuntime generic;
    public static NativeWorldSession open(BaseAccelerator host,UUID id){return new NativeWorldSession(host,id);}
    private NativeWorldSession(BaseAccelerator host,UUID id) {
        this.host=host;var source=(ServerLevel)host.getLevel();journal=FactoryJournal.open(source,id);var record=journal.read();blueprint=record.getCompound("blueprint");
        require(blueprint.getInt("schema")==4,"NOT_NATIVE_WORLD_FACTORY");
        require(blueprint.getString("dimension").equals(source.dimension().location().toString()),"WRONG_DIMENSION");
        require(record.getString("state").equals("CAPTURED") || record.getString("state").equals("ACTIVE") && record.getLong("accelerator")==host.getBlockPos().asLong(),"FACTORY_ALREADY_OWNED");
        world=NativeCellWorlds.open(source,id);
        if(record.hasUUID("worldEpoch"))require(FactoryRecovery.epoch(source.getServer()).permits(record.getUUID("worldEpoch")),"NATIVE_WORLD_REQUIRES_RECONCILIATION");
        unwitnessedPower=record.getBoolean("unwitnessedPower");
        require(id.equals(NativeWorldRuntime.get(world).state().owner()),"NATIVE_WORLD_OWNERSHIP_MISSING");
        // A restarted cell has no resident chunks. Loading must precede ME-boundary discovery,
        // while the world remains paused; starting the lease first would process offline assets.
        NativeWorldRuntime.get(world).retain();
        for(var chunk:NativeRegionMove.chunks(BlockPos.of(blueprint.getLong("first")),BlockPos.of(blueprint.getLong("last"))))
            world.getChunk(chunk.x,chunk.z);
        power=new NativeWorldPower(world,BlockPos.of(blueprint.getLong("first")),BlockPos.of(blueprint.getLong("last")),internalEndpoints(),
                blueprint.getList("energyPorts",Tag.TAG_LONG).stream().map(t->BlockPos.of(((LongTag)t).getAsLong())).toList());
        structures=NativeMachines.gt()?power.blocks().stream().filter(GtMultiblockAdapter::supports).toList():List.of();
        for(var machine:structures)GtMultiblockAdapter.prepareWorld(machine);
        if(NativeMachines.mek())com.syaru.ae2machines.compat.mek.MekWorldParallel.attach(power.blocks());
        runtime=new ProcessingRuntime(new MeteredEnergy("FE",BigInteger.ZERO,BigInteger.ZERO,BigInteger.ZERO,BigInteger.ZERO),ignored->{if(!initializing)persist();});
        var speed=record.contains("worldSpeed")?new BigInteger(record.getByteArray("worldSpeed")):BigInteger.ONE;
        var lanes=record.contains("worldLanes")?new BigInteger(record.getByteArray("worldLanes")):BigInteger.ONE;
        runtime.configure(speed,lanes);NativeWorldRuntime.get(world).speed(speed);
        NativeWorldRuntime.get(world).state().lanes(lanes);initializing=false;
        record.putString("state","ACTIVE");record.putLong("accelerator",host.getBlockPos().asLong());record.putUUID("worldEpoch",FactoryRecovery.epoch(source.getServer()).current());journal.write(record);
        NativeWorldRuntime.get(world).energyHost(host);
        NativeWorldRuntime.get(world).beforeExternalPower(this::externalPower);
        batches=new NativeWorldBatches(this,journal);NativeWorldRuntime.get(world).batches(batches);
        if(NativeMachines.mek() && NativeMachines.chemicals()){variable=new MekNativeRequests(this,journal);NativeWorldRuntime.get(world).variable(variable);}
        generic=new NativeParallelRuntime(this,journal);NativeWorldRuntime.get(world).generic(generic);
        NativeWorldRuntime.get(world).temporal(new NativeTemporalScheduler(this));
        NativeCellLocations.attach(id,this);
    }
    public ServerLevel world(){return world;}
    public UUID id(){return journal.id();}
    public UUID accessOwner(){return accessOwner;}
    public BlockPos previewFirst(){return BlockPos.of(blueprint.getLong("first"));}
    public BlockPos previewLast(){return BlockPos.of(blueprint.getLong("last"));}
    public BaseAccelerator host(){return host;}
    public boolean internalEndpoints(){return blueprint.getInt("endpointVersion")==1;}
    public IGrid mainGrid(){return internalEndpoints()?NativeCellEndpoints.decodedGrid(world,blueprint.getList("boundaries",Tag.TAG_COMPOUND)):host.getMainNode().getGrid();}
    public List<net.minecraft.world.level.block.entity.BlockEntity> nativeBlocks(){return power.blocks();}
    public NativeWorldBatches batches(){return batches;}
    public MekNativeRequests variable(){return variable;}
    public NativeParallelRuntime generic(){return generic;}
    @Override public ProcessingRuntime runtime(){return runtime;}
    @Override public List<ProcessingRuntime> runtimes(){var result=new ArrayList<ProcessingRuntime>();result.add(runtime);if(batches!=null)result.addAll(batches.runtimes());return List.copyOf(result);}
    boolean closed(){return closed;}
    @Override public boolean isOnline(){return !closed && lease!=null && (generic==null || !generic.quarantined() && generic.intact()) && host.networkOnline() && (!internalEndpoints() || mainGrid()!=null);}
    @Override public boolean canConfigure(){return !closed && (batches==null || !batches.active()) && (variable==null || !variable.active());}
    @Override public boolean supportsConfiguration(BigInteger speed,BigInteger lanes){
        if(speed.signum()<=0 || lanes.signum()<=0)return false;
        if(!com.syaru.ae2machines.block.ComputationStructure.accepts(host,speed,lanes))return false;
        if(batches!=null && batches.active() || variable!=null && variable.active())return speed.equals(runtime.speed()) && lanes.equals(runtime.lanes());
        if(lanes.equals(runtime.lanes()))return true;
        return canConfigureAggregation(lanes);
    }
    /** Native ticking/speed never requires a recipe adapter. Q changes need exact aggregation. */
    private boolean canConfigureAggregation(BigInteger lanes){
        // Do not change the number of paid native operations halfway through a recipe.
        var blocks=power.blocks();
        boolean declared=generic!=null && generic.hasTargets();
        if(declared && !generic.canConfigure(lanes))return false;
        boolean mek=NativeMachines.mek() && com.syaru.ae2machines.compat.mek.MekWorldParallel.canConfigure(blocks,lanes);
        boolean gt=blocks.stream().anyMatch(b->NativeMachines.gt() && (GregTechAdapter.supports(b) || GtMultiblockAdapter.supports(b)));
        boolean mekPresent=NativeMachines.mek() && blocks.stream().anyMatch(b->com.syaru.ae2machines.compat.mek.MekWorldParallel.supportsWorld(b)
                || com.syaru.ae2machines.compat.mek.MekEvaporationParallel.supports(b)
                || com.syaru.ae2machines.compat.mek.MekStructureParallel.supports(b) || com.syaru.ae2machines.compat.mek.MekTransportParallel.supports(b));
        if(mekPresent && !mek)return false;
        return (mek || gt || declared) && blocks.stream().filter(b->NativeMachines.gt() && (GregTechAdapter.supports(b) || GtMultiblockAdapter.supports(b))).allMatch(NativeWorldMachine::idle);
    }
    @Override public void configure(BigInteger speed,BigInteger lanes) {
        require(supportsConfiguration(speed,lanes),"NATIVE_WORLD_PARALLEL_EXECUTOR_NOT_AVAILABLE");
        var previousLanes=runtime.lanes();
        if(generic!=null && generic.hasTargets() && (!lanes.equals(previousLanes) || generic.needsInitialization() && generic.canConfigure(lanes)))generic.configure(lanes);
        runtime.configure(speed,lanes);NativeWorldRuntime.get(world).speed(speed);
        NativeWorldRuntime.get(world).state().lanes(lanes);
        if(NativeMachines.mek())com.syaru.ae2machines.compat.mek.MekWorldParallel.attach(power.blocks());
        if(NativeMachines.mek())com.syaru.ae2machines.compat.mek.MekTransportParallel.refresh(power.blocks());
        if(NativeMachines.mek())com.syaru.ae2machines.compat.mek.MekStructureParallel.reconfigured(power.blocks(),previousLanes,lanes);
        persist();
    }
    @Override public void tick(long tick) {
        if(closed)return;
        for(var machine:structures)if(!GtMultiblockAdapter.formedWorld(machine)) {
            GtMultiblockAdapter.prepareWorld(machine);
            if(GtMultiblockAdapter.formedWorld(machine) && batches!=null)batches.invalidate();
        }
        if(internalEndpoints()){tickInternalEndpoints();return;}
        boolean connected=host.getMainNode().getNode()!=null;
        var boundaries=blueprint.getList("boundaries",Tag.TAG_COMPOUND);
        if(connections.size()!=boundaries.size()) {
            disconnect();
            for(var value:boundaries) {
                var tag=(CompoundTag)value;var source=(ServerLevel)host.getLevel();var pos=BlockPos.of(tag.getLong("boundary"));
                if(!source.hasChunkAt(pos) || !(source.getBlockEntity(pos) instanceof IPartHost cable)){connected=false;break;}
                var side=Direction.byName(tag.getString("side"));
                if(!(cable.getPart(side) instanceof VirtualP2PPart p2p) || !p2p.isOutput() || p2p.getFrequency()!=tag.getShort("frequency") || !p2p.getMainNode().isActive()){connected=false;break;}
                var inside=NativeGridBoundary.ports(world.getBlockEntity(BlockPos.of(tag.getLong("pos")))).get(tag.getString("port"));
                if(inside==null || p2p.getExternalFacingNode()==null){connected=false;break;}
                connections.add(GridHelper.createConnection(inside,p2p.getExternalFacingNode()));
            }
        }
        boolean powered=host.getMainNode().isActive();
        if(connected)for(var value:boundaries) {
            var tag=(CompoundTag)value;
            var node=NativeGridBoundary.ports(world.getBlockEntity(BlockPos.of(tag.getLong("pos")))).get(tag.getString("port"));
            var source=host.getLevel();var pos=BlockPos.of(tag.getLong("boundary"));
            if(!source.hasChunkAt(pos) || !(source.getBlockEntity(pos) instanceof IPartHost cable)
                    || !(cable.getPart(Direction.byName(tag.getString("side"))) instanceof VirtualP2PPart p2p)
                    || !p2p.isOutput() || p2p.getFrequency()!=tag.getShort("frequency") || !p2p.getMainNode().isActive()
                    || node==null || node.getGrid()!=host.getMainNode().getGrid()){connected=false;break;}
            powered &= node.isActive();
        }
        boolean linked=connected && powered;
        if(linked && lease==null) {
            var min=BlockPos.of(blueprint.getLong("first"));var max=BlockPos.of(blueprint.getLong("last"));
            lease=NativeWorldRuntime.get(world).start(journal.id(),new net.minecraft.world.level.ChunkPos(min),new net.minecraft.world.level.ChunkPos(max),world.getDayTime());
        }else if(!linked && lease!=null){lease.close();lease=null;}
        // Loss of power pauses work, not physical wiring. Keeping the native graph
        // lets a real AE acceptor restore power without a powered-node bootstrap loop.
        if(!connected)disconnect();
        if(batches!=null)batches.tick();
        if(variable!=null)variable.dispatchOutputs();
    }
    private void tickInternalEndpoints(){
        var carrier=host.getMainNode().getNode();var boundaries=blueprint.getList("boundaries",Tag.TAG_COMPOUND);
        boolean connected=carrier!=null;
        if(connections.size()!=boundaries.size()){
            disconnect();
            for(var value:boundaries){
                var tunnel=NativeCellEndpoints.output(world,(CompoundTag)value);
                if(carrier==null || tunnel==null || tunnel.getMainNode().getNode()==null){connected=false;break;}
                connections.add(GridHelper.createConnection(carrier,tunnel.getMainNode().getNode()));
            }
        }
        for(var value:boundaries){
            var tunnel=NativeCellEndpoints.output(world,(CompoundTag)value);
            if(tunnel==null || tunnel.getMainNode().getGrid()!=host.getMainNode().getGrid()){connected=false;break;}
        }
        boolean linked=connected && host.networkOnline() && mainGrid()!=null;
        if(linked && lease==null){var min=previewFirst();var max=previewLast();lease=NativeWorldRuntime.get(world).start(journal.id(),new net.minecraft.world.level.ChunkPos(min),new net.minecraft.world.level.ChunkPos(max),world.getDayTime());}
        else if(!linked && lease!=null){lease.close();lease=null;}
        if(!connected)disconnect();
        if(batches!=null)batches.tick();if(variable!=null)variable.dispatchOutputs();
    }
    @Override public void persist() {
        if(closed)return;
        if(batches!=null)batches.save();
        if(variable!=null)variable.save();
        if(generic!=null)generic.save();
        var min=BlockPos.of(blueprint.getLong("first"));var max=BlockPos.of(blueprint.getLong("last"));
        if(NativeMachines.mek())MekStructureStorage.checkpoint(world,NativeRegionMove.blockEntities(world,min,max),min,max);
        world.getServer().overworld().getDataStorage().save();world.save(null,true,false);
        var record=journal.read();record.putByteArray("worldSpeed",runtime.speed().toByteArray());record.putByteArray("worldLanes",runtime.lanes().toByteArray());record.putUUID("worldEpoch",FactoryRecovery.epoch(world.getServer()).current());journal.write(record);
        NativeWorldCheckpoint.save(host,world,journal);
    }
    @Override public boolean canDetach(){return !closed;}
    @Override public void detach(){close();var record=journal.read();record.putString("state","CAPTURED");record.remove("accelerator");journal.write(record);}
    @Override public void close(){if(closed)return;if(lease!=null){lease.close();lease=null;}disconnect();persist();NativeWorldRuntime.get(world).batches(null);NativeWorldRuntime.get(world).variable(null);NativeWorldRuntime.get(world).generic(null);NativeWorldRuntime.get(world).energyHost(null);NativeWorldRuntime.get(world).beforeExternalPower(()->{});closed=true;NativeCellLocations.detach(journal.id(),this);}
    private void disconnect(){for(var connection:connections)connection.destroy();connections.clear();}
    @Override public int receiveFE(int offered,long tick,boolean simulate) {
        if(closed || offered<=0)return 0;
        if(!simulate)NativePowerPushes.beforeCredit(this,offered);
        if(internalEndpoints()){
            if(!simulate && power.receiveFE(offered,tick,true)>0)externalPower();
            int accepted=power.receiveFE(offered,tick,simulate);
            if(!simulate)NativePowerPushes.afterCredit(this,accepted);return accepted;
        }
        if(!isOnline()) {
            if(!simulate && power.receiveAeFE(offered,tick,true)>0)externalPower();
            int accepted=power.receiveAeFE(offered,tick,simulate);
            if(!simulate)NativePowerPushes.afterCredit(this,accepted);
            return accepted;
        }
        int changing=variable==null?0:variable.receiveFE(BigInteger.valueOf(offered),true).intValueExact();
        if(!simulate && changing>0)externalPower();
        int variableAccepted=variable==null?0:variable.receiveFE(BigInteger.valueOf(offered),simulate).intValueExact();offered-=variableAccepted;
        if(!simulate && !unwitnessedPower && (power.receiveFE(offered,tick,true)>0
                || batches!=null && batches.receiveExactFE(BigInteger.valueOf(offered),world.getGameTime(),true).signum()>0))externalPower();
        int accepted=batches==null?0:batches.receiveExactFE(BigInteger.valueOf(offered),world.getGameTime(),simulate).intValueExact();
        int total=variableAccepted+accepted+power.receiveFE(offered-accepted,tick,simulate);
        if(!simulate)NativePowerPushes.afterCredit(this,total);
        return total;
    }
    public boolean ownedFeOnly(){return isOnline() && (variable!=null && variable.active() || batches!=null && batches.active()) && power.receiveFE(Integer.MAX_VALUE,world.getGameTime(),true)==0;}
    /** Use the live ledger owner, not a second file reader with a stale before-image. */
    public ExternalHandoffs.Transaction beginOwnedPower(Collection<net.minecraft.world.level.block.entity.BlockEntity> sources){return ExternalHandoffs.beginOwnedPower(this,journal,sources);}
    public BigInteger receiveExactFE(BigInteger offered,boolean simulate){
        if(internalEndpoints())return BigInteger.valueOf(receiveFE(offered.min(BigInteger.valueOf(Integer.MAX_VALUE)).intValueExact(),world.getGameTime(),simulate));
        if(!isOnline() || batches==null)return BigInteger.ZERO;
        var changing=variable==null?BigInteger.ZERO:variable.receiveFE(offered,true);
        if(!simulate && changing.signum()>0)externalPower();
        var variableAccepted=variable==null?BigInteger.ZERO:variable.receiveFE(offered,simulate);
        offered=offered.subtract(variableAccepted);
        if(!simulate && !unwitnessedPower && batches.receiveExactFE(offered,world.getGameTime(),true).signum()>0)externalPower();
        return variableAccepted.add(batches.receiveExactFE(offered,world.getGameTime(),simulate));
    }
    public BigInteger receiveCheckpointedFE(com.syaru.ae2machines.api.CheckpointedEnergySource source,BigInteger offered,boolean simulate){
        com.syaru.ae2machines.engine.ExactAmounts.nonnegative(offered);Objects.requireNonNull(source);
        if(!world.getServer().isSameThread())throw new IllegalStateException("Owned power handoff requires server thread");
        if(!isOnline() || variable==null || internalEndpoints())return BigInteger.ZERO;
        var participants=source.checkpointParticipants();if(!ExternalHandoffs.validOwnedPowerSources(this,participants))return BigInteger.ZERO;
        participants=List.copyOf(participants);
        var demand=variable.receiveFE(offered,true);if(demand.signum()==0)return BigInteger.ZERO;
        var available=source.extractFE(demand,true);
        if(available==null || available.signum()<0 || available.compareTo(demand)>0)throw new IllegalStateException("Invalid source power simulation");
        var packet=variable.receiveFE(available,true);if(packet.signum()==0 || simulate)return packet;
        var tx=ExternalHandoffs.beginOwnedPower(this,journal,participants);if(tx==null)return BigInteger.ZERO;
        try{
            if(!source.extractFE(packet,false).equals(packet) || !variable.receiveFE(packet,false).equals(packet))throw new IllegalStateException("Owned power transfer differed from simulation");
            tx.commit();return packet;
        }catch(RuntimeException|Error failure){if(failure instanceof ExternalHandoffs.PersistenceFailure fatal)throw fatal;throw new ExternalHandoffs.PersistenceFailure(failure);}
    }
    @Override public int extractFE(int requested,long tick,boolean simulate){
        if(!isOnline())return 0;
        if(!simulate && !unwitnessedPower && power.extractFE(requested,tick,true)>0)externalPower();
        return power.extractFE(requested,tick,simulate);
    }
    @Override public boolean exposesEnergyOutput(){return isOnline();}
    @Override public int storedFE(){return isOnline()?power.storedFE():0;}
    @Override public int capacityFE(){return closed?0:isOnline()?Integer.MAX_VALUE:power.receiveAeFE(Integer.MAX_VALUE,world.getGameTime(),true);}
    @Override public long receiveEU(long voltage,long amps,long tick,boolean simulate){
        if(!isOnline() || voltage<=0 || amps<=0)return 0;
        if(internalEndpoints()){
            if(!simulate && power.receiveEU(voltage,amps,tick,true)>0)externalPower();
            return power.receiveEU(voltage,amps,tick,simulate);
        }
        var v=BigInteger.valueOf(voltage);var offered=v.multiply(BigInteger.valueOf(amps));
        if(!simulate && !unwitnessedPower && (power.receiveEU(voltage,amps,tick,true)>0
                || batches!=null && batches.receiveExactEU(voltage,offered,world.getGameTime(),true).signum()>0))externalPower();
        long accepted=batches==null?0:batches.receiveExactEU(voltage,offered,world.getGameTime(),simulate).divide(v).longValueExact();
        return accepted+power.receiveEU(voltage,amps-accepted,tick,simulate);
    }
    public BigInteger receiveExactEU(long voltage,BigInteger offered,boolean simulate) {
        if(internalEndpoints()){
            if(voltage<=0)return BigInteger.ZERO;
            var packet=BigInteger.valueOf(voltage);long amps=offered.divide(packet).min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact();
            return packet.multiply(BigInteger.valueOf(receiveEU(voltage,amps,world.getGameTime(),simulate)));
        }
        if(!isOnline() || batches==null)return BigInteger.ZERO;
        if(!simulate && !unwitnessedPower && batches.receiveExactEU(voltage,offered,world.getGameTime(),true).signum()>0)externalPower();
        return batches.receiveExactEU(voltage,offered,world.getGameTime(),simulate);
    }
    private void externalPower() {
        if(NativePowerPushes.covers(this))return;
        if(unwitnessedPower)return;
        // The caller's battery/pipe is not a transactional participant. Never assert its recovery.
        var record=journal.read();record.putBoolean("unwitnessedPower",true);record.remove("nativeCheckpoint");journal.write(record);
        unwitnessedPower=true;
    }
    @Override public long voltageEU(){return isOnline()?Math.max(power.voltageEU(),batches==null?0:batches.voltageEU()):0;}
    @Override public long demandEU(){
        if(!isOnline())return 0;
        long normal=power.demandEU(),owned=batches==null?0:batches.demandEU(world.getGameTime());
        return normal+Math.min(owned,Long.MAX_VALUE-normal);
    }
}
