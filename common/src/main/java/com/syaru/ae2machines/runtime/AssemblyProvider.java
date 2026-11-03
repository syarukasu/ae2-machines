package com.syaru.ae2machines.runtime;

import appeng.api.networking.*;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.ticking.*;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.helpers.patternprovider.*;
import com.syaru.ae2machines.platform.PlatformData;
import com.syaru.ae2machines.mixin.ProviderWorkAccess;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.*;
import java.util.function.LongFunction;

/** Native provider logic for captured assemblers and furnace inventories on the session's node. */
public final class AssemblyProvider implements PatternProviderLogicHost {
    private final PatternProviderBlockEntity host;
    private final PatternProviderLogic logic;
    private final Runnable changed;
    private final LongFunction<CapturedAssembler> machines;
    private final java.util.function.BiFunction<Long,Direction,appeng.api.storage.MEStorage> inventories;
    private final appeng.api.networking.security.IActionSource actionSource;
    private final CompoundTag base;
    private boolean initialized;
    public AssemblyProvider(ServerLevel level,CompoundTag block,CompoundTag saved,IManagedGridNode node,
                            ICraftingProvider service,Runnable changed,LongFunction<CapturedAssembler> machines,
                            java.util.function.BiFunction<Long,Direction,appeng.api.storage.MEStorage> inventories) {
        this.changed=changed;this.machines=machines;
        this.inventories=inventories;this.actionSource=new appeng.me.helpers.MachineSource(node::getNode);
        var state=NbtUtils.readBlockState(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK),block.getCompound("state"));
        host=(PatternProviderBlockEntity)((EntityBlock)state.getBlock()).newBlockEntity(BlockPos.of(block.getLong("pos")),state);
        host.setLevel(level);base=(saved.isEmpty()?block.getCompound("data"):saved).copy();
        PlatformData.loadBlock(host,base);
        logic=new PatternProviderLogic(node,this);
        PlatformData.loadProvider(logic,base,level);
        logic.updatePatterns();
        node.addService(ICraftingProvider.class,service);
        // FactorySession drives native return work inside the existing durable handoff.
        node.addService(IGridTickable.class,new IGridTickable(){
            public TickingRequest getTickingRequest(IGridNode n){return PlatformData.sleepingTickRequest();}
            public TickRateModulation tickingRequest(IGridNode n,int ticks){return TickRateModulation.SLEEP;}
        });
        initialized=true;
    }
    public CapturedAssembler adjacent(BlockPos pos){return machines.apply(pos.asLong());}
    public PatternProviderTarget inventory(Direction side) {
        var storage=inventories.apply(host.getBlockPos().relative(side).asLong(),side.getOpposite());
        if(storage==null)return null;
        return new PatternProviderTarget() {
            public long insert(appeng.api.stacks.AEKey key,long amount,appeng.api.config.Actionable mode) {
                return storage.insert(key,amount,mode,actionSource);
            }
            public boolean containsPatternInput(Set<appeng.api.stacks.AEKey> keys) {
                for(var entry:storage.getAvailableStacks())if(keys.contains(entry.getKey().dropSecondary()))return true;
                return false;
            }
        };
    }
    @Override public PatternProviderLogic getLogic(){return logic;}
    @Override public BlockEntity getBlockEntity(){return host;}
    @Override public EnumSet<Direction> getTargets(){
        var sides=host.getTargets();sides.removeIf(side->adjacent(host.getBlockPos().relative(side))==null && inventory(side)==null);return sides;
    }
    @Override public void saveChanges(){if(initialized)changed.run();}
    @Override public AEItemKey getTerminalIcon(){return host.getTerminalIcon();}
    @Override public net.minecraft.world.item.ItemStack getMainMenuIcon(){return host.getMainMenuIcon();}
    public boolean hasWork(){return ((ProviderWorkAccess)logic).machines$hasWork();}
    public void work(){((ProviderWorkAccess)logic).machines$work();}
    public CompoundTag save(){var data=base.copy();PlatformData.saveProvider(logic,data,host.getLevel());return data;}
}
