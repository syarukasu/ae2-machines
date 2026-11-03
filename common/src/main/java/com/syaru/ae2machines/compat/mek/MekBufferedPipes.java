package com.syaru.ae2machines.compat.mek;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import com.syaru.ae2machines.platform.*;
import com.syaru.ae2machines.engine.MaterialBuffer;
import com.syaru.ae2machines.runtime.CapturedPipes;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.EntityBlock;
import java.util.*;
import java.util.function.BiFunction;
import static com.syaru.ae2machines.world.NativeFactoryCompiler.require;

/** One owned balance per native Mekanism fluid/gas network, not one copy per transmitter. */
public final class MekBufferedPipes implements CapturedPipes {
    private record Face(long pos,Direction side,String mode,long pull) {}
    private final class Network {
        final CompoundTag definition;
        final boolean fluid;
        final long capacity;
        final List<Face> endpoints=new ArrayList<>();
        final MaterialBuffer<AEKey> buffer;
        Network(CompoundTag definition,CompoundTag saved) {
            this.definition=definition;fluid=definition.getBoolean("fluid");capacity=definition.getLong("capacity");
            long amount=saved.getLong("amount");AEKey key=saved.contains("key")?PlatformData.loadKey(saved.getCompound("key"),level):null;
            require(capacity>0 && amount>=0 && amount<=capacity && (amount==0 || key!=null && MekPipeAccess.accepts(fluid,key)),"INVALID_MEK_PIPE_CONTENT");
            buffer=new MaterialBuffer<>(capacity,new MaterialBuffer.Snapshot<>(key,amount));
        }
        long insert(AEKey offered,long count,boolean simulate) {
            if(!MekPipeAccess.accepts(fluid,offered))return 0;
            long accepted=buffer.insert(offered,count,simulate);
            if(accepted>0 && !simulate)changed.run();return accepted;
        }
        CompoundTag save() {
            var tag=new CompoundTag();var value=buffer.snapshot();tag.putLong("amount",value.amount());
            if(value.key()!=null)tag.put("key",PlatformData.saveKey(value.key(),level));return tag;
        }
        void tick() {
            for(var face:endpoints)if(face.mode().equals("PULL")) {
                var source=target.apply(face.pos(),face.side());if(source==null)continue;
                var stock=new KeyCounter();source.getAvailableStacks(stock);
                for(var entry:stock) {
                    long offer=insert(entry.getKey(),Math.min(face.pull(),entry.getLongValue()),true);
                    if(offer<=0)continue;
                    long extracted=source.extract(entry.getKey(),offer,Actionable.MODULATE,actionSource);
                    require(extracted>=0 && extracted<=offer,"MEK_PIPE_PULL_AMOUNT");
                    require(insert(entry.getKey(),extracted,false)==extracted,"MEK_PIPE_PULL_CHANGED");
                    if(extracted>0)break;
                }
            }
            var value=buffer.snapshot();if(value.amount()==0)return;
            var sinks=new ArrayList<MEStorage>();
            for(var face:endpoints)if(face.mode().equals("NORMAL") || face.mode().equals("PUSH")) {
                var sink=target.apply(face.pos(),face.side());if(sink!=null)sinks.add(sink);
            }
            long sent=MekDistribution.send(value.key(),value.amount(),sinks,actionSource);
            buffer.delivered(value.key(),sent);
            if(sent>0)changed.run();
        }
    }
    private final ServerLevel level;
    private final Runnable changed;
    private final BiFunction<Long,Direction,MEStorage> target;
    private final Map<String,Network> networks=new LinkedHashMap<>();
    private final Map<Long,Network> byPipe=new HashMap<>();
    private final Map<Long,CompoundTag> nodes=new HashMap<>();
    private final IActionSource actionSource=IActionSource.empty();
    public MekBufferedPipes(ServerLevel level,CompoundTag blueprint,CompoundTag saved,
                            BiFunction<Long,Direction,MEStorage> target,Runnable changed) {
        this.level=level;this.target=target;this.changed=changed;
        require(saved.isEmpty() || saved.getInt("schema")==1,"UNKNOWN_MEK_PIPE_STATE");
        for(var value:blueprint.getCompound("pipes").getList("mek",Tag.TAG_COMPOUND)) {
            var definition=(CompoundTag)value;String id=definition.getString("id");
            require(!networks.containsKey(id) && (saved.isEmpty() || saved.contains(id)),"MISSING_MEK_PIPE_NETWORK");
            var network=new Network(definition,saved.isEmpty()?definition.getCompound("initial"):saved.getCompound(id));networks.put(id,network);
            for(var v:definition.getList("nodes",Tag.TAG_COMPOUND)) {
                var node=(CompoundTag)v;long pos=node.getLong("pos");
                require(byPipe.put(pos,network)==null,"DUPLICATE_MEK_PIPE");nodes.put(pos,node);
            }
        }
        for(var entry:nodes.entrySet()) {
            long pos=entry.getKey();var node=entry.getValue();var network=byPipe.get(pos);
            for(var side:Direction.values()) {
                long neighbor=BlockPos.of(pos).relative(side).asLong();
                if(byPipe.containsKey(neighbor))continue;
                String mode=node.getCompound("faces").getString(side.getName());
                require(Set.of("NONE","NORMAL","PUSH","PULL").contains(mode),"INVALID_MEK_PIPE_MODE");
                if(!mode.equals("NONE"))network.endpoints.add(new Face(neighbor,side.getOpposite(),mode,node.getLong("pull")));
            }
        }
        if(!saved.isEmpty()) {
            var expected=new HashSet<>(networks.keySet());expected.add("schema");
            require(saved.getAllKeys().equals(expected),"MEK_PIPE_NETWORK_SET_CHANGED");
        }
    }
    @Override public MEStorage at(long pos,Direction side) {
        var network=byPipe.get(pos);if(network==null || side==null)return null;
        String mode=nodes.get(pos).getCompound("faces").getString(side.getName());
        if(!mode.equals("NORMAL") && !mode.equals("PULL"))return null;
        return new MEStorage() {
            @Override public long insert(AEKey key,long amount,Actionable mode,IActionSource source){return network.insert(key,amount,mode.isSimulate());}
            @Override public Component getDescription(){return Component.literal("Captured Mekanism Pipe");}
        };
    }
    @Override public void tick(){for(var network:networks.values())network.tick();}
    @Override public CompoundTag save(){var tag=new CompoundTag();tag.putInt("schema",1);networks.forEach((id,n)->tag.put(id,n.save()));return tag;}
    @Override public void restore(CompoundTag blueprint) {
        var remaining=new IdentityHashMap<Network,Long>();networks.values().forEach(n->remaining.put(n,n.buffer.snapshot().amount()));
        for(var value:blueprint.getList("blocks",Tag.TAG_COMPOUND)) {
            var block=(CompoundTag)value;long pos=block.getLong("pos");var network=byPipe.get(pos);if(network==null)continue;
            var state=NbtUtils.readBlockState(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK),block.getCompound("state"));
            require(state.getBlock() instanceof EntityBlock,"MISSING_MEK_PIPE_BLOCK");
            var be=((EntityBlock)state.getBlock()).newBlockEntity(BlockPos.of(pos),state);
            require(MekPipeAccess.supported(be),"MISSING_MEK_PIPE_BLOCK");
            be.setLevel(level);PlatformData.loadBlock(be,block.getCompound("data"));
            long share=Math.min(remaining.get(network),nodes.get(pos).getLong("capacity"));
            if(network.fluid)share=Math.min(share,Integer.MAX_VALUE);
            MekPipeAccess.restoreShare(be,network.buffer.snapshot().key(),share);
            block.put("data",PlatformData.saveBlock(be));remaining.put(network,remaining.get(network)-share);
        }
        require(remaining.values().stream().allMatch(n->n==0),"MEK_PIPE_RESTORE_CAPACITY");
    }
}
