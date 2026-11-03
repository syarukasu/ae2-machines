package com.syaru.ae2machines.platform;

import com.syaru.ae2machines.block.BaseAccelerator;
import com.syaru.ae2machines.runtime.RuntimeCodec;
import appeng.api.stacks.AEKey;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.HolderLookup;

public class AcceleratorEntity extends BaseAccelerator {
    private CompoundTag retainedInvalid;
    public AcceleratorEntity(BlockPos pos,BlockState state) { super(MachinesMod.ACCELERATOR_ENTITY.get(),pos,state); }
    @Override protected void checkpoint() {
        factoryCheckpoint();
    }
    @Override public void loadTag(CompoundTag tag,HolderLookup.Provider registries) {
        super.loadTag(tag,registries);getMainNode().loadFromNBT(tag);
        if(tag.hasUUID("factory")) { restoreFactoryDisplaySettings(tag.getInt("speedPower"),tag.getInt("lanePower"));factoryId=tag.getUUID("factory");return; }
        if(tag.contains("runtime")) {
            try {
                runtime = RuntimeCodec.read(tag.getCompound("runtime"),key -> AEKey.fromTagGeneric(registries,key), ignored -> checkpoint());
                settings(tag.getInt("speedPower"),tag.getInt("lanePower"));
            } catch(RuntimeException error) { retainedInvalid=tag.getCompound("runtime").copy();failedLoad(); }
        }
    }
    @Override public void saveAdditional(CompoundTag tag,HolderLookup.Provider registries) {
        super.saveAdditional(tag,registries);getMainNode().saveToNBT(tag);
        tag.putInt("speedPower",speedPower());tag.putInt("lanePower",lanePower());
        if(factoryId!=null) { tag.putUUID("factory",factoryId);return; }
        tag.put("runtime",retainedInvalid != null ? retainedInvalid.copy() :
                RuntimeCodec.write(runtime,key -> key.toTagGeneric(registries)));
    }
    @Override public void onChunkUnloaded() { suspendFactory();getMainNode().destroy();super.onChunkUnloaded(); }
}
