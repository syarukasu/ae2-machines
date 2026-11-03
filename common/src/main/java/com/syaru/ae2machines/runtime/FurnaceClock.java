package com.syaru.ae2machines.runtime;

import com.syaru.ae2machines.mixin.FurnaceClockAccess;
import com.syaru.ae2machines.platform.FurnaceRecipeAccess;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.item.ItemStack;
import java.math.BigInteger;

/** Jump only inside vanilla's constant counter intervals, never across a native transition. */
public final class FurnaceClock {
    private FurnaceClock(){}
    public static long advance(AbstractFurnaceBlockEntity furnace,BigInteger ticks) {
        return advance(furnace,ticks,Long.MAX_VALUE).calls();
    }
    public record Slice(long calls,BigInteger remaining){}
    public static Slice advance(AbstractFurnaceBlockEntity furnace,BigInteger ticks,long callBudget) {
        if(ticks.signum()<=0)throw new IllegalArgumentException("Nonpositive furnace clock");
        if(callBudget<=0)throw new IllegalArgumentException("Nonpositive native call budget");
        if(ticks.equals(BigInteger.ONE)){DetachedFurnaceTick.tick(furnace);return new Slice(1,BigInteger.ZERO);}
        var clock=(FurnaceClockAccess)furnace;long calls=0;
        while(ticks.signum()>0 && calls<callBudget) {
            int beforeProgress=clock.machines$progress(),beforeLit=clock.machines$lit();
            var input=furnace.getItem(0).copy();var output=furnace.getItem(2).copy();
            DetachedFurnaceTick.tick(furnace);calls++;
            ticks=ticks.subtract(BigInteger.ONE);
            int lit=clock.machines$lit(),progress=clock.machines$progress();
            // With no burning fuel and no input/fuel, vanilla does not enter recipe execution.
            if(lit==0 && progress==0 && (furnace.getItem(0).isEmpty() || furnace.getItem(1).isEmpty()))return new Slice(calls,BigInteger.ZERO);
            if(ticks.signum()==0 || !FurnaceRecipeAccess.constant(furnace))continue;
            int jump=0;
            if(lit>1 && beforeLit==lit+1 && beforeProgress>0 && progress==beforeProgress+1)
                jump=Math.min(lit-1,clock.machines$duration()-progress-1);
            else if(lit>1 && beforeLit==lit+1 && progress==0 && beforeProgress==0
                    && ItemStack.matches(input,furnace.getItem(0)) && ItemStack.matches(output,furnace.getItem(2)))
                jump=lit-1;
            if(jump>0) {
                int n=ticks.min(BigInteger.valueOf(jump)).intValueExact();
                clock.machines$lit(lit-n);
                if(progress>0)clock.machines$progress(progress+n);
                ticks=ticks.subtract(BigInteger.valueOf(n));
            }
        }
        return new Slice(calls,ticks);
    }
}
