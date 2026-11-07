package com.syaru.ae2machines.block;

import appeng.api.networking.*;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.me.helpers.IGridConnectedBlockEntity;
import com.syaru.ae2machines.engine.MeteredEnergy;
import com.syaru.ae2machines.platform.MachinesMod;
import com.syaru.ae2machines.runtime.*;
import net.minecraft.core.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import java.math.BigInteger;
import java.util.EnumSet;

public abstract class BaseAccelerator extends AEBaseBlockEntity implements IGridConnectedBlockEntity,com.syaru.ae2machines.api.ExactEnergyReceiver,com.syaru.ae2machines.api.VariableProcessingTarget,com.syaru.ae2machines.api.RecoverableEnergyTarget {
    @Override public BigInteger receiveCheckpointedFE(com.syaru.ae2machines.api.CheckpointedEnergySource source,BigInteger offered,boolean simulate){
        com.syaru.ae2machines.engine.ExactAmounts.nonnegative(offered);java.util.Objects.requireNonNull(source);
        return factoryHandle() instanceof NativeWorldSession session?session.receiveCheckpointedFE(source,offered,simulate):BigInteger.ZERO;
    }
    private com.syaru.ae2machines.runtime.MekNativeRequests variableRequests(){
        if(!(level instanceof net.minecraft.server.level.ServerLevel server))return null;
        if(!server.getServer().isSameThread())throw new IllegalStateException("Variable processing API requires server thread");
        return factoryHandle() instanceof com.syaru.ae2machines.runtime.NativeWorldSession nativeWorld?nativeWorld.variable():null;
    }
    @Override public boolean acceptVariableProcessing(com.syaru.ae2machines.api.VariableProcessingTarget.Request request){var requests=variableRequests();return requests!=null && requests.accept(request);}
    @Override public java.util.Optional<com.syaru.ae2machines.api.VariableProcessingTarget.Snapshot> variableProcessingSnapshot(java.util.UUID id,String digest){var requests=variableRequests();return requests!=null?requests.snapshot(id,digest):factoryId!=null && level instanceof net.minecraft.server.level.ServerLevel server?com.syaru.ae2machines.runtime.MekNativeRequests.durableSnapshot(server,factoryId,id,digest):java.util.Optional.empty();}
    @Override public boolean cancelVariableProcessing(java.util.UUID id,String digest){var requests=variableRequests();return requests!=null && requests.callerOwns(id,digest) && requests.cancel(id,digest);}
    @Override public boolean acknowledgeVariableProcessing(java.util.UUID id,String digest){var requests=variableRequests();return requests!=null && requests.callerOwns(id,digest) && requests.acknowledge(id,digest);}
    @Override public boolean deliverVariableProcessingToME(java.util.UUID id,String digest){var requests=variableRequests();return requests!=null && requests.callerOwns(id,digest) && requests.deliver(id,digest);}
    @Override public boolean forgetVariableProcessing(java.util.UUID id,String digest){var requests=variableRequests();return requests!=null && (requests.callerOwns(id,digest) || requests.forgotten(id,digest)) && requests.forget(id,digest);}
    protected ProcessingRuntime runtime = new ProcessingRuntime(new MeteredEnergy("FE",BigInteger.valueOf(1000000),
            BigInteger.valueOf(1000000),BigInteger.ZERO,BigInteger.ZERO), ignored -> checkpoint());
    private final IManagedGridNode node = GridHelper.createManagedNode(this,new IGridNodeListener<BaseAccelerator>() {
        @Override public void onSaveChanges(BaseAccelerator host,IGridNode node) { host.setChanged(); }
    }).setInWorldNode(true).setTagName("grid").setIdlePowerUsage(1).setFlags(GridFlags.REQUIRE_CHANNEL)
            .setExposedOnSides(EnumSet.allOf(Direction.class));
    private int speedPower, lanePower, threadPower;
    private boolean legacyAcceleration;
    private boolean loadFailed;
    protected java.util.UUID factoryId;
    protected FactoryHandle factory;
    private boolean factoryLoadAttempted;
    private long nextFactoryLoadTick=Long.MIN_VALUE;
    protected BaseAccelerator(BlockEntityType<?> type,BlockPos pos,BlockState state) { super(type,pos,state); }
    protected abstract void checkpoint();
    public ProcessingRuntime machineRuntime() { return runtime; }
    public java.util.List<ProcessingRuntime> machineRuntimes() { return factory==null ? java.util.List.of(runtime):factory.runtimes(); }
    public boolean hasFactory() { return factoryId!=null; }
    public boolean networkOnline() { return node.isActive() && ComputationStructure.controllerOnline(this); }
    public FactoryHandle factoryHandle(){return factory;}
    public java.util.UUID factoryId(){return factoryId;}
    @Override public BigInteger receiveExactFE(BigInteger offered,boolean simulate) {
        com.syaru.ae2machines.engine.ExactAmounts.nonnegative(offered);
        return level!=null && !level.isClientSide && !isRemoved() && !loadFailed && factory instanceof NativeWorldSession nativeWorld
                ? nativeWorld.receiveExactFE(offered,simulate):BigInteger.ZERO;
    }
    public boolean settingsFit() {
        return ComputationStructure.accepts(this,runtime.speed(),runtime.lanes())
                || legacyAcceleration && ComputationStructure.accepts(this,BigInteger.ONE,runtime.lanes())
                && runtime.speed().compareTo(BigInteger.ONE.shiftLeft(Math.max(0,ComputationStructure.threadPower(this)-1)))<=0;
    }
    public boolean factoryOnline() { return factory!=null && settingsFit() && factory.isOnline() && machineRuntimes().stream().noneMatch(ProcessingRuntime::quarantined); }
    public int workState() {
        return machineRuntimes().stream().mapToInt(r->r.displayState(level.getGameTime())).max().orElse(0);
    }
    public int powerPermille() {
        return machineRuntimes().stream().filter(r->r.displayState(level.getGameTime())==1 || r.displayState(level.getGameTime())==2)
                .mapToInt(r->r.powerPermille(level.getGameTime())).min().orElse(0);
    }
    public void insertFactory(java.util.UUID id,Player player) {
        if(!canAccess(player) || factoryId!=null) throw new IllegalArgumentException("ACCELERATOR_UNAVAILABLE");
        if(!ComputationStructure.runnable(this)) throw new IllegalArgumentException("COMPUTATION_STRUCTURE_INCOMPLETE");
        var record=com.syaru.ae2machines.world.FactoryJournal.open((net.minecraft.server.level.ServerLevel)level,id).read();
        if(!record.getCompound("blueprint").getUUID("player").equals(player.getUUID()))
            throw new IllegalArgumentException("FACTORY_OWNER_MISMATCH");
        factory=openFactory(id);factoryId=id;factoryLoadAttempted=true;runtime=factory.runtime();
        loadedFactorySettings();setChanged();
    }
    public boolean ejectFactory(Player player) {
        return canAccess(player) && ejectFactoryAuthorized(player);
    }
    public boolean ejectFactoryFromController(Player player,BaseAccelerator controller) {
        return ComputationStructure.controls(controller,this) && controller.canAccess(player)
                && appeng.util.Platform.hasPermissions(new appeng.api.util.DimensionalBlockPos(this),player)
                && ejectFactoryAuthorized(player);
    }
    private boolean ejectFactoryAuthorized(Player player) {
        if(factory==null || !factory.canDetach()) return false;
        var cell=new net.minecraft.world.item.ItemStack(MachinesMod.CELL.get());
        var data=new net.minecraft.nbt.CompoundTag();data.putUUID("factory",factoryId);
        com.syaru.ae2machines.world.VirtualCellMetadata.populate((net.minecraft.server.level.ServerLevel)level,data);
        com.syaru.ae2machines.platform.PlatformData.setCellData(cell,data);
        factory.detach();factory=null;factoryId=null;runtime=new ProcessingRuntime(new MeteredEnergy("FE",BigInteger.valueOf(1000000),BigInteger.valueOf(1000000),BigInteger.ZERO,BigInteger.ZERO),ignored->checkpoint());
        speedPower=0;lanePower=0;threadPower=0;legacyAcceleration=false;setChanged();
        if(!player.getInventory().add(cell)) player.drop(cell,false);return true;
    }
    protected void factoryCheckpoint() {
        if(factory==null) throw new IllegalStateException("Factory journal not bound");
        factory.persist();setChanged();
    }
    public int speedPower() { return speedPower; }
    public int lanePower() { return lanePower; }
    public int threadPower(){return threadPower;}
    public boolean migratingAcceleration(){return legacyAcceleration;}
    public int baseParallelPower(){return Math.max(0,lanePower-threadPower);}
    public boolean exactParallelDisplay(){return runtime.lanes().bitCount()==1;}
    public void refreshFactoryDisplaySettings(){
        speedPower=runtime.speed().bitLength()-1;lanePower=runtime.lanes().bitLength()-1;
        if(ComputationStructure.runnable(this)){
            int minimum=Math.max(0,lanePower-ComputationStructure.baseParallelPower(this));
            int maximum=Math.min(lanePower,ComputationStructure.threadPower(this));
            if(minimum<=maximum)threadPower=Math.max(minimum,Math.min(threadPower,maximum));
        }
    }
    protected void loadedFactorySettings(){
        legacyAcceleration=ComputationStructure.modular(this) && runtime.speed().compareTo(BigInteger.ONE)>0;
        refreshFactoryDisplaySettings();
    }
    protected void restoreFactoryDisplaySettings(int speed,int lanes) {
        // API-owned exact settings can exceed the GUI's editable exponent range.
        if(speed<0 || speed>=1048576 || lanes<0 || lanes>=1048576)throw new IllegalArgumentException("INVALID_SAVED_FACTORY_SETTINGS");
        speedPower=speed;lanePower=lanes;
    }
    protected void restoreFactoryDisplaySettings(int speed,int lanes,int threads){
        restoreFactoryDisplaySettings(speed,lanes);
        if(threads<0 || threads>9 || threads>lanes)throw new IllegalArgumentException("INVALID_SAVED_THREAD_SETTINGS");
        threadPower=threads;legacyAcceleration=ComputationStructure.modular(this) && speed>0;
    }
    public boolean canSetParallelSettings(int threads,int base){
        return threads>=0 && threads<=ComputationStructure.threadPower(this)
                && base>=0 && base<=ComputationStructure.baseParallelPower(this)
                && exactParallelDisplay() && canSetSettings(0,base+threads);
    }
    public boolean parallelSettings(int threads,int base){
        if(!canSetParallelSettings(threads,base))return false;
        settings(0,base+threads);threadPower=threads;setChanged();return true;
    }
    public boolean canSetSettings(int speed,int lanes) {
        return ComputationStructure.runnable(this) && !loadFailed && (factoryId==null || factory!=null)
                && speed>=0 && lanes>=0 && speed<=3400 && lanes<=3400
                && runtime.canConfigure() && (factory==null || factory.canConfigure())
                && ComputationStructure.accepts(this,BigInteger.ONE.shiftLeft(speed),BigInteger.ONE.shiftLeft(lanes))
                && (factory==null || factory.supportsConfiguration(BigInteger.ONE.shiftLeft(speed),BigInteger.ONE.shiftLeft(lanes)));
    }
    public void settings(int speed,int lanes) {
        if(!canSetSettings(speed,lanes))return;
        if(factory!=null)factory.configure(BigInteger.ONE.shiftLeft(speed),BigInteger.ONE.shiftLeft(lanes));
        else runtime.configure(BigInteger.ONE.shiftLeft(speed),BigInteger.ONE.shiftLeft(lanes));
        legacyAcceleration=false;refreshFactoryDisplaySettings();setChanged();
    }
    private void retireLegacyAcceleration(){
        if(!legacyAcceleration || !runtime.canConfigure() || factory!=null && !factory.canConfigure()
                || !ComputationStructure.accepts(this,BigInteger.ONE,runtime.lanes()))return;
        // Finish already-accounted jobs before changing their clock; never reinterpret S as extra Q.
        if(factory!=null){
            if(!factory.supportsConfiguration(BigInteger.ONE,runtime.lanes()))return;
            factory.configure(BigInteger.ONE,runtime.lanes());
        }else runtime.configure(BigInteger.ONE,runtime.lanes());
        legacyAcceleration=false;refreshFactoryDisplaySettings();setChanged();
    }
    protected void failedLoad() { loadFailed = true; }
    public boolean loadFailed() { return loadFailed; }
    @Override public void addAdditionalDrops(net.minecraft.world.level.Level level,BlockPos pos,java.util.List<net.minecraft.world.item.ItemStack> drops) {
        super.addAdditionalDrops(level,pos,drops);
        if(factoryId==null) return;
        var cell=new net.minecraft.world.item.ItemStack(MachinesMod.CELL.get());
        var data=new net.minecraft.nbt.CompoundTag();data.putUUID("factory",factoryId);
        if(level instanceof net.minecraft.server.level.ServerLevel server)com.syaru.ae2machines.world.VirtualCellMetadata.populate(server,data);
        com.syaru.ae2machines.platform.PlatformData.setCellData(cell,data);
        if(factory!=null) {
            if(factory.canDetach()) factory.detach();else { factory.persist();factory.close(); }
        }
        drops.add(cell);factory=null;factoryId=null;
    }
    public void tickServer() {
        if (level == null || level.isClientSide || loadFailed) return;
        if(ComputationStructure.modular(this)) {
            if(ComputationStructure.role(this,ComputationHostBlock.Role.CONTROLLER)) { ComputationStructure.tickController(this);return; }
            if(ComputationStructure.role(this,ComputationHostBlock.Role.CHANNEL))return;
            var state=getBlockState();
            if(state.getValue(ComputationHostBlock.FILLED)!=hasFactory())
                level.setBlock(worldPosition,state.setValue(ComputationHostBlock.FILLED,hasFactory()),2);
            if(!ComputationStructure.runnable(this)) {
                if(factory!=null)suspendFactory();
                if(getBlockState().getValue(AcceleratorBlock.ACTIVE))level.setBlock(worldPosition,getBlockState().setValue(AcceleratorBlock.ACTIVE,false),2);
                return;
            }
        }
        if(factoryId!=null && !factoryLoadAttempted && level.getGameTime()>=nextFactoryLoadTick) {
            factoryLoadAttempted=true;
            try {
                factory=openFactory(factoryId);runtime=factory.runtime();
                loadedFactorySettings();
            }
            catch(FactorySession.SourceChunkUnavailable e) { factoryLoadAttempted=false;nextFactoryLoadTick=level.getGameTime()+20; }
            catch(RuntimeException e) { failedLoad();org.slf4j.LoggerFactory.getLogger(BaseAccelerator.class).error("Factory {} could not resume",factoryId,e);return; }
        }
        if(factoryId!=null && factory==null) {
            if(getBlockState().getValue(AcceleratorBlock.ACTIVE))level.setBlock(worldPosition,getBlockState().setValue(AcceleratorBlock.ACTIVE,false),2);
            return;
        }
        retireLegacyAcceleration();
        if(factory!=null && settingsFit()) factory.tick(level.getGameTime());else if(factory==null) runtime.tick(level.getGameTime());
        boolean active=factoryOnline();
        if(getBlockState().getValue(AcceleratorBlock.ACTIVE)!=active)
            level.setBlock(worldPosition,getBlockState().setValue(AcceleratorBlock.ACTIVE,active),2);
    }
    public int receiveEnergy(int offered,boolean simulate) {
        if (level == null || level.isClientSide || isRemoved() || loadFailed || offered <= 0 || factory==null) return 0;
        return factory.receiveFE(offered,level.getGameTime(),simulate);
    }
    private FactoryHandle openFactory(java.util.UUID id) {
        var record=com.syaru.ae2machines.world.FactoryJournal.open((net.minecraft.server.level.ServerLevel)level,id).read();
        return record.getCompound("blueprint").getInt("schema")==4?NativeWorldSession.open(this,id):FactorySession.open(this,id);
    }
    public int storedFE() { return factory==null ? 0 : factory.storedFE(); }
    public int capacityFE() { return factory==null ? 0 : factory.capacityFE(); }
    public long receiveEU(long voltage,long amps,boolean simulate) {
        return level==null || level.isClientSide || isRemoved() || loadFailed || factory==null ? 0 : factory.receiveEU(voltage,amps,level.getGameTime(),simulate);
    }
    @Override public BigInteger receiveExactEU(long voltage,BigInteger offered,boolean simulate) {
        com.syaru.ae2machines.engine.ExactAmounts.nonnegative(offered);
        return level==null || level.isClientSide || isRemoved() || loadFailed || !(factory instanceof NativeWorldSession nativeWorld)
                ?BigInteger.ZERO:nativeWorld.receiveExactEU(voltage,offered,simulate);
    }
    public int extractEnergy(int amount,boolean simulate) {
        return level==null || level.isClientSide || isRemoved() || loadFailed || factory==null?0:factory.extractFE(amount,level.getGameTime(),simulate);
    }
    public boolean energyOutput(){return factory!=null && factory.exposesEnergyOutput();}
    public long voltageEU() { return factory==null?0:factory.voltageEU(); }
    public long demandEU() { return factory==null?0:factory.demandEU(); }
    @Override public IManagedGridNode getMainNode() { return node; }
    @Override public void onReady() {
        super.onReady();
        if(ComputationStructure.modular(this)) {
            node.setExposedOnSides(EnumSet.noneOf(Direction.class));
            if(ComputationStructure.role(this,ComputationHostBlock.Role.CHANNEL))
                node.setFlags(GridFlags.DENSE_CAPACITY).setExposedOnSides(EnumSet.of(getBlockState().getValue(AcceleratorBlock.FACING)));
            else if(ComputationStructure.role(this,ComputationHostBlock.Role.CONTROLLER))node.setFlags(GridFlags.REQUIRE_CHANNEL);
        }
        node.setVisualRepresentation(getBlockState().getBlock().asItem());node.create(level,worldPosition);
    }
    public void suspendFactory() { if(factory!=null) { factory.close();factory=null; }factoryLoadAttempted=false;nextFactoryLoadTick=Long.MIN_VALUE; }
    @Override public void setRemoved() { ComputationStructure.detached(this);suspendFactory();node.destroy();super.setRemoved(); }
    @Override public void clearRemoved() { super.clearRemoved();scheduleInit(); }
    public boolean canAccess(Player player) {
        return level == player.level() && !isRemoved() && !player.isSpectator()
                && player.distanceToSqr(worldPosition.getX()+0.5,worldPosition.getY()+0.5,worldPosition.getZ()+0.5) <= 64
                && appeng.util.Platform.hasPermissions(new appeng.api.util.DimensionalBlockPos(this),player);
    }
}
