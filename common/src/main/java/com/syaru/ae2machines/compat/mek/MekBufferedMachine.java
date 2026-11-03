package com.syaru.ae2machines.compat.mek;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import com.syaru.ae2machines.engine.Recipe;
import com.syaru.ae2machines.runtime.BufferedMachine;
import mekanism.api.*;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.inventory.slot.*;
import mekanism.common.tile.prefab.TileEntityConfigurableMachine;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.math.BigInteger;
import java.util.*;
import static com.syaru.ae2machines.world.NativeFactoryCompiler.require;

/** Side configuration and capacity remain native; only native machine ticking is replaced. */
public final class MekBufferedMachine implements BufferedMachine {
    private final TileEntityConfigurableMachine machine;
    private final List<IInventorySlot> inputs,outputs;
    private final Runnable changed;
    private WorkAccess work;
    @Override public boolean bindWork(WorkAccess access){work=Objects.requireNonNull(access);return true;}
    public MekBufferedMachine(BlockEntity be,Runnable changed) {
        machine=(TileEntityConfigurableMachine)be;this.changed=changed;
        inputs=machine.getInventorySlots(null).stream().filter(s->s instanceof InputInventorySlot).toList();
        outputs=machine.getInventorySlots(null).stream().filter(s->s instanceof OutputInventorySlot).toList();
        require(!inputs.isEmpty() && inputs.size()==outputs.size(),"NATIVE_ITEM_PROCESS_LAYOUT_CHANGED");
    }
    @Override public MEStorage storage(Direction side) {
        return new MEStorage() {
            @Override public long insert(AEKey key,long amount,Actionable mode,IActionSource source) {
                if(!(key instanceof AEItemKey item) || amount<=0)return 0;
                long left=Math.min(Integer.MAX_VALUE,amount),offered=left;
                boolean workTried=false;
                for(var slot:machine.getInventorySlots(side)) {
                    // Batteries are not materials: energy slots cannot bypass metered power accounting.
                    if(!inputs.contains(slot))continue;
                    if(work!=null && !workTried) {
                        workTried=true;
                        var recipe=com.syaru.ae2machines.platform.MekanismAdapter.inspect(machine,item.toStack((int)left));
                        if(recipe.isPresent() && slot.isItemValid(item.toStack(1))) {
                            long accepted=work.insert(recipe.get().recipe(),key,left,mode.isSimulate());
                            // WorkAccess durably records ownership; no physical slot changed here.
                            if(accepted>0)return accepted;
                        }
                    }
                    left=slot.insertItem(item.toStack((int)left),mode.isSimulate()?Action.SIMULATE:Action.EXECUTE,AutomationType.EXTERNAL).getCount();
                    if(left==0)break;
                }
                long accepted=offered-left;if(accepted>0 && !mode.isSimulate())changed.run();return accepted;
            }
            @Override public long extract(AEKey key,long amount,Actionable mode,IActionSource source) {
                if(!(key instanceof AEItemKey item) || amount<=0)return 0;
                long left=Math.min(Integer.MAX_VALUE,amount),offered=left;
                boolean workSaved=false;
                boolean exposeWork=false;
                for(var slot:machine.getInventorySlots(side)) {
                    if(!outputs.contains(slot))continue;
                    exposeWork=true;
                    if(item.matches(slot.getStack()))left-=slot.extractItem((int)left,mode.isSimulate()?Action.SIMULATE:Action.EXECUTE,AutomationType.EXTERNAL).getCount();
                }
                // Persist the ledger only after every physical slot has been updated.
                if(exposeWork && work!=null && left>0) {
                    long taken=work.extract(key,left,mode.isSimulate());left-=taken;
                    workSaved=taken>0 && !mode.isSimulate();
                }
                long extracted=offered-left;if(extracted>0 && !mode.isSimulate() && !workSaved)changed.run();return extracted;
            }
            @Override public void getAvailableStacks(KeyCounter counter) {
                boolean exposeWork=false;
                for(var slot:machine.getInventorySlots(side))if(inputs.contains(slot) || outputs.contains(slot)) {
                    var stack=slot.getStack();if(!stack.isEmpty())counter.add(AEItemKey.of(stack),stack.getCount());
                    exposeWork|=outputs.contains(slot);
                }
                if(exposeWork && work!=null)work.outputs().forEach((key,n)->counter.add(key,n.min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact()));
            }
            @Override public Component getDescription(){return machine.getBlockState().getBlock().getName();}
        };
    }
    @Override public boolean ejecting(){return machine.getConfig().getConfig(mekanism.common.lib.transmitter.TransmissionType.ITEM).isEjecting();}
    @Override public Optional<Recipe<AEKey>> discoverRecipe() {
        for(var input:inputs)if(!input.isEmpty()) {
            var profile=com.syaru.ae2machines.platform.MekanismAdapter.inspect(machine,input.getStack());
            if(profile.isPresent() && availableExecutions(profile.get().recipe()).signum()>0)return Optional.of(profile.get().recipe());
        }
        return Optional.empty();
    }
    @Override public Integer transportColor(){var color=machine.getEjector().getOutputColor();return color==null?-1:color.ordinal();}
    @Override public BigInteger availableExecutions(Recipe<AEKey> recipe) {
        if(recipe.inputs().size()!=1 || recipe.outputs().size()!=1)return BigInteger.ZERO;
        var in=recipe.inputs().entrySet().iterator().next();var out=recipe.outputs().entrySet().iterator().next();
        if(!(in.getKey() instanceof AEItemKey key) || !(out.getKey() instanceof AEItemKey result)
                || in.getValue().bitLength()>31 || out.getValue().bitLength()>31)return BigInteger.ZERO;
        int inCount=in.getValue().intValueExact(),outCount=out.getValue().intValueExact();
        long total=0;
        for(int i=0;i<inputs.size();i++) {
        var input=inputs.get(i);var output=outputs.get(i);
        if(!key.matches(input.getStack()))continue;
        int count=Math.min(input.extractItem(Integer.MAX_VALUE,Action.SIMULATE,AutomationType.INTERNAL).getCount()/inCount,
                Integer.MAX_VALUE/outCount);
        if(count==0)continue;
        int offered=count*outCount;
        int fits=offered-output.insertItem(result.toStack(offered),Action.SIMULATE,AutomationType.INTERNAL).getCount();
        total+=fits/outCount;
        }
        return BigInteger.valueOf(total);
    }
    @Override public void takeInputs(Recipe<AEKey> recipe,BigInteger executions) {
        require(executions.signum()>0 && availableExecutions(recipe).compareTo(executions)>=0,"NATIVE_BUFFER_RECIPE_CHANGED");
        var in=recipe.inputs().entrySet().iterator().next();var out=recipe.outputs().entrySet().iterator().next();
        var key=(AEItemKey)in.getKey();var result=(AEItemKey)out.getKey();var left=executions;
        int inputCount=in.getValue().intValueExact(),outputCount=out.getValue().intValueExact();
        for(int i=0;i<inputs.size() && left.signum()>0;i++) {
            var input=inputs.get(i);if(!key.matches(input.getStack()))continue;
            int count=Math.min(input.extractItem(Integer.MAX_VALUE,Action.SIMULATE,AutomationType.INTERNAL).getCount()/inputCount,
                    Integer.MAX_VALUE/outputCount);
            int offered=count*outputCount;
            int fits=(offered-outputs.get(i).insertItem(result.toStack(offered),Action.SIMULATE,AutomationType.INTERNAL).getCount())/outputCount;
            int selected=left.min(BigInteger.valueOf(fits)).intValueExact();if(selected==0)continue;
            int n=Math.multiplyExact(selected,inputCount);
            require(input.extractItem(n,Action.EXECUTE,AutomationType.INTERNAL).getCount()==n,"NATIVE_BUFFER_INPUT_MISMATCH");
            left=left.subtract(BigInteger.valueOf(selected));
        }
        require(left.signum()==0,"NATIVE_BUFFER_INPUT_MISMATCH");
    }
    @Override public boolean putOutputs(Map<AEKey,BigInteger> produced) {
        require(produced.size()==1,"NATIVE_BUFFER_OUTPUT_COUNT");
        var entry=produced.entrySet().iterator().next();
        require(entry.getKey() instanceof AEItemKey,"NATIVE_BUFFER_OUTPUT_TYPE");
        var key=(AEItemKey)entry.getKey();var left=entry.getValue();
        var accepted=new int[outputs.size()];
        for(int i=0;i<outputs.size() && left.signum()>0;i++) {
            int n=left.min(BigInteger.valueOf(Integer.MAX_VALUE)).intValueExact();
            accepted[i]=n-outputs.get(i).insertItem(key.toStack(n),Action.SIMULATE,AutomationType.INTERNAL).getCount();
            left=left.subtract(BigInteger.valueOf(accepted[i]));
        }
        if(left.signum()>0)return false;
        for(int i=0;i<outputs.size();i++)if(accepted[i]>0)
            require(outputs.get(i).insertItem(key.toStack(accepted[i]),Action.EXECUTE,AutomationType.INTERNAL).isEmpty(),"NATIVE_BUFFER_OUTPUT_MISMATCH");
        return true;
    }
}
