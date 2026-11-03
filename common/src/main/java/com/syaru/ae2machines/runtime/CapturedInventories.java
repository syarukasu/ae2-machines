package com.syaru.ae2machines.runtime;

import appeng.api.storage.MEStorage;
import com.syaru.ae2machines.platform.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.properties.ChestType;
import java.util.*;
import static com.syaru.ae2machines.world.NativeFactoryCompiler.require;

/** Owned native inventories, never registered as ticking world block entities. */
public final class CapturedInventories {
    private final Map<Long,BlockEntity> blocks=new LinkedHashMap<>();
    private final Map<Long,MEStorage> containers=new HashMap<>();
    private final Map<Long,BufferedMachine> machines=new HashMap<>();
    private final Map<Long,java.math.BigInteger> clockWork=new HashMap<>();
    private final Map<Long,ParallelFurnaces> furnaces=new HashMap<>();
    private final Runnable changed;
    private final CapturedPipes pipes;
    private final java.util.function.BiFunction<Long,Direction,MEStorage> nativeEndpoint;
    public static boolean supported(BlockEntity be) {
        return be!=null && (be.getClass()==ChestBlockEntity.class || be.getClass()==BarrelBlockEntity.class);
    }
    public static void validate(Collection<BlockEntity> devices) {
        var byPos=new HashMap<Long,BlockEntity>();for(var be:devices)byPos.put(be.getBlockPos().asLong(),be);
        for(var be:devices)if(supported(be)) {
            var data=PlatformData.saveBlock(be);
            require(!data.contains("LootTable") && !data.contains("lock") && data.getString("Lock").isEmpty(),"LOCKED_OR_UNGENERATED_CONTAINER");
            if(be instanceof ChestBlockEntity && be.getBlockState().getValue(ChestBlock.TYPE)!=ChestType.SINGLE) {
                var other=byPos.get(be.getBlockPos().relative(ChestBlock.getConnectedDirection(be.getBlockState())).asLong());
                require(other instanceof ChestBlockEntity && other.getBlockState().getBlock()==be.getBlockState().getBlock()
                        && other.getBlockState().getValue(ChestBlock.TYPE)==(be.getBlockState().getValue(ChestBlock.TYPE)==ChestType.LEFT?ChestType.RIGHT:ChestType.LEFT)
                        && other.getBlockState().getValue(ChestBlock.FACING)==be.getBlockState().getValue(ChestBlock.FACING),"INCOMPLETE_CAPTURED_DOUBLE_CHEST");
            }
        }
    }
    public CapturedInventories(ServerLevel level,CompoundTag blueprint,CompoundTag saved,Runnable changed) {
        this(level,blueprint,saved,changed,(pos,side)->null);
    }
    public CapturedInventories(ServerLevel level,CompoundTag blueprint,CompoundTag saved,Runnable changed,
                               java.util.function.BiFunction<Long,Direction,MEStorage> nativeEndpoint) {
        this.nativeEndpoint=nativeEndpoint;
        this.changed=changed;
        for(var value:blueprint.getList("blocks",Tag.TAG_COMPOUND)) {
            var record=(CompoundTag)value;var pos=BlockPos.of(record.getLong("pos"));
            var states=saved.getCompound("blockStates");String key=Long.toString(pos.asLong());
            var state=NbtUtils.readBlockState(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK),states.contains(key)?states.getCompound(key):record.getCompound("state"));
            if(!(state.getBlock() instanceof EntityBlock factory))continue;
            var be=factory.newBlockEntity(pos,state);if(NativeAssemblers.supports(be) || !supported(be) && !NativeMachines.buffered(be))continue;
            be.setLevel(level);String id=Long.toString(pos.asLong());
            PlatformData.loadBlock(be,saved.contains(id)?saved.getCompound(id):record.getCompound("data"));
            blocks.put(pos.asLong(),be);
        }
        validate(blocks.values());
        var clocks=saved.getCompound("clockWork");
        for(var key:clocks.getAllKeys()) {
            long pos=Long.parseLong(key);var remaining=new java.math.BigInteger(clocks.getByteArray(key));
            require(VanillaFurnaces.supports(blocks.get(pos)) && remaining.signum()>0,"INVALID_NATIVE_CLOCK_WORK");
            clockWork.put(pos,remaining);
        }
        for(var be:blocks.values()) {
            if(VanillaFurnaces.supports(be) && saved.getCompound("parallelFurnaces").contains(Long.toString(be.getBlockPos().asLong()))) {
                long pos=be.getBlockPos().asLong();
                var pool=new ParallelFurnaces((AbstractFurnaceBlockEntity)be,saved.getCompound("parallelFurnaces").getCompound(Long.toString(pos)),changed);
                require(!clockWork.containsKey(pos),"DUPLICATE_FURNACE_CLOCK");
                furnaces.put(pos,pool);machines.put(pos,pool);continue;
            }
            if(NativeMachines.buffered(be)){machines.put(be.getBlockPos().asLong(),NativeMachines.buffer(be,changed));continue;}
            Container inventory=(Container)be;
            if(be instanceof ChestBlockEntity && be.getBlockState().getValue(ChestBlock.TYPE)!=ChestType.SINGLE) {
                var other=(Container)blocks.get(be.getBlockPos().relative(ChestBlock.getConnectedDirection(be.getBlockState())).asLong());
                inventory=be.getBlockState().getValue(ChestBlock.TYPE)==ChestType.RIGHT
                        ?new CompoundContainer(inventory,other):new CompoundContainer(other,inventory);
            }
            containers.put(be.getBlockPos().asLong(),CapturedInventoryAccess.container(inventory,changed));
        }
        pipes=CapturedPipes.open(level,blueprint,saved.getCompound("pipes"),this::direct,this::preview,changed);
    }
    private CapturedPipes.Preview preview(long pos) {
        var be=blocks.get(pos);if(be==null)return null;
        if(furnaces.containsKey(pos)) {
            var copy=new ParallelFurnaces((AbstractFurnaceBlockEntity)be,furnaces.get(pos).save(),()->{});
            return new CapturedPipes.Preview(Set.of(pos),copy::storage);
        }
        if(supported(be)) {
            var aliases=new HashSet<Long>();aliases.add(pos);
            Container live=(Container)be;
            if(be instanceof ChestBlockEntity && be.getBlockState().getValue(ChestBlock.TYPE)!=ChestType.SINGLE) {
                aliases.add(be.getBlockPos().relative(ChestBlock.getConnectedDirection(be.getBlockState())).asLong());
                var other=(Container)blocks.get(be.getBlockPos().relative(ChestBlock.getConnectedDirection(be.getBlockState())).asLong());
                live=be.getBlockState().getValue(ChestBlock.TYPE)==ChestType.RIGHT?new CompoundContainer(live,other):new CompoundContainer(other,live);
            }
            var copy=new net.minecraft.world.SimpleContainer(live.getContainerSize());
            for(int i=0;i<live.getContainerSize();i++)copy.setItem(i,live.getItem(i).copy());
            var storage=CapturedInventoryAccess.container(copy,()->{});return new CapturedPipes.Preview(aliases,side->storage);
        }
        var copy=((EntityBlock)be.getBlockState().getBlock()).newBlockEntity(be.getBlockPos(),be.getBlockState());
        copy.setLevel(be.getLevel());PlatformData.loadBlock(copy,PlatformData.saveBlock(be));
        var machine=NativeMachines.buffer(copy,()->{});return new CapturedPipes.Preview(Set.of(pos),machine::storage);
    }
    private MEStorage direct(long pos,Direction side){var machine=machines.get(pos);var local=machine==null?containers.get(pos):machine.storage(side);return local==null?nativeEndpoint.apply(pos,side):local;}
    public MEStorage at(long pos,Direction side){var target=direct(pos,side);return target!=null?target:pipes==null?null:pipes.at(pos,side);}
    public BufferedMachine machine(long pos){return machines.get(pos);}
    public void configureFurnace(long pos,java.math.BigInteger count){
        if(!VanillaFurnaces.supports(blocks.get(pos)))return;
        var pool=furnaces.get(pos);
        if(pool==null && count.equals(java.math.BigInteger.ONE))return;
        require(!clockWork.containsKey(pos),"FURNACE_CLOCK_IN_PROGRESS");
        if(pool==null)pool=new ParallelFurnaces((AbstractFurnaceBlockEntity)blocks.get(pos),new CompoundTag(),changed);
        if(count.equals(java.math.BigInteger.ONE)) {
            var single=pool.restorable();require(single!=null,"FURNACE_PARALLEL_STATE_NOT_DRAINED");
            blocks.put(pos,single);furnaces.remove(pos);machines.put(pos,VanillaFurnaces.buffer(single,changed));
        } else {pool.configure(count);furnaces.put(pos,pool);machines.put(pos,pool);}
        changed.run();
    }
    public boolean furnacesRestorable(){return clockWork.isEmpty() && furnaces.values().stream().allMatch(pool->pool.restorable()!=null);}
    public void tickNative(long pos){tickNative(pos,java.math.BigInteger.ONE);}
    public void tickNative(long pos,java.math.BigInteger speed){
        var machine=machines.get(pos);if(machine==null)return;
        if(VanillaFurnaces.supports(blocks.get(pos))) {
            if(furnaces.containsKey(pos)){furnaces.get(pos).advance(speed);changed.run();return;}
            var pending=clockWork.getOrDefault(pos,speed);var allowance=pending.min(speed);
            var slice=FurnaceClock.advance((AbstractFurnaceBlockEntity)blocks.get(pos),allowance,512);
            var remaining=pending.subtract(allowance).add(slice.remaining());
            if(remaining.signum()==0)clockWork.remove(pos);else clockWork.put(pos,remaining);
            changed.run();
        } else machine.tickNative(speed);
    }
    public Map<Long,BufferedMachine> machines(){return Collections.unmodifiableMap(machines);}
    public void tick(){if(pipes!=null)pipes.tick();}
    public CompoundTag save(){var tag=new CompoundTag();var states=new CompoundTag();blocks.forEach((pos,be)->{tag.put(Long.toString(pos),PlatformData.saveBlock(be));if(VanillaFurnaces.supports(be))states.put(Long.toString(pos),NbtUtils.writeBlockState(be.getBlockState()));});tag.put("blockStates",states);var pools=new CompoundTag();furnaces.forEach((pos,pool)->pools.put(Long.toString(pos),pool.save()));tag.put("parallelFurnaces",pools);var clocks=new CompoundTag();clockWork.forEach((pos,n)->clocks.putByteArray(Long.toString(pos),n.toByteArray()));tag.put("clockWork",clocks);if(pipes!=null)tag.put("pipes",pipes.save());return tag;}
    public void restore(CompoundTag blueprint){
        var saved=save();var states=saved.getCompound("blockStates");
        for(var entry:furnaces.entrySet()) {
            var be=entry.getValue().restorable();require(be!=null,"FURNACE_PARALLEL_STATE_NOT_DRAINED");
            String id=Long.toString(entry.getKey());saved.put(id,PlatformData.saveBlock(be));states.put(id,NbtUtils.writeBlockState(be.getBlockState()));
        }
        for(var value:blueprint.getList("blocks",Tag.TAG_COMPOUND)){var b=(CompoundTag)value;var id=Long.toString(b.getLong("pos"));if(saved.contains(id))b.put("data",saved.getCompound(id));if(states.contains(id))b.put("state",states.getCompound(id));}if(pipes!=null)pipes.restore(blueprint);
    }
}
