package com.syaru.ae2machines.world;

import com.syaru.ae2machines.mixin.MekHeatAccess;
import java.util.Arrays;
import mekanism.common.lib.multiblock.MultiblockData;
import net.minecraft.nbt.*;

/** Only caches enrolled by a real cell capture retain the native deferred heat queue. */
public final class MekHeatState {
    private static final String KEY="ae2machines:pending_heat";
    private double[] pending;
    private CompoundTag fission=new CompoundTag();

    public interface Owner { MekHeatState ae2machines$heatState(); }
    public boolean enrolled(){return pending!=null;}

    public void capture(MultiblockData data) {
        var heat=data.getHeatCapacitors(null);
        pending=new double[heat.size()];
        for(int i=0;i<heat.size();i++)if(heat.get(i) instanceof MekHeatAccess access)
            pending[i]=access.ae2machines$pendingHeat();
        if(data instanceof com.syaru.ae2machines.compat.mek.FissionParallelState.Access access)
            fission=access.machines$fissionGroups().save(access);
    }
    public void sync(MultiblockData data) { if(pending!=null)capture(data); }
    public void apply(MultiblockData data) {
        if(pending==null)return;
        var heat=data.getHeatCapacitors(null);
        if(pending.length!=heat.size())throw new IllegalStateException("MEK_PENDING_HEAT_LAYOUT_CHANGED");
        for(int i=0;i<pending.length;i++) {
            if(heat.get(i) instanceof MekHeatAccess access)access.ae2machines$pendingHeat(pending[i]);
            else if(pending[i]!=0)throw new IllegalStateException("MEK_PENDING_HEAT_TARGET_UNSUPPORTED");
        }
        if(data instanceof com.syaru.ae2machines.compat.mek.FissionParallelState.Access access)
            access.machines$fissionGroups().load(fission);
    }
    public void save(CompoundTag tag) {
        if(pending==null)return;
        var list=new ListTag();for(double value:pending)list.add(DoubleTag.valueOf(value));tag.put(KEY,list);
        if(!fission.isEmpty())tag.put("ae2machines:fission_groups",fission.copy());
    }
    public void load(CompoundTag tag) {
        pending=null;fission=tag.getCompound("ae2machines:fission_groups").copy();if(!tag.contains(KEY,Tag.TAG_LIST))return;
        var list=tag.getList(KEY,Tag.TAG_DOUBLE);pending=new double[list.size()];
        for(int i=0;i<pending.length;i++) {
            pending[i]=list.getDouble(i);
            if(!Double.isFinite(pending[i]))throw new IllegalStateException("MEK_PENDING_HEAT_INVALID");
        }
    }
    public void merge(MekHeatState other) {
        if(!other.fission.isEmpty()){
            if(!fission.isEmpty() && !fission.equals(other.fission))throw new IllegalStateException("FISSION_COHORT_CACHE_MERGE_CONFLICT");
            fission=other.fission.copy();
        }
        if(other.pending==null)return;
        pending=pending==null?new double[other.pending.length]:Arrays.copyOf(pending,Math.max(pending.length,other.pending.length));
        for(int i=0;i<other.pending.length;i++)pending[i]+=other.pending[i];
    }
}
