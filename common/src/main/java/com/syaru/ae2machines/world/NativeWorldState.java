package com.syaru.ae2machines.world;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/** Persistent clock and ownership, not a second copy of the world's inventories. */
public final class NativeWorldState {
    private UUID owner;
    private long ticks;
    private long processingTicks;
    private int processingDepth;
    private boolean legacyClockLoaded;
    private long dayTime;
    private java.math.BigInteger tickDebt=java.math.BigInteger.ZERO;
    private java.math.BigInteger lanes=java.math.BigInteger.ONE;
    private int nativeLanes=1;
    private final java.util.Map<Long,Integer> parallelWork=new java.util.HashMap<>();
    private final java.util.Map<Long,java.util.Map<Integer,Integer>> processWork=new java.util.HashMap<>();
    private int minX, minZ, maxX, maxZ;
    private final Runnable changed;
    private net.minecraft.world.level.GameRules rules = new net.minecraft.world.level.GameRules();

    public NativeWorldState(Runnable changed) { this.changed = changed; }

    public void claim(UUID factory, int x0, int z0, int x1, int z1, long initialDay) {
        if (x1 < x0 || z1 < z0 || (long) x1 - x0 > 7 || (long) z1 - z0 > 7
                || x0 < -1874999 || z0 < -1874999 || x1 > 1874999 || z1 > 1874999)
            throw new IllegalArgumentException("NATIVE_WORLD_INVALID_CHUNK_RANGE");
        if (owner != null) {
            if (!owner.equals(factory) || minX != x0 || minZ != z0 || maxX != x1 || maxZ != z1)
                throw new IllegalStateException("NATIVE_WORLD_ALREADY_OWNED");
            return;
        }
        owner = java.util.Objects.requireNonNull(factory);
        minX = x0; minZ = z0; maxX = x1; maxZ = z1; dayTime = initialDay;
        changed.run();
    }

    public void advance(boolean daylight) {
        long next = Math.incrementExact(ticks);
        long nextProcessing = Math.incrementExact(processingTicks);
        long nextDay = daylight ? Math.incrementExact(dayTime) : dayTime;
        ticks = next; processingTicks = nextProcessing; dayTime = nextDay;legacyClockLoaded=false;
        changed.run();
    }
    public void advance(int count,boolean daylight){
        if(count<0)throw new IllegalArgumentException("Negative world interval");
        long next=Math.addExact(ticks,count),nextDay=daylight?Math.addExact(dayTime,count):dayTime;
        long nextProcessing=Math.addExact(processingTicks,count);
        ticks=next;processingTicks=nextProcessing;dayTime=nextDay;legacyClockLoaded=false;changed.run();
    }

    public long ticks() { return ticks; }
    public long processingTicks(){return processingTicks;}
    public boolean legacyClockLoaded(){return legacyClockLoaded && processingTicks==ticks;}
    public long visibleTicks(){return processingDepth==0?ticks:processingTicks;}
    public <T> T processingClock(java.util.function.Supplier<T> action){
        processingDepth++;
        try{return action.get();}finally{processingDepth--;}
    }
    public void advanceProcessing(int count){
        if(count<0)throw new IllegalArgumentException("Negative processing interval");
        processingTicks=Math.addExact(processingTicks,count);if(count>0)legacyClockLoaded=false;changed.run();
    }
    public java.math.BigInteger lanes(){return lanes;}
    public int nativeLanes(){return nativeLanes;}
    public int parallelWork(long position){return parallelWork.getOrDefault(position,1);}
    public void parallelWork(long position,int count){
        if(count<0)throw new IllegalArgumentException("NATIVE_WORLD_INVALID_PARALLEL_WORK");
        if(count==0)parallelWork.remove(position);else parallelWork.put(position,count);
        changed.run();
    }
    public int processWork(long position,int process){
        if(process==0)return parallelWork(position);
        return processWork.getOrDefault(position,java.util.Map.of()).getOrDefault(process,1);
    }
    public int recordedProcessWork(long position,int process){
        return process==0?parallelWork.getOrDefault(position,0):processWork.getOrDefault(position,java.util.Map.of()).getOrDefault(process,0);
    }
    public void processWork(long position,int process,int count){
        if(process<0 || count<0)throw new IllegalArgumentException("NATIVE_WORLD_INVALID_PARALLEL_WORK");
        if(process==0){parallelWork(position,count);return;}
        if(count==0){var work=processWork.get(position);if(work!=null){work.remove(process);if(work.isEmpty())processWork.remove(position);}}
        else processWork.computeIfAbsent(position,ignored->new java.util.HashMap<>()).put(process,count);
        changed.run();
    }
    public void lanes(java.math.BigInteger value){
        if(value.signum()<=0)throw new IllegalArgumentException("NATIVE_WORLD_INVALID_PARALLELISM");
        lanes=value;nativeLanes=value.min(java.math.BigInteger.valueOf(Integer.MAX_VALUE)).intValueExact();changed.run();
    }
    public java.math.BigInteger tickDebt(){return tickDebt;}
    public void addTickDebt(java.math.BigInteger count){if(count.signum()<0)throw new IllegalArgumentException("Negative world work");tickDebt=tickDebt.add(count);changed.run();}
    public void consumeTickDebt(){if(tickDebt.signum()==0)throw new IllegalStateException("World work underflow");tickDebt=tickDebt.subtract(java.math.BigInteger.ONE);changed.run();}
    public void consumeTickDebt(int count){
        if(count<0 || tickDebt.compareTo(java.math.BigInteger.valueOf(count))<0)throw new IllegalArgumentException("World work underflow");
        tickDebt=tickDebt.subtract(java.math.BigInteger.valueOf(count));changed.run();
    }
    public void setTime(long ticks, long dayTime) {
        if(ticks < 0) throw new IllegalArgumentException("NATIVE_WORLD_INVALID_CLOCK");
        this.ticks=ticks;this.processingTicks=Math.max(processingTicks,ticks);this.dayTime=dayTime;legacyClockLoaded=false;changed.run();
    }
    public net.minecraft.world.level.GameRules rules() { return rules; }
    public void initialRules(net.minecraft.world.level.GameRules source) {
        if (owner != null) return;
        rules = source.copy(); changed.run();
    }
    public long dayTime() { return dayTime; }
    public UUID owner() { return owner; }
    public int minX() { return minX; }
    public int minZ() { return minZ; }
    public int maxX() { return maxX; }
    public int maxZ() { return maxZ; }

    public CompoundTag save(CompoundTag tag) {
        tag.putInt("schema", 1);
        tag.put("rules", rules.createTag());
        tag.putLong("ticks", ticks); tag.putLong("dayTime", dayTime);
        tag.putLong("processingTicks",processingTicks);
        tag.putByteArray("tickDebt",tickDebt.toByteArray());
        tag.putByteArray("lanes",lanes.toByteArray());
        var work=new CompoundTag();parallelWork.forEach((p,n)->work.putInt(Long.toString(p),n));tag.put("parallelWork",work);
        var processes=new CompoundTag();processWork.forEach((p,counts)->{
            var saved=new CompoundTag();counts.forEach((i,n)->saved.putInt(Integer.toString(i),n));processes.put(Long.toString(p),saved);
        });tag.put("processWork",processes);
        if (owner != null) {
            tag.putUUID("factory", owner);
            tag.putInt("minX", minX); tag.putInt("minZ", minZ);
            tag.putInt("maxX", maxX); tag.putInt("maxZ", maxZ);
        }
        return tag;
    }

    public void load(CompoundTag tag) {
        if (tag.getInt("schema") != 1 || tag.getLong("ticks") < 0)
            throw new IllegalArgumentException("NATIVE_WORLD_INVALID_CLOCK");
        rules = new net.minecraft.world.level.GameRules(new com.mojang.serialization.Dynamic<>(
                net.minecraft.nbt.NbtOps.INSTANCE, tag.getCompound("rules")));
        if (tag.hasUUID("factory"))
            claim(tag.getUUID("factory"), tag.getInt("minX"), tag.getInt("minZ"),
                    tag.getInt("maxX"), tag.getInt("maxZ"), tag.getLong("dayTime"));
        ticks = tag.getLong("ticks"); dayTime = tag.getLong("dayTime");
        processingTicks=tag.contains("processingTicks")?tag.getLong("processingTicks"):ticks;
        legacyClockLoaded=!tag.contains("processingTicks");
        if(processingTicks<ticks)throw new IllegalArgumentException("NATIVE_WORLD_INVALID_PROCESSING_CLOCK");
        tickDebt=tag.contains("tickDebt")?new java.math.BigInteger(tag.getByteArray("tickDebt")):java.math.BigInteger.ZERO;
        if(tickDebt.signum()<0)throw new IllegalArgumentException("NATIVE_WORLD_NEGATIVE_WORK");
        lanes(tag.contains("lanes")?new java.math.BigInteger(tag.getByteArray("lanes")):java.math.BigInteger.ONE);
        parallelWork.clear();var work=tag.getCompound("parallelWork");
        for(var p:work.getAllKeys()) {
            int n=work.getInt(p);if(n<=0)throw new IllegalArgumentException("NATIVE_WORLD_INVALID_PARALLEL_WORK");
            parallelWork.put(Long.parseLong(p),n);
        }
        processWork.clear();var processes=tag.getCompound("processWork");
        for(var p:processes.getAllKeys())for(var i:processes.getCompound(p).getAllKeys()) {
            int index=Integer.parseInt(i),count=processes.getCompound(p).getInt(i);
            if(index<=0 || count<=0)throw new IllegalArgumentException("NATIVE_WORLD_INVALID_PARALLEL_WORK");
            processWork.computeIfAbsent(Long.parseLong(p),ignored->new java.util.HashMap<>()).put(index,count);
        }
    }
}
