package com.syaru.ae2machines.platform;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.io.*;
import java.util.List;

public final class PlatformData {
    private PlatformData() {}
    public static appeng.api.networking.ticking.TickingRequest sleepingTickRequest(){return new appeng.api.networking.ticking.TickingRequest(1,20,true,true);}
    public static void loadProvider(appeng.helpers.patternprovider.PatternProviderLogic logic,CompoundTag tag,Level level){logic.readFromNBT(tag);}
    public static void saveProvider(appeng.helpers.patternprovider.PatternProviderLogic logic,CompoundTag tag,Level level){logic.writeToNBT(tag);}
    public static void chunkSaveEvent(net.minecraft.server.level.ServerLevel level,net.minecraft.world.level.chunk.LevelChunk chunk,CompoundTag data) {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.level.ChunkDataEvent.Save(chunk,level,data));
    }
    public static CompoundTag cellData(ItemStack stack) { return stack.getOrCreateTag().copy(); }
    public static void setCellData(ItemStack stack,CompoundTag tag) { stack.setTag(tag); }
    public static ResourceLocation id(String id) { return new ResourceLocation(id); }
    public static CompoundTag visualTag(BlockEntity be){return be.getUpdateTag();}
    public static void applyVisualTag(BlockEntity be,CompoundTag tag){be.handleUpdateTag(tag);}
    public static boolean ldlibPresent(){return net.minecraftforge.fml.ModList.get().isLoaded("ldlib");}
    public static boolean emiPresent(){return net.minecraftforge.fml.ModList.get().isLoaded("emi");}
    public static ItemStack processingPattern(List<GenericStack> inputs,List<GenericStack> outputs) {
        return PatternDetailsHelper.encodeProcessingPattern(inputs.toArray(GenericStack[]::new),outputs.toArray(GenericStack[]::new));
    }
    public static CompoundTag saveBlock(BlockEntity be) { return com.syaru.ae2machines.runtime.NativeAssemblers.save(be,be.saveWithFullMetadata()); }
    public static void loadBlock(BlockEntity be,CompoundTag tag) { be.load(tag);com.syaru.ae2machines.runtime.NativeAssemblers.loaded(be,tag); }
    public static CompoundTag saveItem(ItemStack stack,Level level) { return stack.save(new CompoundTag()); }
    public static ItemStack loadItem(CompoundTag tag,Level level) { return ItemStack.of(tag); }
    public static CompoundTag saveKey(AEKey key,Level level) { return key.toTagGeneric(); }
    public static AEKey loadKey(CompoundTag tag,Level level) { return AEKey.fromTagGeneric(tag); }
    public static CompoundTag readNbt(DataInput input) throws IOException { return NbtIo.read(input); }
    public static net.minecraft.server.level.ServerPlayer spatialActor(net.minecraft.server.level.ServerLevel level,java.util.UUID owner) {
        return net.minecraftforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(owner,"AE2SpatialOwner"));
    }
}
