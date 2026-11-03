package com.syaru.ae2machines.compat.mek;

import com.syaru.ae2machines.platform.MekRestorationPorts;
import java.math.*;
import java.util.*;
import java.util.function.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Native capacity projection is confined to a prepared spatial restore, never normal work. */
public final class MekRestorationStorage {
    private static final ThreadLocal<Boolean> PROJECTING=new ThreadLocal<>();
    private MekRestorationStorage(){}
    public static boolean projecting(){return Boolean.TRUE.equals(PROJECTING.get());}
    public static final class Projection implements AutoCloseable {
        private final Boolean before=PROJECTING.get();
        private Projection(){PROJECTING.set(true);}
        @Override public void close(){if(before==null)PROJECTING.remove();else PROJECTING.set(before);}
    }
    public static Projection project(){return new Projection();}
    public record Address(long pos,String kind,int index){}
    public record AmountPort(BlockEntity owner,Address address,Object identity,Supplier<BigInteger> stored,
                             Function<CompoundTag,BigInteger> capacity,Supplier<CompoundTag> payload,
                             BiConsumer<CompoundTag,BigInteger> write){}
    public record HeatPort(BlockEntity owner,Address address,Object identity,Supplier<BigDecimal> heat,
                           Supplier<BigDecimal> pending,Supplier<BigDecimal> capacity,int lanes,
                           BiConsumer<BigDecimal,BigDecimal> write){}
    public static final class Ports {
        public final Map<Address,AmountPort> amounts=new LinkedHashMap<>();
        public final Map<Address,HeatPort> heat=new LinkedHashMap<>();
        private final Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        private final Map<Object,Address> identities=new IdentityHashMap<>();
        private final Map<Address,Address> aliases=new HashMap<>();
        public void amount(BlockEntity owner,String kind,int index,Object identity,Supplier<BigInteger> stored,
                           Supplier<BigInteger> capacity,Supplier<CompoundTag> payload,BiConsumer<CompoundTag,BigInteger> write){
            amount(owner,kind,index,identity,stored,ignored->capacity.get(),payload,write);
        }
        public void amount(BlockEntity owner,String kind,int index,Object identity,Supplier<BigInteger> stored,
                           Function<CompoundTag,BigInteger> capacity,Supplier<CompoundTag> payload,BiConsumer<CompoundTag,BigInteger> write){
            if(seen.add(identity)){
                var address=new Address(owner.getBlockPos().asLong(),kind,index);
                if(amounts.put(address,new AmountPort(owner,address,identity,stored,capacity,payload,write))!=null)throw new IllegalStateException("DUPLICATE_RESTORE_CONTAINER");
                identities.put(identity,address);
            }
        }
        public void alias(BlockEntity owner,String kind,Object identity){
            var target=identities.get(identity);if(target==null)throw new IllegalStateException("RESTORE_ALIAS_WITHOUT_CONTAINER");
            aliases.put(new Address(owner.getBlockPos().asLong(),kind,0),target);
        }
        public AmountPort amount(Address address){
            var port=amounts.get(aliases.getOrDefault(address,address));
            return port!=null?port:address.kind.startsWith("network:")?amounts.get(new Address(address.pos,"buffer:"+address.kind.substring(8),address.index)):null;
        }
        public void heat(BlockEntity owner,String kind,int index,Object identity,Supplier<BigDecimal> heatValue,
                         Supplier<BigDecimal> pending,Supplier<BigDecimal> capacity,int lanes,
                         BiConsumer<BigDecimal,BigDecimal> write){
            if(seen.add(identity)){
                var address=new Address(owner.getBlockPos().asLong(),kind,index);
                if(heat.put(address,new HeatPort(owner,address,identity,heatValue,pending,capacity,lanes,write))!=null)throw new IllegalStateException("DUPLICATE_RESTORE_HEAT_CONTAINER");
            }
        }
    }
    public static Ports ports(ServerLevel level,BlockPos min,BlockPos max){
        var ports=new Ports();MekRestorationPorts.collect(level,min,max,ports);return ports;
    }
    public static void refreshSaveShares(List<BlockEntity> blocks){
        var seen=Collections.newSetFromMap(new IdentityHashMap<>());
        for(var be:blocks)if(be instanceof mekanism.common.tile.transmitter.TileEntityTransmitter tile){
            com.syaru.ae2machines.platform.MekTransportStorage.refreshOrphanShare(tile);
            if(tile.getTransmitter().getTransmitterNetwork() instanceof mekanism.common.lib.transmitter.DynamicBufferedNetwork<?,?,?,?> network && seen.add(network))
                ((com.syaru.ae2machines.mixin.MekSaveSharesAccess)network).machines$saveShareTime(Long.MIN_VALUE);
        }
    }
    public static final class Plan {
        private final List<Runnable> checks=new ArrayList<>(),changes=new ArrayList<>();
        private final ListTag entries=new ListTag();
        public CompoundTag ledger(){
            var saved=new ListTag();for(var value:entries){var entry=(CompoundTag)value;
                if(entry.contains("thermal")?new BigDecimal(entry.getString("thermal")).signum()>0:new BigInteger(entry.getByteArray("amount")).signum()>0)saved.add(entry.copy());
            }
            var tag=new CompoundTag();tag.putInt("schema",1);tag.put("entries",saved);return tag;
        }
        public void verify(){checks.forEach(Runnable::run);}
        public void apply(){changes.forEach(Runnable::run);}
    }
    public static Plan plan(ServerLevel level,BlockPos min,BlockPos max){return plan(ports(level,min,max));}
    public static Plan plan(Ports ports){
        var plan=new Plan();
        for(var port:ports.amounts.values()){
            var before=nonnegative(port.stored.get());if(before.signum()==0)continue;
            var payload=port.payload.get().copy();var capacity=nonnegative(port.capacity.apply(payload));
            if(before.compareTo(capacity)<=0)continue;
            var entry=entry(port.owner,port.address);
            entry.put("payload",payload);entry.putByteArray("amount",before.subtract(capacity).toByteArray());plan.entries.add(entry);
            plan.checks.add(()->require(port.stored.get().equals(before) && port.capacity.apply(payload).equals(capacity) && port.payload.get().equals(payload),"RESTORE_CONTAINER_CHANGED"));
            plan.changes.add(()->{port.write.accept(payload,capacity);require(port.stored.get().equals(capacity),"RESTORE_CONTAINER_REJECTED_NATIVE_CAPACITY");});
        }
        for(var port:ports.heat.values()){
            if(port.lanes<=1)continue;
            var before=port.heat.get();var pending=port.pending.get();var total=before.add(pending);
            require(total.signum()>=0 && Double.isFinite(total.doubleValue()),"INVALID_NATIVE_THERMAL_BALANCE");
            double factor=port.lanes;
            var kept=decimal(before.doubleValue()/factor);var keptPending=decimal(pending.doubleValue()/factor);
            // Keep both native queues. The residual owns the exact decimal difference, including rounding.
            while(kept.add(keptPending).compareTo(total)>0)kept=decimal(Math.nextDown(kept.doubleValue()));
            var remaining=total.subtract(kept).subtract(keptPending);
            var keepHeat=kept;var keepPending=keptPending;
            plan.checks.add(()->require(port.heat.get().compareTo(before)==0 && port.pending.get().compareTo(pending)==0,"RESTORE_HEAT_CHANGED"));
            if(remaining.signum()>0){
                var entry=entry(port.owner,port.address);entry.putString("thermal",remaining.toPlainString());
                entry.putString("temperature",total.divide(port.capacity.get(),MathContext.DECIMAL128).toPlainString());plan.entries.add(entry);
                plan.changes.add(()->{
                    port.write.accept(keepHeat,keepPending);
                    var actualRemaining=total.subtract(port.heat.get()).subtract(port.pending.get());
                    require(actualRemaining.signum()>=0,"RESTORE_HEAT_ROUNDING_CREATED_ENERGY");entry.putString("thermal",actualRemaining.toPlainString());
                });
            }else{
                plan.changes.add(()->{port.write.accept(keepHeat,keepPending);require(port.heat.get().add(port.pending.get()).compareTo(total)==0,"RESTORE_HEAT_ROUNDING_MISMATCH");});
            }
        }
        return plan;
    }
    public static CompoundTag entry(BlockEntity owner,Address address){
        var entry=new CompoundTag();entry.putLong("pos",address.pos);entry.putString("kind",address.kind);entry.putInt("index",address.index);
        entry.putString("block",BuiltInRegistries.BLOCK.getKey(owner.getBlockState().getBlock()).toString());return entry;
    }
    public static Address address(CompoundTag entry){return new Address(entry.getLong("pos"),entry.getString("kind"),entry.getInt("index"));}
    public static BigDecimal decimal(double value){require(Double.isFinite(value),"NONFINITE_NATIVE_HEAT");return BigDecimal.valueOf(value);}
    public static BigInteger nonnegative(BigInteger value){require(value.signum()>=0,"NEGATIVE_NATIVE_ASSET");return value;}
    private static void require(boolean condition,String reason){if(!condition)throw new IllegalArgumentException(reason);}
}
