package com.syaru.ae2machines.runtime;

import appeng.blockentity.crafting.MolecularAssemblerBlockEntity;
import net.minecraft.nbt.CompoundTag;

/** Preserve paid progress only for assemblers transferred through a factory cell. */
public final class AssemblerPersistence {
    private boolean tracked;
    private CompoundTag pendingNode;
    public void loaded(MolecularAssemblerBlockEntity be,CompoundTag tag) {
        tracked=tag.contains("ae2machines:assemblerProgress");
        pendingNode=tracked && tag.contains("proxy")?tag.getCompound("proxy").copy():null;
        if(tracked)NativeAssemblers.loaded(be,tag);
    }
    public void save(MolecularAssemblerBlockEntity be,CompoundTag tag) {
        if(!tracked)return;
        NativeAssemblers.save(be,tag);
        // AE2 defers node creation until onReady; chunk flush can precede that callback.
        if(!be.getMainNode().isReady() && pendingNode!=null)tag.put("proxy",pendingNode.copy());
    }
}
