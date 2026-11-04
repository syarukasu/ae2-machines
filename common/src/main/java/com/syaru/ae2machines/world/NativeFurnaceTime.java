package com.syaru.ae2machines.world;

import com.syaru.ae2machines.mixin.FurnaceClockAccess;
import com.syaru.ae2machines.platform.FurnaceRecipeAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.*;

public final class NativeFurnaceTime {
    private NativeFurnaceTime(){}
    static boolean supports(BlockEntity be){return be.getClass()==FurnaceBlockEntity.class || be.getClass()==BlastFurnaceBlockEntity.class || be.getClass()==SmokerBlockEntity.class;}
    record Frame(BlockEntity owner,long tick,int lit,int progress,ItemStack input,ItemStack output,ItemStack fuel){}
    public static void before(AbstractFurnaceBlockEntity m){
        if(m.getLevel() instanceof net.minecraft.server.level.ServerLevel level && NativeCellWorlds.owns(level)){
            var clock=(FurnaceClockAccess)m;
            NativeWorldRuntime.get(level).observeFurnace(new Frame(m,level.getGameTime(),clock.machines$lit(),clock.machines$progress(),m.getItem(0).copy(),m.getItem(2).copy(),m.getItem(1).copy()));
        }
    }
    static NativeQuietStep plan(BlockEntity be,int offered,Frame before){
        if(!supports(be))return null;
        var m=(AbstractFurnaceBlockEntity)be;var clock=(FurnaceClockAccess)m;
        int lit=clock.machines$lit(),progress=clock.machines$progress();
        if(lit==0 && progress==0 && m.getItem(0).isEmpty())return new NativeQuietStep(offered,n->{},()->{});
        // Stay strictly before fuel exhaustion or recipe completion. Native tick handles both.
        if(lit<=1 || progress<=0 || before==null || before.owner()!=be || before.tick()!=be.getLevel().getGameTime()
                || before.lit()!=lit+1 || before.progress()+1!=progress || before.progress()<=0
                || !ItemStack.matches(before.input(),m.getItem(0)) || !ItemStack.matches(before.output(),m.getItem(2))
                || !ItemStack.matches(before.fuel(),m.getItem(1)) || !FurnaceRecipeAccess.constant(m))return new NativeQuietStep(0,n->{},()->{});
        int n=Math.min(offered,Math.min(lit-1,clock.machines$duration()-progress-1));
        return new NativeQuietStep(Math.max(0,n),count->{clock.machines$lit(lit-count);clock.machines$progress(progress+count);m.getLevel().getChunkAt(m.getBlockPos()).setUnsaved(true);},
            ()->{clock.machines$lit(lit);clock.machines$progress(progress);m.getLevel().getChunkAt(m.getBlockPos()).setUnsaved(true);});
    }
}
