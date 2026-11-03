package com.syaru.ae2machines.block;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridConnection;
import java.math.BigInteger;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Physical parts only; factories retain their original journals, resources and execution paths. */
public final class ComputationStructure {
    public static final int MAX_UNITS = 32;
    private static final Map<Level, Map<BlockPos, Assembly>> ASSEMBLIES = new WeakHashMap<>();
    private ComputationStructure() {}
    private static Map<BlockPos, Assembly> assemblies(Level level) {
        return ASSEMBLIES.computeIfAbsent(level, ignored -> new HashMap<>());
    }
    public static boolean role(BaseAccelerator host, ComputationHostBlock.Role role) {
        return host.getBlockState().getBlock() instanceof ComputationHostBlock block && block.role() == role;
    }
    public static boolean modular(BaseAccelerator host) { return host.getBlockState().getBlock() instanceof ComputationHostBlock; }
    public static boolean controllerOnline(BaseAccelerator host) {
        if(!modular(host))return true;
        var assembly=owner(host.getLevel(),host.getBlockPos());
        return assembly!=null && assembly.valid && assembly.controller.getMainNode().isActive();
    }
    public static BlockPos position(BlockPos controller, Direction facing, int x, int y, int z) {
        return controller.relative(facing.getClockWise(), x - 1).above(y - 1).relative(facing.getOpposite(), z);
    }
    private static Assembly owner(Level level, BlockPos pos) {
        if (level == null) return null;
        for (var assembly : assemblies(level).values()) if (assembly.members.containsKey(pos)) return assembly;
        return null;
    }
    public static void changed(Level level, BlockPos pos) {
        if (level.isClientSide) return;
        var assembly = owner(level, pos);
        if (assembly != null) assembly.stop(level);
    }
    public static void detached(BaseAccelerator host) {
        if (host.getLevel() != null && !host.getLevel().isClientSide && modular(host)) changed(host.getLevel(), host.getBlockPos());
    }
    public static void stopped(MinecraftServer server) {
        ASSEMBLIES.keySet().removeIf(level -> level.getServer() == server);
    }
    public static boolean runnable(BaseAccelerator bay) {
        if (!modular(bay)) return true;
        var assembly = owner(bay.getLevel(), bay.getBlockPos());
        return role(bay, ComputationHostBlock.Role.BAY) && assembly != null && assembly.valid
                && bay.getBlockState().getValue(ComputationHostBlock.FORMED);
    }
    public static BaseAccelerator menuHost(BaseAccelerator selected, boolean empty) {
        if (!modular(selected) || role(selected, ComputationHostBlock.Role.BAY)) return selected;
        var assembly = owner(selected.getLevel(), selected.getBlockPos());
        if (assembly == null || !assembly.valid) return null;
        for (var bay : assembly.bays) if (empty ? !bay.hasFactory() : bay.hasFactory()) return bay;
        return empty ? null : assembly.bays.get(0);
    }
    public static boolean guiController(BaseAccelerator host) {
        return host!=null && role(host,ComputationHostBlock.Role.CONTROLLER) && !host.isRemoved();
    }
    public static List<BaseAccelerator> menuBays(BaseAccelerator controller) {
        if(!guiController(controller))return List.of();
        var assembly=owner(controller.getLevel(),controller.getBlockPos());
        return assembly!=null && assembly.valid && assembly.controller==controller?List.copyOf(assembly.bays):List.of();
    }
    public static boolean controls(BaseAccelerator controller,BaseAccelerator bay) {
        return guiController(controller) && menuBays(controller).contains(bay);
    }
    public static BaseAccelerator controller(BaseAccelerator bay){
        if(!modular(bay))return bay;
        var assembly=owner(bay.getLevel(),bay.getBlockPos());return assembly!=null && assembly.valid?assembly.controller:null;
    }
    /** The existing assembly is authoritative; incomplete structures expose physical parts only. */
    public static List<BlockPos> previewPositions(BaseAccelerator controller) {
        if(!guiController(controller) || controller.getLevel()==null)return List.of();
        var assembly=owner(controller.getLevel(),controller.getBlockPos());
        if(assembly!=null && assembly.valid && assembly.controller==controller)return List.copyOf(assembly.members.keySet());
        var result=new ArrayList<BlockPos>();var level=controller.getLevel();
        var facing=controller.getBlockState().getValue(AcceleratorBlock.FACING);
        for(int x=0;x<3;x++)for(int y=0;y<3;y++)for(int z=0;z<2;z++){
            var pos=position(controller.getBlockPos(),facing,x,y,z);
            if(level.hasChunkAt(pos) && computationBlock(level.getBlockState(pos)))result.add(pos);
        }
        for(int unit=1;unit<=MAX_UNITS+1;unit++){
            int before=result.size();boolean end=false;
            for(int y=0;y<3;y++)for(int z=0;z<2;z++){
                var pos=position(controller.getBlockPos(),facing,-unit,y,z);
                if(level.hasChunkAt(pos) && computationBlock(level.getBlockState(pos))){
                    result.add(pos);end|=part(level.getBlockState(pos),ComputationPartBlock.Kind.END);
                }
            }
            if(result.size()==before || end)break;
        }
        return List.copyOf(result);
    }
    private static boolean computationBlock(BlockState state) {
        return state.getBlock() instanceof ComputationHostBlock || state.getBlock() instanceof ComputationPartBlock || state.getBlock() instanceof EnergyPortBlock;
    }
    public static BaseAccelerator energyTarget(Level level, BlockPos port) {
        var assembly = owner(level, port);
        if (assembly == null || !assembly.valid) return null;
        var bay = assembly.power.get(port);
        return bay != null && runnable(bay) ? bay : null;
    }
    public static int units(BaseAccelerator host) {
        var assembly = owner(host.getLevel(), host.getBlockPos());
        return assembly == null || !assembly.valid ? 0 : assembly.bays.size();
    }
    public static int maximumPower(BaseAccelerator bay, boolean speed) {
        if (!modular(bay)) return 3400;
        var assembly = owner(bay.getLevel(), bay.getBlockPos());
        var powers = assembly == null || !assembly.valid ? null : assembly.limits.get(bay);
        return powers == null ? 0 : powers[speed ? 0 : 1];
    }
    public static boolean accepts(BaseAccelerator bay, BigInteger speed, BigInteger lanes) {
        if (!modular(bay)) return speed.signum() > 0 && lanes.signum() > 0;
        return runnable(bay) && speed.signum() > 0 && lanes.signum() > 0
                && speed.compareTo(BigInteger.ONE.shiftLeft(maximumPower(bay, true))) <= 0
                && lanes.compareTo(BigInteger.ONE.shiftLeft(maximumPower(bay, false))) <= 0;
    }
    public static void tickController(BaseAccelerator controller) {
        var level = controller.getLevel();
        if (level == null || level.isClientSide) return;
        var map = assemblies(level);
        var previous = map.get(controller.getBlockPos());
        if (previous == null || !previous.valid || level.getGameTime() % 20 == 0) {
            var candidate = scan(controller);
            if (candidate == null) {
                if (previous != null) previous.stop(level);
                update(level, controller.getBlockPos(), false, null);
                return;
            }
            if (previous == null || !previous.valid || !previous.members.equals(candidate.members)) {
                if (previous != null) previous.stop(level);
                for (var other : map.values()) if (other != previous && other.valid
                        && candidate.members.keySet().stream().anyMatch(other.members::containsKey)) {
                    update(level, controller.getBlockPos(), false, null);
                    return;
                }
                candidate.valid = true;
                map.put(controller.getBlockPos(), candidate);
                previous = candidate;
                for (var entry : candidate.members.entrySet()) update(level, entry.getKey(), true, entry.getValue());
            }
        }
        previous.connect();
        boolean active = previous.bays.stream().anyMatch(BaseAccelerator::factoryOnline);
        var state = controller.getBlockState();
        if (state.getValue(AcceleratorBlock.ACTIVE) != active)
            level.setBlock(controller.getBlockPos(), state.setValue(AcceleratorBlock.ACTIVE, active), 2);
    }
    private static Assembly scan(BaseAccelerator controller) {
        var level = controller.getLevel();
        var facing = controller.getBlockState().getValue(AcceleratorBlock.FACING);
        var center = controller.getBlockPos();
        var result = new Assembly();
        result.controller = controller;
        for (int x = 0; x < 3; x++) for (int y = 0; y < 3; y++) for (int z = 0; z < 2; z++) {
            var p = position(center, facing, x, y, z);
            if (!level.hasChunkAt(p)) return null;
            var state = level.getBlockState(p);
            if (x == 1 && y == 1 && z == 0) {
                if (!host(state, ComputationHostBlock.Role.CONTROLLER)) return null;
            } else if (x == 2 && y == 1 && z == 1) {
                if (!host(state, ComputationHostBlock.Role.CHANNEL) || !(level.getBlockEntity(p) instanceof BaseAccelerator channel)) return null;
                result.channel = channel;
            } else if (!part(state, ComputationPartBlock.Kind.CASING)) return null;
            result.members.put(p, host(state, ComputationHostBlock.Role.CHANNEL) ? facing.getOpposite() : facing);
        }
        for (int unit = 1; unit <= MAX_UNITS + 1; unit++) {
            var end = position(center, facing, -unit, 1, 0);
            if (!level.hasChunkAt(end)) return null;
            if (part(level.getBlockState(end), ComputationPartBlock.Kind.END)) {
                if (unit == 1) return null;
                for (int y = 0; y < 3; y++) for (int z = 0; z < 2; z++) {
                    var p = position(center, facing, -unit, y, z);
                    if (!level.hasChunkAt(p) || !(y == 1 && z == 0 ? part(level.getBlockState(p), ComputationPartBlock.Kind.END)
                            : part(level.getBlockState(p), ComputationPartBlock.Kind.CASING))) return null;
                    result.members.put(p, facing);
                }
                return result;
            }
            if (unit > MAX_UNITS) return null;
            BaseAccelerator bay = null;
            BlockPos power = null;
            int speedPower = 0, lanePower = 0;
            for (int y = 0; y < 3; y++) for (int z = 0; z < 2; z++) {
                var p = position(center, facing, -unit, y, z);
                if (!level.hasChunkAt(p)) return null;
                var state = level.getBlockState(p);
                if (z == 0 && y == 2) {
                    if (!host(state, ComputationHostBlock.Role.BAY) || !(level.getBlockEntity(p) instanceof BaseAccelerator found)) return null;
                    bay = found;
                } else if (y == 1) {
                    if (!part(state, ComputationPartBlock.Kind.COOLING)) return null;
                } else if (z == 0) {
                    if (!part(state, ComputationPartBlock.Kind.PARALLEL)) return null;
                    lanePower = ((ComputationPartBlock)state.getBlock()).maximumPower();
                } else if (y == 2) {
                    if (!part(state, ComputationPartBlock.Kind.SPEED)) return null;
                    speedPower = ((ComputationPartBlock)state.getBlock()).maximumPower();
                } else {
                    if (!state.is(com.syaru.ae2machines.platform.MachinesMod.COMPUTATION_POWER.get())
                            && !state.is(com.syaru.ae2machines.platform.MachinesMod.ENERGY.get())) return null;
                    power = p;
                }
                result.members.put(p, z == 0 ? facing : facing.getOpposite());
            }
            result.bays.add(Objects.requireNonNull(bay));
            result.power.put(Objects.requireNonNull(power), bay);
            result.limits.put(bay, new int[]{speedPower, lanePower});
        }
        return null;
    }
    private static boolean host(BlockState state, ComputationHostBlock.Role role) {
        return state.getBlock() instanceof ComputationHostBlock b && b.role() == role;
    }
    private static boolean part(BlockState state, ComputationPartBlock.Kind kind) {
        return state.getBlock() instanceof ComputationPartBlock b && b.kind() == kind;
    }
    private static void update(Level level, BlockPos pos, boolean formed, Direction facing) {
        if (!level.hasChunkAt(pos)) return;
        var state = level.getBlockState(pos);
        if (!state.hasProperty(ComputationHostBlock.FORMED)) return;
        var next = state.setValue(ComputationHostBlock.FORMED, formed);
        if (facing != null && state.hasProperty(AcceleratorBlock.FACING)) next = next.setValue(AcceleratorBlock.FACING, facing);
        if (!formed && state.hasProperty(AcceleratorBlock.ACTIVE)) next = next.setValue(AcceleratorBlock.ACTIVE, false);
        if (state != next) {
            level.setBlock(pos, next, 2);
            if (facing != null && level.getBlockEntity(pos) instanceof BaseAccelerator host
                    && role(host, ComputationHostBlock.Role.CHANNEL))
                host.getMainNode().setExposedOnSides(EnumSet.of(facing));
        }
    }
    private static final class Assembly {
        final Map<BlockPos, Direction> members = new LinkedHashMap<>();
        final List<BaseAccelerator> bays = new ArrayList<>();
        final Map<BlockPos, BaseAccelerator> power = new HashMap<>();
        final Map<BaseAccelerator, int[]> limits = new IdentityHashMap<>();
        final Map<BaseAccelerator, IGridConnection> connections = new IdentityHashMap<>();
        BaseAccelerator channel;
        BaseAccelerator controller;
        boolean valid;
        void connect() {
            var node = channel.getMainNode().getNode();
            if (node == null) return;
            if (!connections.containsKey(controller) && controller.getMainNode().getNode() != null)
                connections.put(controller, GridHelper.createConnection(node, controller.getMainNode().getNode()));
            for (var bay : bays) if (!connections.containsKey(bay) && bay.getMainNode().getNode() != null)
                connections.put(bay, GridHelper.createConnection(node, bay.getMainNode().getNode()));
        }
        void stop(Level level) {
            if (!valid) return;
            valid = false;
            for (var connection : connections.values()) connection.destroy();
            connections.clear();
            for (var bay : bays) if (!bay.isRemoved() && bay.factoryHandle() != null) bay.suspendFactory();
            for (var pos : members.keySet()) update(level, pos, false, null);
        }
    }
}
