package com.syaru.ae2machines.runtime;

import appeng.blockentity.crafting.MolecularAssemblerBlockEntity;
import com.syaru.ae2machines.mixin.AssemblerAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class NativeAssemblers {
    private NativeAssemblers(){}
    public static boolean supports(BlockEntity be){return be!=null && (be.getClass()==MolecularAssemblerBlockEntity.class || be instanceof CapturedAssembler);}
    public static CompoundTag save(BlockEntity be,CompoundTag tag) {
        if(supports(be))tag.putDouble("ae2machines:assemblerProgress",((AssemblerAccess)be).machines$progress());
        return tag;
    }
    public static void loaded(BlockEntity be,CompoundTag tag) {
        if(supports(be) && tag.contains("ae2machines:assemblerProgress")) {
            double progress=tag.getDouble("ae2machines:assemblerProgress");
            if(!Double.isFinite(progress) || progress<0 || progress>=100)throw new IllegalArgumentException("INVALID_ASSEMBLER_PROGRESS");
            ((AssemblerAccess)be).machines$progress(progress);
        }
    }
}
