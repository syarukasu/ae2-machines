package com.syaru.ae2machines.compat.mek;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import com.syaru.ae2machines.engine.ItemTransit;
import com.syaru.ae2machines.platform.*;
import com.syaru.ae2machines.runtime.CapturedPipes;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.EntityBlock;
import java.util.*;
import java.util.function.*;
import static com.syaru.ae2machines.world.NativeFactoryCompiler.require;

/** Captured Mekanism item topology and persistent, capacity-reserved transit ownership. */
public final class MekItemPipes implements CapturedPipes {
    private final ServerLevel level;
    private final Runnable changed;
    private final BiFunction<Long,Direction,MEStorage> target;
    private final LongFunction<Preview> preview;
    private final Map<Long,CompoundTag> nodes=new LinkedHashMap<>();
    private final Map<Long,Integer> cooldown=new HashMap<>(),failures=new HashMap<>();
    private final ItemTransit<AEItemKey> transit;
    private final IActionSource source=IActionSource.empty();
    public MekItemPipes(ServerLevel level,CompoundTag blueprint,CompoundTag saved,
                        BiFunction<Long,Direction,MEStorage> target,LongFunction<Preview> preview,Runnable changed) {
        this.level=level;this.changed=changed;this.target=target;this.preview=preview;
        require(saved.isEmpty() || saved.getInt("schema")==1,"UNKNOWN_MEK_ITEM_PIPE_STATE");
        require(saved.isEmpty() || saved.contains("nextId",Tag.TAG_LONG) && saved.contains("packets",Tag.TAG_LIST),"INCOMPLETE_MEK_ITEM_PIPE_STATE");
        var topology=new ArrayList<ItemTransit.Node>();
        for(var value:blueprint.getCompound("pipes").getList("mekItems",Tag.TAG_COMPOUND)) {
            var node=(CompoundTag)value;long pos=node.getLong("pos");nodes.put(pos,node);
            var endpoints=new ArrayList<ItemTransit.Endpoint>();
            for(var endpoint:node.getList("endpoints",Tag.TAG_COMPOUND))endpoints.add(endpoint((CompoundTag)endpoint));
            topology.add(new ItemTransit.Node(pos,node.getInt("speed"),node.getDouble("cost"),node.getInt("color"),
                    Arrays.stream(node.getLongArray("neighbors")).boxed().toList(),endpoints));
            int delay=saved.getCompound("cooldown").getInt(Long.toString(pos)),fails=saved.getCompound("failures").getInt(Long.toString(pos));
            require(delay>=0 && delay<=40 && fails>=0 && fails<=6,"INVALID_TRANSPORTER_PULL_STATE");
            cooldown.put(pos,delay);failures.put(pos,fails);
        }
        var packets=new ArrayList<ItemTransit.Packet<AEItemKey>>();
        for(var value:saved.getList("packets",Tag.TAG_COMPOUND)) {
            var p=(CompoundTag)value;
            for(var name:List.of("id","pipe","home"))require(p.contains(name,Tag.TAG_LONG),"INCOMPLETE_TRANSPORTER_PACKET");
            for(var name:List.of("amount","color","progress"))require(p.contains(name,Tag.TAG_INT),"INCOMPLETE_TRANSPORTER_PACKET");
            require(p.contains("route",Tag.TAG_LONG_ARRAY) && p.contains("key",Tag.TAG_COMPOUND),"INCOMPLETE_TRANSPORTER_PACKET");
            var key=PlatformData.loadKey(p.getCompound("key"),level);
            require(key instanceof AEItemKey,"INVALID_TRANSPORTER_ITEM");
            packets.add(new ItemTransit.Packet<>(p.getLong("id"),(AEItemKey)key,p.getInt("amount"),p.getLong("pipe"),
                    p.getLong("home"),p.getInt("color"),p.getInt("progress"),Arrays.stream(p.getLongArray("route")).boxed().toList(),
                    p.contains("target")?endpoint(p.getCompound("target")):null));
        }
        transit=new ItemTransit<>(topology,new ItemTransit.Access<>() {
            public long capacity(ItemTransit.Endpoint e,AEItemKey key,int offer,List<ItemTransit.Packet<AEItemKey>> reservations) {
                var copy=preview.apply(e.position());if(copy==null)return 0;
                // Apply ALL reserved item identities to a disposable native inventory, not a
                // count subtraction that overlooks competing types in the same empty slot.
                for(var packet:reservations)if(copy.aliases().contains(packet.target().position())) {
                    var side=Direction.from3DDataValue(packet.target().face());var storage=copy.storage().apply(side);
                    if(storage==null || storage.insert(packet.key(),packet.amount(),Actionable.MODULATE,source)!=packet.amount())return 0;
                }
                var storage=copy.storage().apply(Direction.from3DDataValue(e.face()));
                return storage==null?0:storage.insert(key,offer,Actionable.SIMULATE,source);
            }
            public long deliver(ItemTransit.Endpoint e,AEItemKey key,int offer) {
                var storage=target.apply(e.position(),Direction.from3DDataValue(e.face()));
                return storage==null?0:storage.insert(key,offer,Actionable.MODULATE,source);
            }
        },saved.getLong("nextId"),packets);
    }
    private static ItemTransit.Endpoint endpoint(CompoundTag tag) {
        require(tag.contains("pipe",Tag.TAG_LONG) && tag.contains("pos",Tag.TAG_LONG)
                && tag.contains("face",Tag.TAG_INT) && tag.contains("color",Tag.TAG_INT),"INCOMPLETE_TRANSPORTER_ENDPOINT");
        int face=tag.getInt("face"),color=tag.getInt("color");
        require(face>=0 && face<6 && color>=-2 && color<16,"INVALID_TRANSPORTER_ENDPOINT");
        return new ItemTransit.Endpoint(tag.getLong("pipe"),tag.getLong("pos"),face,color);
    }
    private static CompoundTag endpoint(ItemTransit.Endpoint endpoint) {
        var tag=new CompoundTag();tag.putLong("pipe",endpoint.pipe());tag.putLong("pos",endpoint.position());
        tag.putInt("face",endpoint.face());tag.putInt("color",endpoint.color());return tag;
    }
    @Override public MEStorage at(long pos,Direction side) {
        var node=nodes.get(pos);if(node==null || side==null || !node.getCompound("faces").getCompound(side.getName()).getBoolean("insert"))return null;
        return new ColoredItemInput() {
            @Override public long insert(AEKey key,long amount,Actionable mode,IActionSource actionSource) {
                return insertColored(key,amount,mode,node.getInt("color"));
            }
            @Override public long insertColored(AEKey key,long amount,Actionable mode,int color) {
                if(!(key instanceof AEItemKey item) || amount<=0)return 0;
                int accepted=transit.insert(item,(int)Math.min(Integer.MAX_VALUE,amount),pos,BlockPos.of(pos).relative(side).asLong(),color,mode.isSimulate());
                if(accepted>0 && !mode.isSimulate())changed.run();return accepted;
            }
            @Override public Component getDescription(){return Component.literal("Captured Mekanism Transporter");}
        };
    }
    @Override public void tick() {
        boolean dirty=false;
        for(var entry:nodes.entrySet()) {
            long pos=entry.getKey();int delay=cooldown.get(pos);
            if(delay>0){cooldown.put(pos,delay-1);dirty=true;continue;}
            boolean pulling=false,moved=false;
            for(var side:Direction.values()) {
                if(!entry.getValue().getCompound("faces").getCompound(side.getName()).getBoolean("pull"))continue;
                long home=BlockPos.of(pos).relative(side).asLong();if(nodes.containsKey(home))continue;
                pulling=true;var storage=target.apply(home,side.getOpposite());if(storage==null)continue;
                var stock=new KeyCounter();storage.getAvailableStacks(stock);
                for(var item:stock)if(item.getKey() instanceof AEItemKey key) {
                    int count=(int)Math.min(entry.getValue().getInt("pull"),item.getLongValue());
                    int fits=transit.insert(key,count,pos,home,true);if(fits==0)continue;
                    long extracted=storage.extract(key,fits,Actionable.MODULATE,source);
                    require(extracted>=0 && extracted<=fits,"TRANSPORTER_PULL_AMOUNT");
                    if(extracted==0)continue;
                    int accepted=transit.insert(key,(int)extracted,pos,home,false);
                    if(accepted<extracted)transit.hold(key,(int)extracted-accepted,pos,home);
                    moved=true;dirty=true;break;
                }
            }
            if(pulling) {
                int failed=moved?0:Math.min(6,failures.get(pos)+1);
                failures.put(pos,failed);cooldown.put(pos,moved?10:Math.min(40,1<<failed));dirty=true;
            }
        }
        if(transit.tick())dirty=true;
        if(dirty)changed.run();
    }
    @Override public CompoundTag save() {
        var tag=new CompoundTag();tag.putInt("schema",1);tag.putLong("nextId",transit.nextId());
        var list=new ListTag();
        for(var p:transit.packets()) {
            var value=new CompoundTag();value.putLong("id",p.id());value.put("key",PlatformData.saveKey(p.key(),level));
            value.putInt("amount",p.amount());value.putLong("pipe",p.pipe());value.putLong("home",p.home());
            value.putInt("color",p.color());value.putInt("progress",p.progress());
            value.putLongArray("route",p.route().stream().mapToLong(Long::longValue).toArray());
            if(p.target()!=null)value.put("target",endpoint(p.target()));list.add(value);
        }
        tag.put("packets",list);var delays=new CompoundTag();var failed=new CompoundTag();
        cooldown.forEach((pos,n)->delays.putInt(Long.toString(pos),n));failures.forEach((pos,n)->failed.putInt(Long.toString(pos),n));
        tag.put("cooldown",delays);tag.put("failures",failed);return tag;
    }
    @Override public void restore(CompoundTag blueprint) {
        var restored=new HashSet<Long>();
        for(var value:blueprint.getList("blocks",Tag.TAG_COMPOUND)) {
            var block=(CompoundTag)value;long pos=block.getLong("pos");if(!nodes.containsKey(pos))continue;
            var state=NbtUtils.readBlockState(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK),block.getCompound("state"));
            require(state.getBlock() instanceof EntityBlock,"MISSING_TRANSPORTER_BLOCK");
            var be=((EntityBlock)state.getBlock()).newBlockEntity(BlockPos.of(pos),state);
            require(MekTransporterAccess.supported(be),"MISSING_TRANSPORTER_BLOCK");
            be.setLevel(level);PlatformData.loadBlock(be,block.getCompound("data"));
            MekTransporterAccess.restore(be,transit.packets());block.put("data",PlatformData.saveBlock(be));restored.add(pos);
        }
        require(restored.equals(nodes.keySet()),"MISSING_TRANSPORTER_RESTORE_NODE");
    }
}
