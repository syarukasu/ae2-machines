package com.syaru.ae2machines.world;

import com.syaru.ae2machines.compat.mek.MekRestorationStorage;
import java.math.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import static com.syaru.ae2machines.world.NativeFactoryCompiler.require;

/** A restored cell owns only these assets, never a second copy of the physical factory. */
public final class RestorationRemainder {
    public static final String KEY="restorationRemainder";
    private RestorationRemainder(){}
    public static boolean has(CompoundTag record){return !entries(record).isEmpty();}
    private static ListTag entries(CompoundTag record){
        if(!record.contains(KEY))return new ListTag();var saved=record.getCompound(KEY);
        require(saved.getInt("schema")==1 && saved.contains("entries",Tag.TAG_LIST),"INVALID_RESTORATION_REMAINDER");
        var entries=(ListTag)saved.get("entries");
        require(entries.isEmpty() || entries.getElementType()==Tag.TAG_COMPOUND,"INVALID_RESTORATION_ENTRY_LIST");
        for(var value:entries){var entry=(CompoundTag)value;
            require(entry.contains("pos",Tag.TAG_LONG) && entry.contains("kind",Tag.TAG_STRING) && entry.contains("block",Tag.TAG_STRING),"INVALID_RESTORATION_ENTRY");
            if(entry.contains("thermal"))require(new BigDecimal(entry.getString("thermal")).signum()>0 && new BigDecimal(entry.getString("temperature")).signum()>=0,"INVALID_RESTORATION_HEAT");
            else require(new BigInteger(entry.getByteArray("amount")).signum()>0 && entry.contains("payload",Tag.TAG_COMPOUND),"INVALID_RESTORATION_AMOUNT");
        }
        return entries;
    }
    public static void cellData(CompoundTag data,CompoundTag record){
        if(has(record))data.putBoolean("restorationRemainder",true);else VirtualCellMetadata.clear(data);
    }
    public static void recover(ServerLevel level,Player player,FactoryJournal journal){
        var record=journal.read();var blueprint=record.getCompound("blueprint");
        require(record.getString("state").equals("RESTORED"),"FACTORY_NOT_RESTORED");
        require(blueprint.getUUID("player").equals(player.getUUID()),"FACTORY_OWNER_MISMATCH");
        require(blueprint.getString("dimension").equals(level.dimension().location().toString()),"WRONG_DIMENSION");
        var entries=entries(record);if(entries.isEmpty())return;
        require(com.syaru.ae2machines.runtime.NativeMachines.mek(),"MEKANISM_REQUIRED_TO_RECOVER_NATIVE_ASSETS");
        var min=BlockPos.of(blueprint.getLong("first"));var max=BlockPos.of(blueprint.getLong("last"));
        for(var pos:BlockPos.betweenClosed(min,max)){
            require(level.hasChunkAt(pos) && level.mayInteract(player,pos),"RESTORE_CHUNK_OR_PERMISSION");
            var be=level.getBlockEntity(pos);if(be!=null)require(appeng.util.Platform.hasPermissions(new appeng.api.util.DimensionalBlockPos(be),player),"AE2_PERMISSION_DENIED");
        }
        var ports=MekRestorationStorage.ports(level,min,max);var changes=new ArrayList<Runnable>();
        for(var value:entries){var entry=(CompoundTag)value;var address=MekRestorationStorage.address(entry);
            var pos=BlockPos.of(address.pos());
            if(!BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString().equals(entry.getString("block")))continue;
            if(entry.contains("thermal")){
                var port=ports.heat.get(address);if(port==null)continue;
                var remaining=new BigDecimal(entry.getString("thermal"));var beforeHeat=port.heat().get();var beforePending=port.pending().get();
                var limit=new BigDecimal(entry.getString("temperature")).multiply(port.capacity().get());
                var offered=remaining.min(limit.subtract(beforeHeat).subtract(beforePending).max(BigDecimal.ZERO));if(offered.signum()==0)continue;
                changes.add(()->{
                    BigDecimal actual=BigDecimal.ZERO;var proposed=offered;
                    for(int attempt=0;attempt<8;attempt++){
                        if(address.kind().equals("plasma"))port.write().accept(beforeHeat.add(proposed),BigDecimal.ZERO);
                        else port.write().accept(beforeHeat,beforePending.add(proposed));
                        actual=port.heat().get().add(port.pending().get()).subtract(beforeHeat).subtract(beforePending);
                        if(actual.signum()>=0 && actual.compareTo(offered)<=0)break;
                        port.write().accept(beforeHeat,beforePending);
                        proposed=MekRestorationStorage.decimal(Math.nextDown(proposed.doubleValue()));
                    }
                    require(actual.signum()>=0 && actual.compareTo(offered)<=0,"RESTORATION_HEAT_ACCEPTANCE_MISMATCH");
                    entry.putString("thermal",remaining.subtract(actual).toPlainString());port.owner().setChanged();
                });
            }else{
                var port=ports.amount(address);if(port==null)continue;
                var before=MekRestorationStorage.nonnegative(port.stored().get());var payload=entry.getCompound("payload");
                if(before.signum()>0 && !port.payload().get().equals(payload))continue;
                var remaining=new BigInteger(entry.getByteArray("amount"));var capacity=MekRestorationStorage.nonnegative(port.capacity().apply(payload));
                var offered=remaining.min(capacity.subtract(before).max(BigInteger.ZERO));if(offered.signum()==0)continue;
                changes.add(()->{
                    // Several former pipe shares can now target one native network. Re-read its room.
                    var current=port.stored().get();if(current.signum()>0 && !port.payload().get().equals(payload))return;
                    var offer=remaining.min(port.capacity().apply(payload).subtract(current).max(BigInteger.ZERO));if(offer.signum()==0)return;
                    port.write().accept(payload,current.add(offer));var accepted=port.stored().get().subtract(current);
                    require(accepted.signum()>=0 && accepted.compareTo(offer)<=0 && (accepted.signum()==0 || port.payload().get().equals(payload)),"RESTORATION_NATIVE_ACCEPTANCE_MISMATCH");
                    entry.putByteArray("amount",remaining.subtract(accepted).toByteArray());port.owner().setChanged();
                });
            }
        }
        if(changes.isEmpty())return;
        var participants=new HashSet<>(NativeRegionMove.blockEntities(level,min,max));
        var tx=ExternalHandoffs.beginRestorationRecovery(level,journal,participants,min,max);
        try{
            changes.forEach(Runnable::run);
            var remaining=new ListTag();for(var value:entries){var entry=(CompoundTag)value;
                if(entry.contains("thermal")?new BigDecimal(entry.getString("thermal")).signum()>0:new BigInteger(entry.getByteArray("amount")).signum()>0)remaining.add(entry);
            }
            var ledger=new CompoundTag();ledger.putInt("schema",1);ledger.put("entries",remaining);
            record=journal.read();record.put(KEY,ledger);journal.write(record);tx.commit();
        }catch(RuntimeException failure){throw new ExternalHandoffs.PersistenceFailure(failure);}
    }
}
