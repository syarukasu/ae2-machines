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
    public static appeng.api.networking.ticking.TickingRequest sleepingTickRequest(){return new appeng.api.networking.ticking.TickingRequest(1,20,true);}
    public static void loadProvider(appeng.helpers.patternprovider.PatternProviderLogic logic,CompoundTag tag,Level level){logic.readFromNBT(tag,level.registryAccess());}
    public static void saveProvider(appeng.helpers.patternprovider.PatternProviderLogic logic,CompoundTag tag,Level level){logic.writeToNBT(tag,level.registryAccess());}
    public static void chunkSaveEvent(net.minecraft.server.level.ServerLevel level,net.minecraft.world.level.chunk.LevelChunk chunk,CompoundTag data) {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.ChunkDataEvent.Save(chunk,level,data));
    }
    public static CompoundTag cellData(ItemStack stack) { return stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag(); }
    public static void setCellData(ItemStack stack,CompoundTag tag) { stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(tag)); }
    public static ResourceLocation id(String id) { return ResourceLocation.parse(id); }
    public static CompoundTag visualTag(BlockEntity be){return be.getUpdateTag(be.getLevel().registryAccess());}
    public static void applyVisualTag(BlockEntity be,CompoundTag tag){be.handleUpdateTag(tag,be.getLevel().registryAccess());}
    public static boolean ldlibPresent(){return net.neoforged.fml.ModList.get().isLoaded("ldlib");}
    public static boolean emiPresent(){return net.neoforged.fml.ModList.get().isLoaded("emi");}
    public static ItemStack processingPattern(List<GenericStack> inputs,List<GenericStack> outputs) {
        return PatternDetailsHelper.encodeProcessingPattern(inputs,outputs);
    }
    public static CompoundTag saveBlock(BlockEntity be) { return com.syaru.ae2machines.runtime.NativeAssemblers.save(be,be.saveWithFullMetadata(be.getLevel().registryAccess())); }
    public static void loadBlock(BlockEntity be,CompoundTag tag) { be.loadWithComponents(tag,be.getLevel().registryAccess());com.syaru.ae2machines.runtime.NativeAssemblers.loaded(be,tag); }
    public static CompoundTag saveItem(ItemStack stack,Level level) { return (CompoundTag)stack.save(level.registryAccess()); }
    public static ItemStack loadItem(CompoundTag tag,Level level) { return ItemStack.parseOptional(level.registryAccess(),tag); }
    public static CompoundTag saveKey(AEKey key,Level level) { return key.toTagGeneric(level.registryAccess()); }
    public static AEKey loadKey(CompoundTag tag,Level level) { return AEKey.fromTagGeneric(level.registryAccess(),tag); }
    public static CompoundTag readNbt(DataInput input) throws IOException { return NbtIo.read(input,NbtAccounter.unlimitedHeap()); }
    public static net.minecraft.server.level.ServerPlayer spatialActor(net.minecraft.server.level.ServerLevel level,java.util.UUID owner) {
        return net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(owner,"AE2SpatialOwner"));
    }
}
