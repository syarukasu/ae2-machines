package com.syaru.ae2machines.runtime;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import com.syaru.ae2machines.engine.Recipe;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.*;
import java.math.BigInteger;
import java.util.Map;

/** Fuel, recipe choice, progress, return containers and XP remain owned by vanilla. */
public final class VanillaFurnaces {
    private VanillaFurnaces(){}
    public static boolean supports(BlockEntity be) {
        return be!=null && (be.getClass()==FurnaceBlockEntity.class || be.getClass()==BlastFurnaceBlockEntity.class || be.getClass()==SmokerBlockEntity.class);
    }
    public static MachineProfile profile() {
        return new MachineProfile(null,BigInteger.ZERO,BigInteger.ZERO,BigInteger.ZERO,BigInteger.ONE,BigInteger.ONE,BigInteger.ONE,0,0,"FE");
    }
    public static BufferedMachine buffer(BlockEntity be,Runnable changed) {
        var furnace=(AbstractFurnaceBlockEntity)be;
        return new BufferedMachine() {
            @Override public void tickNative(){DetachedFurnaceTick.tick(furnace);changed.run();}
            @Override public MEStorage storage(Direction side) {
                return new MEStorage() {
                    @Override public long insert(AEKey key,long amount,Actionable mode,IActionSource source) {
                        if(!(key instanceof AEItemKey item) || amount<=0 || side==null)return 0;
                        long remaining=amount;
                        for(int slot:furnace.getSlotsForFace(side)) {
                            var offered=item.toStack();var current=furnace.getItem(slot);
                            if(!furnace.canPlaceItemThroughFace(slot,offered,side) || !current.isEmpty() && !item.matches(current))continue;
                            int limit=Math.min(furnace.getMaxStackSize(),offered.getMaxStackSize());
                            int accepted=(int)Math.min(remaining,Math.max(0,limit-current.getCount()));
                            if(accepted>0 && !mode.isSimulate())DetachedFurnaceTick.mutate(furnace,()->furnace.setItem(slot,item.toStack(current.getCount()+accepted)));
                            remaining-=accepted;if(remaining==0)break;
                        }
                        if(remaining!=amount && !mode.isSimulate())changed.run();return amount-remaining;
                    }
                    @Override public long extract(AEKey key,long amount,Actionable mode,IActionSource source) {
                        if(!(key instanceof AEItemKey item) || amount<=0 || side==null)return 0;
                        long remaining=amount;
                        for(int slot:furnace.getSlotsForFace(side)) {
                            var current=furnace.getItem(slot);
                            if(current.isEmpty() || !item.matches(current) || !furnace.canTakeItemThroughFace(slot,current,side))continue;
                            int taken=(int)Math.min(remaining,current.getCount());
                            if(!mode.isSimulate())DetachedFurnaceTick.mutate(furnace,()->furnace.removeItem(slot,taken));
                            remaining-=taken;if(remaining==0)break;
                        }
                        if(remaining!=amount && !mode.isSimulate())changed.run();return amount-remaining;
                    }
                    @Override public void getAvailableStacks(KeyCounter out) {
                        if(side==null)return;
                        for(int slot:furnace.getSlotsForFace(side)) {
                            var stack=furnace.getItem(slot);
                            if(!stack.isEmpty() && furnace.canTakeItemThroughFace(slot,stack,side))out.add(AEItemKey.of(stack),stack.getCount());
                        }
                    }
                    @Override public Component getDescription(){return furnace.getBlockState().getBlock().getName();}
                };
            }
            @Override public BigInteger availableExecutions(Recipe<AEKey> recipe){return BigInteger.ZERO;}
            @Override public void takeInputs(Recipe<AEKey> recipe,BigInteger count){throw new IllegalStateException("Vanilla owns furnace inputs");}
            @Override public boolean putOutputs(Map<AEKey,BigInteger> outputs){throw new IllegalStateException("Vanilla owns furnace outputs");}
            @Override public boolean ejecting(){return false;}
        };
    }
}
