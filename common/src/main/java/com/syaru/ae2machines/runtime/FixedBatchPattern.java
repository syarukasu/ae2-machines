package com.syaru.ae2machines.runtime;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.IPatternDetails.IInput;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.*;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.crafting.inv.ICraftingInventory;
import net.minecraft.world.level.Level;
import java.util.*;
import java.util.function.ToLongFunction;

/** Ephemeral extraction view only; the registered pattern, recipe and saved CPU task stay unchanged. */
public record FixedBatchPattern(IPatternDetails original,int count,IInput[] inputs,GenericStack[] outputs) {
    public static FixedBatchPattern offer(IPatternDetails pattern,int limit,ICraftingInventory stock,
                                         IEnergyService energy,ToLongFunction<AEKey> waiting) {
        if(limit<2 || pattern.getClass()!=AEProcessingPattern.class || PowerMultiplier.CONFIG.multiplier!=1.0)return null;
        try {
            var amounts=new LinkedHashMap<AEKey,Long>();
            for(var input:pattern.getInputs()) {
                var choices=input.getPossibleInputs();
                if(choices.length!=1 || !(choices[0].what() instanceof AEItemKey) || input.getMultiplier()<=0
                        || choices[0].amount()<=0 || input.getRemainingKey(choices[0].what())!=null)return null;
                long amount=Math.multiplyExact(choices[0].amount(),input.getMultiplier());
                amounts.merge(choices[0].what(),amount,Math::addExact);
            }
            if(amounts.isEmpty())return null;
            long unitPower=0;
            for(var entry:amounts.entrySet()) {
                if(entry.getKey().getAmountPerOperation()!=1)return null;
                limit=(int)Math.min(limit,stock.extract(entry.getKey(),Long.MAX_VALUE,Actionable.SIMULATE)/entry.getValue());
                unitPower=Math.addExact(unitPower,entry.getValue());
            }
            var totals=new LinkedHashMap<AEKey,Long>();
            for(var output:pattern.getOutputs()) {
                if(output.amount()<=0)return null;
                totals.merge(output.what(),output.amount(),Math::addExact);
            }
            for(var entry:totals.entrySet()) {
                long already=waiting.applyAsLong(entry.getKey());if(already<0)return null;
                limit=(int)Math.min(limit,(Long.MAX_VALUE-already)/entry.getValue());
            }
            if(unitPower<=0)return null;
            limit=(int)Math.min(limit,(1L<<53)/unitPower);
            if(limit<2)return null;
            double available=energy.extractAEPower((double)unitPower*limit,Actionable.SIMULATE,PowerMultiplier.CONFIG);
            if(!Double.isFinite(available) || available<0)return null;
            limit=(int)Math.min(limit,Math.floor((available+0.01)/unitPower));
            return limit<2?null:scale(pattern,limit);
        }catch(ArithmeticException overflow){return null;}
    }
    private static FixedBatchPattern scale(IPatternDetails pattern,int count) {
        var original=pattern.getInputs();var inputs=new IInput[original.length];
        for(int i=0;i<inputs.length;i++) {
            var input=original[i];long multiplier=Math.multiplyExact(input.getMultiplier(),count);
            inputs[i]=new IInput() {
                public GenericStack[] getPossibleInputs(){return input.getPossibleInputs();}
                public long getMultiplier(){return multiplier;}
                public boolean isValid(AEKey key,Level level){return input.isValid(key,level);}
                public AEKey getRemainingKey(AEKey key){return input.getRemainingKey(key);}
            };
        }
        var outputs=new ArrayList<GenericStack>();
        for(var s:pattern.getOutputs())outputs.add(new GenericStack(s.what(),Math.multiplyExact(s.amount(),count)));
        return new FixedBatchPattern(pattern,count,inputs,outputs.toArray(GenericStack[]::new));
    }
    public IPatternDetails view(){return new com.syaru.ae2machines.platform.FixedPatternView(original.getDefinition(),inputs,outputs);}
}
