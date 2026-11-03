package com.syaru.ae2machines.platform;

import appeng.api.stacks.*;
import java.math.BigInteger;
import java.util.*;
import mekanism.api.*;
import mekanism.api.chemical.IChemicalTank;
import mekanism.common.inventory.slot.*;
import mekanism.common.tile.prefab.TileEntityConfigurableMachine;
import me.ramidzkh.mekae2.ae2.MekanismKey;

/** Owns native scratch slots/tanks; debits only what the native handlers actually accept. */
@SuppressWarnings({"rawtypes","unchecked"})
public final class MekRequestMaterials {
    public static GenericStack output(Object value){
        if(value instanceof net.minecraft.world.item.ItemStack stack)return stack.isEmpty()?null:new GenericStack(AEItemKey.of(stack),stack.getCount());
        if(value instanceof net.minecraftforge.fluids.FluidStack stack)return stack.isEmpty()?null:new GenericStack(AEFluidKey.of(stack),stack.getAmount());
        if(value instanceof mekanism.api.chemical.ChemicalStack stack)return stack.isEmpty()?null:new GenericStack(MekanismKey.of(stack),stack.getAmount());
        return null;
    }
    private final TileEntityConfigurableMachine machine;
    private final List<IChemicalTank> chemicals=new ArrayList<>();
    private final List<Object> containers=new ArrayList<>();
    private final Set<Object> inputs=Collections.newSetFromMap(new IdentityHashMap<>());
    public MekRequestMaterials(TileEntityConfigurableMachine machine,int[] mask,boolean fresh){
        this.machine=machine;chemicals.addAll(machine.getGasTanks(null));chemicals.addAll(machine.getInfusionTanks(null));chemicals.addAll(machine.getPigmentTanks(null));chemicals.addAll(machine.getSlurryTanks(null));
        containers.addAll(machine.getInventorySlots(null));containers.addAll(machine.getFluidTanks(null));containers.addAll(chemicals);
        if(fresh){for(var slot:machine.getInventorySlots(null))slot.setEmpty();for(var tank:machine.getFluidTanks(null))tank.setEmpty();for(var tank:chemicals)tank.setEmpty();}
        else for(int i:mask){if(i<0 || i>=containers.size())throw new IllegalStateException("Native input container layout changed");inputs.add(containers.get(i));}
    }
    public int[] mask(){return java.util.stream.IntStream.range(0,containers.size()).filter(i->inputs.contains(containers.get(i))).toArray();}
    private static void debit(Map<AEKey,BigInteger> pool,AEKey key,long amount){if(amount<=0)return;var left=pool.get(key).subtract(BigInteger.valueOf(amount));if(left.signum()<0)throw new IllegalStateException("Native input overdraft");if(left.signum()==0)pool.remove(key);else pool.put(key,left);}
    public void offer(Map<AEKey,BigInteger> pool,Map<AEKey,BigInteger> perExecution,int window){
        for(var key:List.copyOf(pool.keySet())){
            var limit=perExecution.getOrDefault(key,BigInteger.ZERO).multiply(BigInteger.valueOf(window));
            if(limit.signum()==0)continue;
            if(key instanceof AEItemKey item)for(var slot:machine.getInventorySlots(null))if(slot instanceof InputInventorySlot){
                int n=pool.getOrDefault(key,BigInteger.ZERO).min(limit).min(BigInteger.valueOf(Integer.MAX_VALUE)).intValueExact();if(n==0)break;
                var stack=item.toStack(n);if(!slot.isItemValid(stack))continue;
                int accepted=n-slot.insertItem(stack,Action.EXECUTE,AutomationType.INTERNAL).getCount();
                if(accepted>0){inputs.add(slot);debit(pool,key,accepted);}
            }
            if(key instanceof AEFluidKey fluid)for(var tank:machine.getFluidTanks(null)){
                int n=pool.getOrDefault(key,BigInteger.ZERO).min(limit).min(BigInteger.valueOf(Integer.MAX_VALUE)).intValueExact();if(n==0)break;
                var stack=new net.minecraftforge.fluids.FluidStack(fluid.getFluid(),n,fluid.getTag());int accepted=n-tank.insert(stack,Action.EXECUTE,AutomationType.EXTERNAL).getAmount();
                if(accepted>0){inputs.add(tank);debit(pool,key,accepted);}
            }
            if(key instanceof MekanismKey chemical)for(var tank:chemicals){
                long n=pool.getOrDefault(key,BigInteger.ZERO).min(limit).min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact();if(n==0)break;
                var stack=chemical.withAmount(n);if(stack.getClass()!=tank.getEmptyStack().getClass())continue;
                long accepted=n-tank.insert(stack,Action.EXECUTE,AutomationType.EXTERNAL).getAmount();
                if(accepted>0){inputs.add(tank);debit(pool,key,accepted);}
            }
        }
    }
    public Map<AEKey,BigInteger> inputAmounts(){return amounts(true,false);}
    public Map<AEKey,BigInteger> drainOutputs(){return amounts(false,true);}
    public Map<AEKey,BigInteger> returnInputs(){return amounts(true,true);}
    private Map<AEKey,BigInteger> amounts(boolean input,boolean clear){
        var result=new LinkedHashMap<AEKey,BigInteger>();
        for(var slot:machine.getInventorySlots(null))if((input?inputs.contains(slot):slot instanceof OutputInventorySlot) && !slot.isEmpty()){
            add(result,AEItemKey.of(slot.getStack()),slot.getCount());if(clear)slot.setEmpty();
        }
        for(var tank:machine.getFluidTanks(null))if(inputs.contains(tank)==input && !tank.isEmpty()){
            add(result,AEFluidKey.of(tank.getFluid()),tank.getFluidAmount());if(clear)tank.setEmpty();
        }
        for(var tank:chemicals)if(inputs.contains(tank)==input && !tank.isEmpty()){
            add(result,MekanismKey.of(tank.getStack()),tank.getStored());if(clear)tank.setEmpty();
        }
        return Map.copyOf(result);
    }
    private static void add(Map<AEKey,BigInteger> map,AEKey key,long amount){map.merge(Objects.requireNonNull(key),BigInteger.valueOf(amount),BigInteger::add);}
}

