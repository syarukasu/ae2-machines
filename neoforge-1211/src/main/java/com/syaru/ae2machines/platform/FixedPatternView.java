package com.syaru.ae2machines.platform;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.*;
public record FixedPatternView(AEItemKey definition,IInput[] inputs,GenericStack[] outputs) implements IPatternDetails {
    public AEItemKey getDefinition(){return definition;}
    public IInput[] getInputs(){return inputs;}
    public java.util.List<GenericStack> getOutputs(){return java.util.List.of(outputs);}
}
