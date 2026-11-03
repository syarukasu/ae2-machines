package com.syaru.ae2machines.world;

import com.syaru.ae2machines.platform.PlatformData;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import java.util.*;
import static com.syaru.ae2machines.world.NativeFactoryCompiler.require;

public final class FactoryWorld {
    private FactoryWorld() {}
    public static void checkpointCellHolder(ServerLevel level,BlockPos pos) {
        var blocks=new ListTag();var entry=new CompoundTag();entry.putLong("pos",pos.asLong());blocks.add(entry);
        FactoryChunkSave.flush(level,blocks);
    }
    public static FactoryJournal capture(ServerLevel level,Player player,BlockPos first,BlockPos second) {
        return capture(level,player,first,second,id->{});
    }
    public static FactoryJournal capture(ServerLevel level,Player player,BlockPos first,BlockPos second,java.util.function.Consumer<UUID> prepared) {
        return NativeWorldFactory.capture(level,player,first,second,prepared);
    }
    public static void restore(ServerLevel level,Player player,UUID id) {
        var journal=FactoryJournal.open(level,id);var record=journal.read();var blueprint=record.getCompound("blueprint");
        if(blueprint.getInt("schema")==4){NativeWorldFactory.restore(level,player,journal);return;}
        require(blueprint.getString("dimension").equals(level.dimension().location().toString()),"WRONG_DIMENSION");
        require(blueprint.getUUID("player").equals(player.getUUID()),"FACTORY_OWNER_MISMATCH");
        String phase=record.getString("state");
        require(Set.of("CAPTURED","SNAPSHOT_SAVED","RESTORE_PREPARED").contains(phase),"FACTORY_MUST_BE_IDLE_AND_DETACHED");
        var blocks=blueprint.getList("blocks",Tag.TAG_COMPOUND);
        require(!blocks.isEmpty(),"MISSING_FACTORY_BLOCKS");
        for(var entry:blocks) {
            var saved=(CompoundTag)entry;var pos=BlockPos.of(saved.getLong("pos"));
            require(level.hasChunkAt(pos),"CHUNK_UNLOADED");
            require(level.mayInteract(player,pos),"PERMISSION_DENIED");
            if(!level.getBlockState(pos).isAir()) {
                require(!phase.equals("CAPTURED"),"RESTORE_SPACE_OCCUPIED");
                var state=NbtUtils.readBlockState(level.registryAccess().lookupOrThrow(Registries.BLOCK),saved.getCompound("state"));
                var be=level.getBlockEntity(pos);
                require(state.equals(level.getBlockState(pos)) && (saved.contains("data") ? be!=null && PlatformData.saveBlock(be).equals(saved.getCompound("data")):be==null),
                        "RECOVERY_WORLD_DIFFERS_FROM_SNAPSHOT");
            }
        }
        journal.state("RESTORE_PREPARED");
        for(var entry:blocks) {
            var saved=(CompoundTag)entry;var pos=BlockPos.of(saved.getLong("pos"));
            if(!level.getBlockState(pos).isAir()) continue;
            var state=NbtUtils.readBlockState(level.registryAccess().lookupOrThrow(Registries.BLOCK),saved.getCompound("state"));
            require(level.setBlock(pos,state,3),"WORLD_RESTORE_FAILED");
            if(saved.contains("data")) {
                var be=level.getBlockEntity(pos);require(be!=null,"RESTORED_BLOCK_ENTITY_MISSING");
                PlatformData.loadBlock(be,saved.getCompound("data"));be.setChanged();
            }
        }
        FactoryChunkSave.flush(level,blocks);journal.state("RESTORED");
    }
}
