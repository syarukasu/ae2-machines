package com.syaru.ae2machines.world;

import appeng.api.config.*;
import appeng.api.crafting.*;
import appeng.api.networking.GridFlags;
import appeng.api.parts.IPartHost;
import appeng.api.stacks.*;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import com.syaru.ae2machines.platform.*;
import com.syaru.ae2machines.runtime.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.*;

/** Each captured provider keeps its own machine, external ME boundary and energy ownership. */
public final class NativeFactoryCompiler {
    public record Compiled(CompoundTag blueprint,PatternProgram program,MachineProfile energy,BlockEntity machine) {
        public Compiled(CompoundTag blueprint,PatternProgram program,MachineProfile energy){this(blueprint,program,energy,null);}
    }
    private NativeFactoryCompiler() {}
    public static boolean available() {
        return true;
    }
    public static Compiled rebuild(ServerLevel level,CompoundTag data,java.util.function.BooleanSupplier online) {
        require(available(),"NATIVE_ADAPTER_UNAVAILABLE");
        require(data.getInt("schema")==1,"UNKNOWN_FACTORY_SCHEMA");
        if(data.contains("gtStructure")) {
            require(NativeMachines.gt(),"GT_ADAPTER_UNAVAILABLE");
            var profiles=new LinkedHashMap<IPatternDetails,MachineProfile>();
            for(var value:data.getList("patterns",Tag.TAG_COMPOUND)) {
                var pattern=PatternDetailsHelper.decodePattern(PlatformData.loadItem((CompoundTag)value,level),level);
                require(pattern!=null,"PATTERN_NO_LONGER_VALID");
                profiles.put(pattern,GtMultiblockAdapter.inspect(level,data.getCompound("gtStructure"),PatternProgram.inputTotals(pattern))
                        .orElseThrow(()->new IllegalArgumentException("GT_STRUCTURE_RECIPE_NO_LONGER_VALID")));
            }
            return new Compiled(data.copy(),new PatternProgram(profiles,online),profiles.values().iterator().next());
        }
        BlockEntity machine=null;
        for(var value:data.getList("blocks",Tag.TAG_COMPOUND)) {
            var block=(CompoundTag)value;
            if(block.getLong("pos")!=data.getLong("machine")) continue;
            var state=NbtUtils.readBlockState(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK),block.getCompound("state"));
            require(state.getBlock() instanceof net.minecraft.world.level.block.EntityBlock,"MISSING_NATIVE_MACHINE");
            machine=((net.minecraft.world.level.block.EntityBlock)state.getBlock()).newBlockEntity(BlockPos.of(block.getLong("pos")),state);
            require(machine!=null,"MISSING_NATIVE_MACHINE");machine.setLevel(level);
            PlatformData.loadBlock(machine,block.getCompound("data"));
        }
        require(machine!=null && NativeMachines.supports(machine),"UNSUPPORTED_NATIVE_MACHINE");
        var profiles=new LinkedHashMap<IPatternDetails,MachineProfile>();
        if(data.getBoolean("assemblyProvider") || data.getBoolean("furnaceProvider")) {
            require(data.getBoolean("assemblyProvider")?NativeAssemblers.supports(machine):VanillaFurnaces.supports(machine),"NATIVE_PROVIDER_ROUTE_CHANGED");
            return new Compiled(data.copy(),new PatternProgram(profiles,online),NativeMachines.idleProfile(machine),machine);
        }
        for(var value:data.getList("patterns",Tag.TAG_COMPOUND)) {
            var pattern=PatternDetailsHelper.decodePattern(PlatformData.loadItem((CompoundTag)value,level),level);
            require(pattern!=null,"PATTERN_NO_LONGER_VALID");
            var inputs=PatternProgram.inputTotals(pattern);
            var profile=NativeMachines.inspect(machine,inputs);
            require(profile.isPresent(),"NATIVE_RECIPE_NO_LONGER_VALID");profiles.put(pattern,profile.get());
        }
        var program=new PatternProgram(profiles,online);
        var energy=profiles.isEmpty()?NativeMachines.idleProfile(machine):profiles.values().iterator().next();
        return new Compiled(data.copy(),program,energy,machine);
    }
    public static Compiled inspect(ServerLevel level,Player player,BlockPos first,BlockPos second) {
        long volume=(Math.abs((long)first.getX()-second.getX())+1)*(Math.abs((long)first.getY()-second.getY())+1)
                *(Math.abs((long)first.getZ()-second.getZ())+1);
        require(volume<=4096,"SCAN_LIMIT");
        var providers=new ArrayList<PatternProviderBlockEntity>();var machines=new HashSet<BlockPos>();
        var devices=new ArrayList<BlockEntity>();var captured=new HashSet<BlockPos>();var allBlocks=new ListTag();
        var otherBlocks=new HashSet<BlockPos>();
        for(var p:BlockPos.betweenClosed(first,second)) {
            require(level.hasChunkAt(p),"CHUNK_UNLOADED");
            require(level.mayInteract(player,p),"PERMISSION_DENIED");
            var state=level.getBlockState(p);
            if(state.isAir() || state.is(MachinesMod.MARKER.get()))continue;
            var be=level.getBlockEntity(p);
            require(!NativeMachines.craftingCpu(be),"CRAFTING_CPU_MUST_REMAIN_OUTSIDE: "+p);
            if(be!=null && be.getClass()==PatternProviderBlockEntity.class) { providers.add((PatternProviderBlockEntity)be);devices.add(be); }
            else if(be!=null && available() && NativeMachines.supports(be)) {machines.add(p.immutable());devices.add(be);}
            else if(be!=null && NetworkBlueprint.passive(be))devices.add(be);
            else otherBlocks.add(p.immutable());
            if(be!=null)require(appeng.util.Platform.hasPermissions(new appeng.api.util.DimensionalBlockPos(be),player),"AE2_PERMISSION_DENIED");
            captured.add(p.immutable());
        }
        var groups=new HashMap<BlockPos,Set<BlockPos>>();var structureBlocks=new HashSet<BlockPos>();
        for(var machine:machines) {
            var group=NativeMachines.members(level.getBlockEntity(machine));groups.put(machine,group);
            for(var pos:group) {
                require(pos.getX()>=Math.min(first.getX(),second.getX()) && pos.getX()<=Math.max(first.getX(),second.getX())
                        && pos.getY()>=Math.min(first.getY(),second.getY()) && pos.getY()<=Math.max(first.getY(),second.getY())
                        && pos.getZ()>=Math.min(first.getZ(),second.getZ()) && pos.getZ()<=Math.max(first.getZ(),second.getZ()),"GT_STRUCTURE_OUTSIDE_CAPTURE");
                require(structureBlocks.add(pos),"SHARED_MACHINE_STRUCTURE");otherBlocks.remove(pos);
            }
        }
        require(otherBlocks.isEmpty(),"UNSUPPORTED_BLOCKS: "+otherBlocks.stream().map(p->p+"="+level.getBlockState(p)).toList());
        require(!machines.isEmpty(),"NO_NATIVE_MACHINES");
        var network=NetworkBlueprint.inspect(devices,captured);
        var pipes=CapturedPipes.inspect(devices,captured);
        var lanes=new ListTag();var used=new HashSet<BlockPos>();Compiled head=null;
        for(var provider:providers) {
            var adjacent=machines.stream().filter(p->NativeMachines.reaches(level.getBlockEntity(p),provider.getBlockPos()))
                    .sorted(java.util.Comparator.comparingLong(BlockPos::asLong)).toList();
            boolean assemblers=!adjacent.isEmpty() && adjacent.stream().allMatch(p->NativeAssemblers.supports(level.getBlockEntity(p)));
            // Native ProviderLogic chooses among adjacent assemblers. Other machine routes stay explicit.
            require((adjacent.size()==1 || assemblers) && used.add(adjacent.get(0)),"AMBIGUOUS_MACHINE_ROUTE");
            var machine=level.getBlockEntity(adjacent.get(0));
            var pair=NativeMachines.multiblock(machine)?inspectStructure(level,player,provider,machine,groups.get(machine.getBlockPos()),captured)
                    :inspectPair(level,player,provider.getBlockPos(),adjacent.get(0),true,captured);
            if(head==null)head=pair;
            lanes.add(pair.blueprint());
        }
        for(var pos:machines)if(!used.contains(pos)) {
            var passive=inspectPassive(level,player,level.getBlockEntity(pos),captured,network);
            if(head==null)head=passive;lanes.add(passive.blueprint());
        }
        // Serialize cells only after AE2 persisted its live cell handlers during network inspection.
        for(var pos:captured) {
            var block=new CompoundTag();block.putLong("pos",pos.asLong());block.put("state",NbtUtils.writeBlockState(level.getBlockState(pos)));
            if(level.getBlockEntity(pos)!=null)block.put("data",PlatformData.saveBlock(level.getBlockEntity(pos)));allBlocks.add(block);
        }
        var data=new CompoundTag();data.putInt("schema",3);data.put("lanes",lanes);data.put("blocks",allBlocks);data.put("network",network);
        if(!pipes.isEmpty())data.put("pipes",pipes);
        data.putString("dimension",level.dimension().location().toString());data.putUUID("player",player.getUUID());
        return new Compiled(data,head.program(),head.energy());
    }
    private static Compiled inspectPassive(ServerLevel level,Player player,BlockEntity machine,Set<BlockPos> captured,CompoundTag network) {
        require(NativeMachines.buffered(machine),"PASSIVE_NATIVE_EXECUTOR_UNSUPPORTED");
        NativeMachines.verifyPassiveBoundary(machine,captured);
        var block=new CompoundTag();block.putLong("pos",machine.getBlockPos().asLong());
        block.put("state",NbtUtils.writeBlockState(machine.getBlockState()));block.put("data",PlatformData.saveBlock(machine));
        var blocks=new ListTag();blocks.add(block);
        var data=new CompoundTag();data.putInt("schema",1);data.putBoolean("passive",true);
        data.put("blocks",blocks);data.put("patterns",new ListTag());data.putLong("machine",machine.getBlockPos().asLong());
        data.putBoolean("network",true);data.putString("dimension",level.dimension().location().toString());data.putUUID("player",player.getUUID());
        // This zero-channel service node owns no inventory. Bind it to a real captured main-grid node.
        long anchor=network.getList("boundaries",Tag.TAG_COMPOUND).getCompound(0).getLong("node");data.putLong("anchor",anchor);
        for(var value:network.getList("nodes",Tag.TAG_COMPOUND))if(((CompoundTag)value).getLong("id")==anchor)
            data.putInt("owner",((CompoundTag)value).getInt("owner"));
        data.put("flags",new ListTag());return rebuild(level,data,()->false);
    }
    public static List<CompoundTag> lanes(CompoundTag blueprint) {
        if(blueprint.getInt("schema")==1)return List.of(blueprint);
        require(blueprint.getInt("schema")==2 || blueprint.getInt("schema")==3,"UNKNOWN_FACTORY_SCHEMA");
        var result=new ArrayList<CompoundTag>();
        for(var lane:blueprint.getList("lanes",Tag.TAG_COMPOUND))result.add((CompoundTag)lane);
        require(!result.isEmpty(),"NO_MACHINE_ROUTES");return List.copyOf(result);
    }
    private static Compiled inspectStructure(ServerLevel level,Player player,PatternProviderBlockEntity provider,BlockEntity machine,Set<BlockPos> members,Set<BlockPos> captured) {
        var logic=provider.getLogic();
        require(!logic.isBusy(),"PROVIDER_HAS_PENDING_INPUT");
        require(logic.getConfigManager().getSetting(Settings.LOCK_CRAFTING_MODE)==LockCraftingMode.NONE,"CRAFTING_LOCK_NOT_SUPPORTED");
        for(int i=0;i<logic.getReturnInv().size();i++)require(logic.getReturnInv().getStack(i)==null,"PROVIDER_HAS_PENDING_OUTPUT");
        var structure=GtMultiblockAdapter.capture(machine,provider,captured,player);
        var data=new CompoundTag();data.putInt("schema",1);data.putString("dimension",level.dimension().location().toString());
        data.put("gtStructure",structure);data.putLong("machine",machine.getBlockPos().asLong());data.putLong("provider",provider.getBlockPos().asLong());
        data.putBoolean("network",true);data.putUUID("player",player.getUUID());
        var node=provider.getMainNode().getNode();data.putInt("owner",node.getOwningPlayerId());data.putDouble("idleAE",node.getIdlePowerUsage());
        data.put("flags",NetworkBlueprint.flags(node));data.putInt("priority",logic.getPriority());data.putBoolean("blocking",logic.isBlocking());
        var positions=new HashSet<>(members);positions.add(provider.getBlockPos());var blocks=new ListTag();
        for(var pos:positions) {
            if(level.getBlockState(pos).isAir())continue;
            var block=new CompoundTag();block.putLong("pos",pos.asLong());block.put("state",NbtUtils.writeBlockState(level.getBlockState(pos)));
            if(level.getBlockEntity(pos)!=null)block.put("data",PlatformData.saveBlock(level.getBlockEntity(pos)));blocks.add(block);
        }
        data.put("blocks",blocks);var patterns=new ListTag();
        for(var pattern:logic.getAvailablePatterns())patterns.add(PlatformData.saveItem(pattern.getDefinition().toStack(),level));
        data.put("patterns",patterns);return rebuild(level,data,()->false);
    }
    private static Compiled inspectPair(ServerLevel level,Player player,BlockPos first,BlockPos second,boolean network,Set<BlockPos> captured) {
        require(available(),"NATIVE_ADAPTER_UNAVAILABLE");
        long volume=((long)Math.abs(first.getX()-second.getX())+1)*((long)Math.abs(first.getY()-second.getY())+1)
                *((long)Math.abs(first.getZ()-second.getZ())+1);
        require(volume<=4096,"SCAN_LIMIT");
        PatternProviderBlockEntity provider=null;BlockEntity machine=null;
        var blocks=new ListTag();
        for(var p:BlockPos.betweenClosed(first,second)) {
            require(level.hasChunkAt(p),"CHUNK_UNLOADED");
            require(level.mayInteract(player,p),"PERMISSION_DENIED");
            var state=level.getBlockState(p);
            if(state.isAir() || state.is(MachinesMod.MARKER.get())) continue;
            var be=level.getBlockEntity(p);
            if(be!=null && be.getClass()==PatternProviderBlockEntity.class) {
                require(provider==null,"MULTIPLE_PROVIDERS_NOT_SUPPORTED");provider=(PatternProviderBlockEntity)be;
                require(appeng.util.Platform.hasPermissions(new appeng.api.util.DimensionalBlockPos(be),player),"AE2_PERMISSION_DENIED");
            } else if(be!=null && NativeMachines.supports(be)) {
                require(machine==null,"MULTIPLE_MACHINES_NOT_SUPPORTED");machine=be;
            } else throw new IllegalArgumentException("UNSUPPORTED_BLOCK: "+state);
            var saved=new CompoundTag();saved.putLong("pos",p.asLong());saved.put("state",NbtUtils.writeBlockState(state));
            saved.put("data",PlatformData.saveBlock(be));blocks.add(saved);
        }
        require(provider!=null && machine!=null,"PROVIDER_AND_MACHINE_REQUIRED");
        require(provider.getBlockPos().distManhattan(machine.getBlockPos())==1,"MACHINE_NOT_ADJACENT");
        var node=provider.getMainNode().getNode();
        require(node!=null && node.isActive(),"PROVIDER_OFFLINE");
        require(node.getGrid().getCraftingService().getCpus().stream().noneMatch(cpu->cpu.isBusy()),"ACTIVE_CRAFTING_CPU");
        var logic=provider.getLogic();
        require(!logic.isBusy(),"PROVIDER_HAS_PENDING_INPUT");
        require(logic.getConfigManager().getSetting(Settings.LOCK_CRAFTING_MODE)==LockCraftingMode.NONE,"CRAFTING_LOCK_NOT_SUPPORTED");
        var returns=logic.getReturnInv();
        for(int i=0;i<returns.size();i++) require(returns.getStack(i)==null,"PROVIDER_HAS_PENDING_OUTPUT");
        Direction machineToProvider=null;
        for(var direction:Direction.values()) if(machine.getBlockPos().relative(direction).equals(provider.getBlockPos())) machineToProvider=direction;
        boolean assembly=NativeAssemblers.supports(machine);
        boolean furnace=VanillaFurnaces.supports(machine);
        if(assembly) {
            var assembler=(appeng.blockentity.crafting.MolecularAssemblerBlockEntity)machine;
            NativeMachines.verifyPassiveBoundary(machine,captured);
            require(assembler.acceptsPlans(),"ASSEMBLER_HAS_FIXED_PATTERN");
            require(provider.getTargets().contains(machineToProvider.getOpposite()),"PROVIDER_POINTS_AWAY_FROM_ASSEMBLER");
        } else if(furnace) {
            require(provider.getTargets().contains(machineToProvider.getOpposite()),"PROVIDER_POINTS_AWAY_FROM_FURNACE");
        } else if(NativeMachines.mek() && MekanismAdapter.buffered(machine))MekanismAdapter.verifyIdleBoundary(machine,machineToProvider,captured);
        else NativeMachines.verifyIdleBoundary(machine,machineToProvider);
        // A different adjacent device can change native insertion/ejection routing and is not silently discarded.
        for(var direction:Direction.values()) {
            var neighbor=machine.getBlockPos().relative(direction);
            require(neighbor.equals(provider.getBlockPos()) || level.getBlockState(neighbor).isAir()
                    || level.getBlockState(neighbor).is(MachinesMod.MARKER.get())
                    || captured.contains(neighbor) && NetworkBlueprint.passive(level.getBlockEntity(neighbor)),"UNDECLARED_MACHINE_BOUNDARY");
        }
        var profiles=new LinkedHashMap<IPatternDetails,MachineProfile>();var patterns=new ListTag();
        for(var pattern:logic.getAvailablePatterns()) {
            if(assembly) {
                require(pattern instanceof appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern,"ASSEMBLER_PATTERN_UNSUPPORTED");
                patterns.add(PlatformData.saveItem(pattern.getDefinition().toStack(),level));continue;
            }
            if(furnace) {
                require(pattern.supportsPushInputsToExternalInventory(),"FURNACE_REQUIRES_PROCESSING_PATTERN");
                patterns.add(PlatformData.saveItem(pattern.getDefinition().toStack(),level));continue;
            }
            var inputs=PatternProgram.inputTotals(pattern);
            var profile=NativeMachines.inspect(machine,inputs).orElseThrow(()->new IllegalArgumentException("NATIVE_RECIPE_UNSUPPORTED"));
            NativeMachines.verifyRecipeRoute(machine,machineToProvider,profile);
            profiles.put(pattern,profile);patterns.add(PlatformData.saveItem(pattern.getDefinition().toStack(),level));
        }
        var program=new PatternProgram(profiles,()->false);
        require(!profiles.isEmpty() || NativeMachines.buffered(machine),"PASSIVE_NATIVE_EXECUTOR_UNSUPPORTED");
        var energy=profiles.isEmpty()?NativeMachines.idleProfile(machine):profiles.values().iterator().next();
        var data=new CompoundTag();data.putInt("schema",1);data.putString("dimension",level.dimension().location().toString());
        data.put("blocks",blocks);data.put("patterns",patterns);data.putLong("machine",machine.getBlockPos().asLong());
        data.putLong("provider",provider.getBlockPos().asLong());data.putBoolean("network",network);
        data.putBoolean("assemblyProvider",assembly);
        if(furnace)data.putBoolean("furnaceProvider",true);
        data.putInt("owner",node.getOwningPlayerId());data.putUUID("player",player.getUUID());
        data.putDouble("idleAE",node.getIdlePowerUsage());data.putInt("priority",logic.getPriority());
        data.putBoolean("blocking",logic.isBlocking());
        var flags=new ListTag();
        for(var flag:GridFlags.values()) if(node.hasFlag(flag)) flags.add(StringTag.valueOf(flag.name()));
        data.put("flags",flags);
        return new Compiled(data,program,energy);
    }
    public static void require(boolean condition,String reason) {
        if(!condition) throw new IllegalArgumentException(reason);
    }
}
