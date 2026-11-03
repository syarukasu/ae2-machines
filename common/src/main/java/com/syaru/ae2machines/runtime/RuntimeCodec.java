package com.syaru.ae2machines.runtime;

import appeng.api.stacks.AEKey;
import com.syaru.ae2machines.engine.*;
import net.minecraft.nbt.*;
import java.math.BigInteger;
import java.util.*;
import java.util.function.Function;

/** Versioned snapshot: stores native recipe coefficients and paid work, not expected receipts. */
public final class RuntimeCodec {
    private RuntimeCodec() {}
    public static CompoundTag write(ProcessingRuntime runtime, Function<AEKey, CompoundTag> keyWriter) {
        var tag = new CompoundTag(); tag.putInt("schema",5);
        tag.putLong("lastWorkTick",runtime.lastWorkTick());
        var power=runtime.powerCheckpoint();
        if(power!=null) {
            var p=new CompoundTag();p.put("start",jobTag(power.start(),keyWriter));
            putNumber(p,"supplied",power.supplied());tag.put("power",p);
        }
        tag.putBoolean("quarantined",runtime.quarantined());
        putNumber(tag,"speed",runtime.speed()); putNumber(tag,"lanes",runtime.lanes());
        var e = runtime.energy();
        tag.putBoolean("inputPerTick",e.limitPerTick());
        tag.putString("unit",e.unit()); putNumber(tag,"capacity",e.capacity());
        putNumber(tag,"inputRate",e.maxInput()); putNumber(tag,"voltage",e.voltage());
        putNumber(tag,"stored",e.stored());
        var jobs = new ListTag();
        for (var c : runtime.snapshots()) {
            jobs.add(jobTag(c,keyWriter));
        }
        tag.put("jobs",jobs);
        var nativeJobs=new ListTag();
        runtime.nativeSnapshots().forEach((id,state)->{
            var entry=new CompoundTag();entry.putUUID("id",id);
            entry.put("remaining",amounts(state.remaining(),keyWriter));entry.put("produced",amounts(state.produced(),keyWriter));
            putNumber(entry,"energyUsed",state.energyUsed());entry.putLong("calls",state.calls());
            entry.putBoolean("consumesAtStart",state.consumesAtStart());nativeJobs.add(entry);
        });
        tag.put("nativeJobs",nativeJobs);
        var forgotten=new ListTag();
        runtime.forgotten().forEach((id,digest)->{
            var entry=new CompoundTag();entry.putUUID("id",id);entry.putString("digest",digest);forgotten.add(entry);
        });
        tag.put("forgotten",forgotten);return tag;
    }
    private static CompoundTag jobTag(BatchJob.Checkpoint<AEKey> c,Function<AEKey,CompoundTag> keyWriter) {
            var j = new CompoundTag(); j.putUUID("id",c.id()); j.putString("digest",c.digest());
            j.putString("recipe",c.recipe().id());
            j.put("inputs",amounts(c.recipe().inputs(),keyWriter)); j.put("outputs",amounts(c.recipe().outputs(),keyWriter));
            putNumber(j,"duration",c.recipe().duration()); putNumber(j,"energyPerWork",c.recipe().energyPerWork());
            j.putString("energyUnit",c.recipe().energyUnit()); putNumber(j,"minimumVoltage",c.recipe().minimumVoltage());
            putNumber(j,"total",c.total()); putNumber(j,"lanes",c.lanes()); putNumber(j,"speed",c.speed());
            putNumber(j,"completed",c.completed());
            var phases = new ListTag();
            c.phases().forEach((work,count) -> {
                var phase = new CompoundTag();putNumber(phase,"work",work);putNumber(phase,"count",count);phases.add(phase);
            });
            j.put("phases",phases);
            putNumber(j,"energyUsed",c.energyUsed()); j.putString("state",c.state().name()); return j;
    }
    public static ProcessingRuntime read(CompoundTag tag, Function<CompoundTag,AEKey> keyReader,
                                         ProcessingRuntime.DurableCheckpoint checkpoint) {
        int schema=tag.getInt("schema");
        if (schema != 2 && schema != 3 && schema != 4 && schema != 5) throw new IllegalArgumentException("Unknown runtime schema");
        if(schema>=3 && !tag.contains("lastWorkTick",Tag.TAG_LONG)) throw new IllegalArgumentException("Missing work tick");
        var energy = new MeteredEnergy(tag.getString("unit"),number(tag,"capacity"),number(tag,"inputRate"),
                number(tag,"voltage"),number(tag,"stored"),!tag.contains("inputPerTick") || tag.getBoolean("inputPerTick"));
        var runtime = new ProcessingRuntime(energy,checkpoint);
        runtime.configure(number(tag,"speed"),number(tag,"lanes"));
        var jobs = new ArrayList<BatchJob.Checkpoint<AEKey>>();
        for (Tag value : requiredList(tag,"jobs")) {
            jobs.add(readJob((CompoundTag)value,keyReader));
        }
        runtime.restore(jobs);
        if(schema>=3) {
            var p=tag.getCompound("power");
            runtime.restorePower(tag.getLong("lastWorkTick"),tag.contains("power")
                    ? new DemandWork.Checkpoint<>(tag.getLong("lastWorkTick"),readJob(p.getCompound("start"),keyReader),number(p,"supplied")):null);
        }
        var forgotten=new LinkedHashMap<UUID,String>();
        for(var value:requiredList(tag,"forgotten")) {
            var entry=(CompoundTag)value;
            if(forgotten.put(entry.getUUID("id"),entry.getString("digest"))!=null)
                throw new IllegalArgumentException("Duplicate release record");
        }
        if(schema>=4) {
            var states=new LinkedHashMap<UUID,NativeWorkLedger.Snapshot>();
            for(var value:requiredList(tag,"nativeJobs")) {
                var entry=(CompoundTag)value;
                if(states.put(entry.getUUID("id"),new NativeWorkLedger.Snapshot(readAmounts(requiredList(entry,"remaining"),keyReader),
                        readAmounts(requiredList(entry,"produced"),keyReader),number(entry,"energyUsed"),entry.getLong("calls"),
                        schema>=5 && entry.getBoolean("consumesAtStart")))!=null)
                    throw new IllegalArgumentException("Duplicate native execution");
            }
            runtime.restoreNative(states);
        }
        runtime.restoreForgotten(forgotten);runtime.restoreQuarantine(tag.getBoolean("quarantined"));return runtime;
    }
    private static BatchJob.Checkpoint<AEKey> readJob(CompoundTag j,Function<CompoundTag,AEKey> keyReader) {
            var recipe = new Recipe<AEKey>(j.getString("recipe"),readAmounts(requiredList(j,"inputs"),keyReader),
                    readAmounts(requiredList(j,"outputs"),keyReader),number(j,"duration"),number(j,"energyPerWork"),
                    j.getString("energyUnit"),number(j,"minimumVoltage"));
            var phases = new LinkedHashMap<BigInteger,BigInteger>();
            for (var valuePhase : requiredList(j,"phases")) {
                var phase = (CompoundTag)valuePhase;
                if (phases.put(number(phase,"work"),ExactAmounts.positive(number(phase,"count"))) != null)
                    throw new IllegalArgumentException("Duplicate phase");
            }
            return new BatchJob.Checkpoint<>(j.getUUID("id"),j.getString("digest"),recipe,number(j,"total"),
                    number(j,"lanes"),number(j,"speed"),number(j,"completed"),phases,number(j,"energyUsed"),
                    BatchJob.State.valueOf(j.getString("state")));
    }
    private static BigInteger number(CompoundTag tag,String name) {
        return ExactAmounts.nonnegative(new BigInteger(tag.getByteArray(name)));
    }
    private static ListTag requiredList(CompoundTag tag,String name) {
        if (!(tag.get(name) instanceof ListTag list)
                || !list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)
            throw new IllegalArgumentException("Missing or invalid " + name);
        return list;
    }
    private static void putNumber(CompoundTag tag,String name,BigInteger n) {
        tag.putByteArray(name,ExactAmounts.nonnegative(n).toByteArray());
    }
    static ListTag amounts(Map<AEKey,BigInteger> map,Function<AEKey,CompoundTag> writer) {
        var list = new ListTag(); map.forEach((key,n) -> {
            var tag = new CompoundTag();tag.put("key",writer.apply(key));putNumber(tag,"amount",n);list.add(tag);
        }); return list;
    }
    static Map<AEKey,BigInteger> readAmounts(ListTag list,Function<CompoundTag,AEKey> reader) {
        var result = new LinkedHashMap<AEKey,BigInteger>();
        for (var value : list) {
            var tag = (CompoundTag)value; var key = Objects.requireNonNull(reader.apply(tag.getCompound("key")));
            if (result.put(key,ExactAmounts.positive(number(tag,"amount"))) != null) throw new IllegalArgumentException("Duplicate key");
        }
        return Map.copyOf(result);
    }
}
