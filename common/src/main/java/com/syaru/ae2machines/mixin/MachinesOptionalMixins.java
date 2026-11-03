package com.syaru.ae2machines.mixin;
import java.util.*;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;
public final class MachinesOptionalMixins implements IMixinConfigPlugin {
    public void onLoad(String p){}
    public String getRefMapperConfig(){return null;}
    public boolean shouldApplyMixin(String target,String mixin){return !mixin.endsWith("AcoNativeProviderMixin") || com.syaru.ae2machines.compat.OptionalAco.exactProcessing();}
    public void acceptTargets(Set<String> own,Set<String> other){}
    public List<String> getMixins(){return null;}
    public void preApply(String t,ClassNode n,String m,IMixinInfo i){}
    public void postApply(String t,ClassNode n,String m,IMixinInfo i){}
}
