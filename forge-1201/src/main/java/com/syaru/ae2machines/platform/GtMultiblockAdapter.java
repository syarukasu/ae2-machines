package com.syaru.ae2machines.platform;

import appeng.api.stacks.*;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.misc.InterfaceBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.*;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.DistillationTowerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.part.*;
import com.gregtechceu.gtceu.common.machine.multiblock.part.*;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.recipe.*;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.syaru.ae2machines.runtime.*;
import com.syaru.ae2machines.engine.Recipe;
import com.syaru.ae2machines.world.NativeCellWorlds;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import java.math.BigInteger;
import java.util.*;
import static com.syaru.ae2machines.world.NativeFactoryCompiler.require;

/** Formed native controllers and copied hatch handlers. Probes never join world tick subscriptions. */
public final class GtMultiblockAdapter {
    private GtMultiblockAdapter() {}
    private static Object machine(BlockEntity be) { return be instanceof com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity holder?holder.getMetaMachine():null; }
    public static boolean supports(BlockEntity be) {
        var m=machine(be);
        return m!=null && (m.getClass()==WorkableElectricMultiblockMachine.class
                || m.getClass()==CoilWorkableElectricMultiblockMachine.class
                || m.getClass()==DistillationTowerMachine.class);
    }
    private static WorkableElectricMultiblockMachine controller(BlockEntity be) { return (WorkableElectricMultiblockMachine)machine(be); }
    private static BlockEntity block(com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart part) { return (BlockEntity)part.self().getHolder(); }
    public static Set<BlockPos> members(BlockEntity be) {
        var m=controller(be);require(m.isFormed() && !m.getMultiblockState().hasError(),"GT_STRUCTURE_NOT_FORMED");
        var result=new HashSet<BlockPos>();result.add(be.getBlockPos());
        for(long p:m.getMultiblockState().cache)result.add(BlockPos.of(p));
        return Set.copyOf(result);
    }
    public static boolean reaches(BlockEntity be,BlockPos provider) {
        return controller(be).getParts().stream().anyMatch(part->part.self() instanceof TieredIOPartMachine io && io(io)==IO.IN
                && block(part).getBlockPos().relative(io.getFrontFacing()).equals(provider));
    }
    public static Set<BlockPos> worldProviders(BlockEntity be) {
        var result=new HashSet<BlockPos>();
        for(var part:controller(be).getParts())if(part.self() instanceof TieredIOPartMachine io && io(io)==IO.IN)
            result.add(block(part).getBlockPos().relative(io.getFrontFacing()));
        return Set.copyOf(result);
    }
    public static Set<BlockPos> worldOwnedBlocks(BlockEntity be) {
        var result=new HashSet<BlockPos>();result.add(be.getBlockPos());
        for(var part:controller(be).getParts())result.add(block(part).getBlockPos());
        return Set.copyOf(result);
    }
    public static boolean worldReaches(BlockEntity be,PatternProviderBlockEntity provider) {
        return controller(be).getParts().stream().anyMatch(p->p.self() instanceof TieredIOPartMachine io && io(io)==IO.IN
                && block(p).getBlockPos().relative(io.getFrontFacing()).equals(provider.getBlockPos())
                && provider.getTargets().contains(io.getFrontFacing().getOpposite()));
    }
    public static CompoundTag capture(BlockEntity be,PatternProviderBlockEntity provider,Set<BlockPos> captured,Player player) {
        var m=controller(be);members(be);
        require(m.getRecipeLogic().isIdle() && m.getRecipeLogic().getProgress()==0 && m.getRecipeLogic().isWorkingEnabled(),"GT_MACHINE_NOT_IDLE");
        require(!m.isGenerator() && m.getCoverContainer().getCovers().isEmpty(),"GT_CONTROLLER_UNSUPPORTED");
        var data=new CompoundTag();data.put("controller",save(be));data.putInt("tier",m.getTier());
        if(m instanceof CoilWorkableElectricMultiblockMachine coil) {
            var state=members(be).stream().map(be.getLevel()::getBlockState)
                    .filter(s->s.getBlock() instanceof com.gregtechceu.gtceu.common.block.CoilBlock c && c.coilType==coil.getCoilType()).findFirst().orElseThrow();
            data.put("coilState",NbtUtils.writeBlockState(state));
        }
        var parts=new ListTag();var returns=new ListTag();int power=0;long voltage=0;
        for(var part:m.getParts()) {
            var b=block(part);var actual=part.self();
            if(provider==null && NativeCellWorlds.owns(be.getLevel()))
                require(b.getLevel()==be.getLevel() && be.getLevel().getBlockEntity(b.getBlockPos())==b,"GT_STALE_NATIVE_PART");
            require(Set.of(ItemBusPartMachine.class,FluidHatchPartMachine.class,EnergyHatchPartMachine.class,AutoMaintenanceHatchPartMachine.class,MufflerPartMachine.class).contains(actual.getClass()),"GT_PART_UNSUPPORTED: "+actual.getClass().getSimpleName());
            if(actual instanceof MufflerPartMachine muffler)require(muffler.isFrontFaceFree(),"GT_MUFFLER_OBSTRUCTED");
            require(part.getControllers().size()==1 && actual.getCoverContainer().getCovers().isEmpty(),"GT_SHARED_OR_COVERED_HATCH");
            require(m.getMultiblockState().getMatchContext().<it.unimi.dsi.fastutil.longs.Long2ObjectMap<IO>>getOrCreate("ioMap",it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap::new).getOrDefault(b.getBlockPos().asLong(),IO.BOTH)==IO.BOTH,"GT_RESTRICTED_STRUCTURE_IO");
            if(actual instanceof ItemBusPartMachine bus)require(!bus.isDistinct() && bus.getInventory().isEmpty(),"GT_ITEM_BUS_NOT_EMPTY_OR_DISTINCT");
            if(actual instanceof FluidHatchPartMachine hatch)require(hatch.tank.isEmpty(),"GT_FLUID_HATCH_NOT_EMPTY");
            if(actual instanceof EnergyHatchPartMachine hatch) {
                require(io(hatch)==IO.IN && hatch.isWorkingEnabled(),"GT_ENERGY_HATCH_DISABLED");
                long next=hatch.energyContainer.getInputVoltage();
                require(power==0 || voltage==next,"GT_MIXED_VOLTAGE_HATCHES");voltage=next;power++;
            }
            if(actual instanceof TieredIOPartMachine io && (actual instanceof ItemBusPartMachine || actual instanceof FluidHatchPartMachine)) {
                require(io.isWorkingEnabled(),"GT_HATCH_DISABLED");var neighbor=b.getBlockPos().relative(io.getFrontFacing());
                if(provider==null) {
                    require(captured.contains(b.getBlockPos()),"GT_PART_OUTSIDE_CELL");
                } else if(io(io)==IO.IN)require(neighbor.equals(provider.getBlockPos()),"GT_INPUT_ROUTE_MUST_REACH_PROVIDER");
                else {
                    require(io(io)==IO.OUT && !captured.contains(neighbor),"GT_OUTPUT_ROUTE_UNSUPPORTED");
                    require(be.getLevel().getBlockEntity(neighbor) instanceof InterfaceBlockEntity,"GT_OUTPUT_INTERFACE_REQUIRED");
                    var target=(InterfaceBlockEntity)be.getLevel().getBlockEntity(neighbor);
                    require(target.getMainNode().isActive() && target.getMainNode().getGrid()==provider.getMainNode().getGrid(),"GT_OUTPUT_GRID_MISMATCH");
                    require(appeng.util.Platform.hasPermissions(new appeng.api.util.DimensionalBlockPos(target),player),"AE2_PERMISSION_DENIED");
                    returns.add(LongTag.valueOf(neighbor.asLong()));
                }
            }
            parts.add(save(b));
        }
        require(power>0,"GT_INPUT_ENERGY_HATCH_REQUIRED");
        if(provider!=null)require(!returns.isEmpty(),"GT_OUTPUT_INTERFACE_REQUIRED");
        if(m instanceof DistillationTowerMachine tower) {
            var layers=new ListTag();
            for(var handler:tower.getFluidOutputs()) {
                var hatch=m.getParts().stream().filter(part->part.self() instanceof FluidHatchPartMachine f && f.tank==handler).findFirst();
                require(hatch.isPresent(),"GT_VOID_DISTILLATION_LAYER");
                layers.add(LongTag.valueOf(block(hatch.orElseThrow()).getBlockPos().asLong()));
            }
            require(!layers.isEmpty(),"GT_DISTILLATION_LAYERS_MISSING");data.put("fluidLayers",layers);
        }
        data.put("parts",parts);data.put("returns",returns);return data;
    }
    public static CompoundTag worldSnapshot(BlockEntity be) {
        require(be.getLevel() instanceof ServerLevel level && NativeCellWorlds.owns(level),"GT_NOT_NATIVE_CELL");
        return worldLocked(be,()->capture(be,null,members(be),null));
    }
    public static <T> T worldLocked(BlockEntity be,java.util.function.Supplier<T> action) {
        var lock=controller(be).getPatternLock();lock.lock();
        try{return action.get();}finally{lock.unlock();}
    }
    public static void prepareWorld(BlockEntity be) {
        var m=controller(be);
        var lock=m.getPatternLock();lock.lock();
        try {
            if((!m.isFormed() || m.getParts().isEmpty()) && m.checkPattern()) {
                m.onStructureFormed();
                com.gregtechceu.gtceu.api.pattern.MultiblockWorldSavedData.getOrCreate((ServerLevel)be.getLevel()).addMapping(m.getMultiblockState());
            }
        } finally {lock.unlock();}
    }
    public static boolean formedWorld(BlockEntity be){return controller(be).isFormed() && !controller(be).getParts().isEmpty();}
    public static List<Object> worldConfiguration(BlockEntity be) {
        var m=controller(be);
        require(m.isFormed() && m.isWorkingEnabled(),"GT_NATIVE_STRUCTURE_OFFLINE");
        var result=new ArrayList<Object>();result.add(m.getTier());result.add(m.getFrontFacing());
        if(m instanceof CoilWorkableElectricMultiblockMachine coil)result.add(coil.getCoilType());
        for(var part:m.getParts()) {
            var p=part.self();require(p.getCoverContainer().getCovers().isEmpty(),"GT_NATIVE_COVER_CHANGED");
            if(p instanceof TieredIOPartMachine io)require(io.isWorkingEnabled(),"GT_NATIVE_HATCH_DISABLED");
            result.add(List.of(block(part).getBlockPos(),block(part).getBlockState()));
            if(p instanceof ItemBusPartMachine bus && io(bus)==IO.IN)result.add(com.gregtechceu.gtceu.common.item.IntCircuitBehaviour.getCircuitConfiguration(bus.getCircuitInventory().getStackInSlot(0)));
        }
        return List.copyOf(result);
    }
    public static boolean worldIdle(BlockEntity be) {
        try {worldSnapshot(be);return true;}catch(IllegalArgumentException unavailable){return false;}
    }
    public static MachineProfile worldIdleProfile(BlockEntity be) {
        var capacity=BigInteger.ZERO;var stored=BigInteger.ZERO;var rate=BigInteger.ZERO;var amps=BigInteger.ZERO;long voltage=0;
        for(var part:controller(be).getParts())if(part.self() instanceof EnergyHatchPartMachine hatch && io(hatch)==IO.IN) {
            var e=hatch.energyContainer;
            capacity=capacity.add(BigInteger.valueOf(e.getEnergyCapacity()));stored=stored.add(BigInteger.valueOf(e.getEnergyStored()));
            rate=rate.add(BigInteger.valueOf(e.getInputVoltage()).multiply(BigInteger.valueOf(e.getInputAmperage())));
            amps=amps.add(BigInteger.valueOf(e.getInputAmperage()));voltage=Math.max(voltage,e.getInputVoltage());
        }
        return new MachineProfile(null,capacity,rate,stored,BigInteger.ONE,BigInteger.ONE,BigInteger.ONE,
                voltage,amps.min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact(),MachineProfile.GT_UNIT);
    }
    public static BigInteger worldEnergy(BlockEntity be) {return worldIdleProfile(be).stored();}
    public static Optional<MachineProfile> inspectWorld(BlockEntity be,Map<AEKey,BigInteger> inputs) {
        return worldLocked(be,()->{
        var data=capture(be,null,members(be),null);
        return inspect((ServerLevel)be.getLevel(),data,inputs).map(p->{
            var actual=worldIdleProfile(be);
            return new MachineProfile(p.recipe(),actual.capacity(),actual.inputRate(),actual.stored(),
                    p.unitsPerFE(),p.nativeQuantum(),p.parallelism(),actual.voltage(),actual.amperage(),MachineProfile.GT_UNIT);
        });
        });
    }
    public static void worldEnergy(BlockEntity be,BigInteger amount) {
        require(amount.signum()>=0 && amount.compareTo(worldIdleProfile(be).capacity())<=0,"GT_INVALID_NATIVE_ENERGY");
        var difference=amount.subtract(worldEnergy(be));var left=difference.abs();
        var hatches=controller(be).getParts().stream().map(p->p.self())
                .filter(EnergyHatchPartMachine.class::isInstance).map(EnergyHatchPartMachine.class::cast)
                .filter(h->io(h)==IO.IN).toList();
        for(var hatch:hatches) {
            var e=hatch.energyContainer;
            var room=BigInteger.valueOf(difference.signum()<0?e.getEnergyStored():e.getEnergyCapacity()-e.getEnergyStored());
            var share=left.min(room);
            if(share.signum()>0){e.changeEnergy(difference.signum()<0?-share.longValueExact():share.longValueExact());block(hatch).setChanged();}
            left=left.subtract(share);
        }
        require(left.signum()==0 && worldEnergy(be).equals(amount),"GT_NATIVE_ENERGY_CHANGED");
    }
    public static com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine worldMachine(BlockEntity be) {return controller(be);}
    private static IO io(TieredIOPartMachine part) { return part.getRecipeHandlers().get(0).getHandlerIO(); }
    private static CompoundTag save(BlockEntity be) {
        var tag=new CompoundTag();tag.putLong("pos",be.getBlockPos().asLong());tag.put("state",NbtUtils.writeBlockState(be.getBlockState()));tag.put("data",PlatformData.saveBlock(be));return tag;
    }
    private static BlockEntity load(CompoundTag saved,ServerLevel level) {
        var state=NbtUtils.readBlockState(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK),saved.getCompound("state"));
        var be=((EntityBlock)state.getBlock()).newBlockEntity(BlockPos.of(saved.getLong("pos")),state);
        require(be!=null,"GT_PART_MISSING");be.setLevel(level);PlatformData.loadBlock(be,saved.getCompound("data"));return be;
    }
    static WorkableElectricMultiblockMachine probe(ServerLevel level,CompoundTag data,Map<AEKey,BigInteger> inputs) {
        var m=controller(load(data.getCompound("controller"),level));
        var parts=m.getParts();parts.clear();
        for(var value:data.getList("parts",Tag.TAG_COMPOUND)) {
            var b=load((CompoundTag)value,level);var part=(com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart)machine(b);parts.add(part);
            for(var handlers:part.getRecipeHandlers())m.addHandlerList(handlers);
        }
        // Native part positions and tier are derived runtime fields, not persisted controller configuration.
        try {
            var update=MultiblockControllerMachine.class.getDeclaredMethod("updatePartPositions");update.setAccessible(true);update.invoke(m);
            var tier=WorkableElectricMultiblockMachine.class.getDeclaredField("tier");tier.setAccessible(true);tier.setInt(m,data.getInt("tier"));
            if(m instanceof CoilWorkableElectricMultiblockMachine) {
                var state=NbtUtils.readBlockState(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK),data.getCompound("coilState"));
                require(state.getBlock() instanceof com.gregtechceu.gtceu.common.block.CoilBlock,"GT_COIL_MISSING");
                var coil=((com.gregtechceu.gtceu.common.block.CoilBlock)state.getBlock()).coilType;
                var field=CoilWorkableElectricMultiblockMachine.class.getDeclaredField("coilType");field.setAccessible(true);field.set(m,coil);
                // Forge derives coil tier directly from the native coil type.
            }
            if(m instanceof DistillationTowerMachine) {
                var outputs=new ArrayList<IFluidHandler>();
                for(var value:data.getList("fluidLayers",Tag.TAG_LONG)) {
                    long pos=((LongTag)value).getAsLong();
                    var part=parts.stream().filter(p->block(p).getBlockPos().asLong()==pos).findFirst().orElseThrow();
                    require(part.self() instanceof FluidHatchPartMachine,"GT_DISTILLATION_HATCH_MISSING");
                    outputs.add(((FluidHatchPartMachine)part.self()).tank);
                }
                require(!outputs.isEmpty(),"GT_DISTILLATION_LAYERS_MISSING");
                var field=DistillationTowerMachine.class.getDeclaredField("fluidOutputs");field.setAccessible(true);field.set(m,outputs);
                field=DistillationTowerMachine.class.getDeclaredField("firstValid");field.setAccessible(true);field.set(m,outputs.get(0));
            }
        } catch(ReflectiveOperationException e){throw new IllegalStateException("GT_MULTIBLOCK_API_CHANGED",e);}
        for(var input:inputs.entrySet()) {
            int amount=input.getValue().intValueExact();
            if(input.getKey() instanceof AEItemKey key) {
                var left=key.toStack(amount);
                for(var handler:m.getCapabilitiesFlat(IO.IN,ItemRecipeCapability.CAP))if(handler.shouldSearchContent() && handler instanceof NotifiableItemStackHandler inventory)
                    for(int slot=0;slot<inventory.getSlots() && !left.isEmpty();slot++)left=inventory.insertItem(slot,left,false);
                require(left.isEmpty(),"GT_NATIVE_INPUT_CAPACITY");
            } else if(input.getKey() instanceof AEFluidKey key) {
                var fluid=new FluidStack(key.getFluid(),amount,key.getTag());
                for(var handler:m.getCapabilitiesFlat(IO.IN,FluidRecipeCapability.CAP))if(handler instanceof NotifiableFluidTank tank) {
                    int n=tank.fill(fluid,IFluidHandler.FluidAction.EXECUTE);fluid.shrink(n);if(fluid.isEmpty())break;
                }
                require(fluid.isEmpty(),"GT_NATIVE_FLUID_CAPACITY");
            } else throw new IllegalArgumentException("GT_UNSUPPORTED_MATERIAL");
        }
        return m;
    }
    public static Optional<MachineProfile> inspect(ServerLevel level,CompoundTag data,Map<AEKey,BigInteger> inputs) {
        var m=probe(level,data,inputs);var candidates=m.getRecipeType().searchRecipe(m,r->RecipeHelper.matchRecipe(m,r).isSuccess());
        var original=candidates.hasNext()?candidates.next():null;
        if(original==null || !GregTechAdapter.deterministic(original))return Optional.empty();
        // Obstruction is a live work condition, not a recipe identity change after reload.
        // This exact native part only tests its front face in modifyRecipe; completion.online
        // applies that native test to every work/payment/delivery boundary.
        var parts=new ArrayList<>(m.getParts());
        GTRecipe modified;
        try {
            m.getParts().removeIf(p->machine(block(p)) instanceof MufflerPartMachine);
            modified=m.fullModifyRecipe(original.copy());
        } finally {m.getParts().clear();m.getParts().addAll(parts);}
        if(modified==null || !GregTechAdapter.deterministic(modified) || modified.duration<=0)return Optional.empty();
        if(!RecipeHelper.handleRecipeIO(m,original,IO.IN,new HashMap<>()).isSuccess() || !contents(m,IO.IN).isEmpty())return Optional.empty();
        if(!output(m,original))return Optional.empty();
        var outputs=contents(m,IO.OUT);if(outputs.isEmpty())return Optional.empty();
        var nativeOutputs=new LinkedHashMap<AEKey,BigInteger>();
        for(var stack:RecipeHelper.getOutputItems(modified))add(nativeOutputs,AEItemKey.of(stack),stack.getCount());
        for(var stack:RecipeHelper.getOutputFluids(modified))add(nativeOutputs,AEFluidKey.of(stack),stack.getAmount());
        if(!nativeOutputs.keySet().equals(outputs.keySet()))return Optional.empty();
        BigInteger parallel=null;
        for(var entry:outputs.entrySet()) {
            var ratio=nativeOutputs.get(entry.getKey()).divideAndRemainder(entry.getValue());
            if(ratio[1].signum()!=0 || ratio[0].signum()<=0 || parallel!=null && !parallel.equals(ratio[0]))return Optional.empty();
            parallel=ratio[0];
        }
        if(!BigInteger.ONE.equals(parallel) && m.getParts().stream().anyMatch(p->machine(block(p)) instanceof MufflerPartMachine))return Optional.empty();
        var expanded=new LinkedHashMap<AEKey,BigInteger>();var factor=parallel;inputs.forEach((k,n)->expanded.put(k,n.multiply(factor)));
        var check=probe(level,data,expanded);
        if(!RecipeHelper.handleRecipeIO(check,modified,IO.IN,new HashMap<>()).isSuccess() || !contents(check,IO.IN).isEmpty()
                || !output(check,modified) || !contents(check,IO.OUT).equals(nativeOutputs))return Optional.empty();
        var eu=modified.getInputEUt();var cost=BigInteger.valueOf(eu.voltage()).multiply(BigInteger.valueOf(eu.amperage())).divideAndRemainder(parallel);
        if(cost[0].signum()<=0 || cost[1].signum()!=0)return Optional.empty();
        var energy=m.getEnergyContainer();
        // GT can overclock using multiple input amperes. EU/t is not the voltage of one input packet.
        // The native modifier above validated this exact captured hatch set; retain its packet tier.
        var recipe=new Recipe<AEKey>(original.getId().toString(),inputs,outputs,BigInteger.valueOf(modified.duration),cost[0],MachineProfile.GT_UNIT,BigInteger.valueOf(energy.getHighestInputVoltage()));
        return Optional.of(new MachineProfile(recipe,BigInteger.valueOf(energy.getEnergyCapacity()),
                BigInteger.valueOf(energy.getInputVoltage()).multiply(BigInteger.valueOf(energy.getInputAmperage())),BigInteger.valueOf(energy.getEnergyStored()),
                BigInteger.ONE,BigInteger.ONE,parallel,energy.getHighestInputVoltage(),energy.getInputAmperage()));
    }
    static boolean output(WorkableElectricMultiblockMachine machine,GTRecipe recipe) {
        if(!(machine instanceof DistillationTowerMachine tower))return RecipeHelper.handleRecipeIO(machine,recipe,IO.OUT,new HashMap<>()).isSuccess();
        // Native towers may silently void missing layers. Never advertise those as delivered outputs.
        if(recipe.getOutputContents(FluidRecipeCapability.CAP).size()>tower.getFluidOutputs().size())return false;
        try {
            var method=DistillationTowerMachine.DistillationTowerLogic.class.getDeclaredMethod("handleRecipeIO",GTRecipe.class,IO.class);
            method.setAccessible(true);return ((ActionResult)method.invoke(tower.getRecipeLogic(),recipe,IO.OUT)).isSuccess();
        } catch(ReflectiveOperationException e){throw new IllegalStateException("GT_DISTILLATION_API_CHANGED",e);}
    }
    private static Map<AEKey,BigInteger> contents(WorkableElectricMultiblockMachine m,IO io) {
        var result=new LinkedHashMap<AEKey,BigInteger>();
        for(var handler:m.getCapabilitiesFlat(io,ItemRecipeCapability.CAP))if(handler.shouldSearchContent())for(var value:handler.getContents()) {
            if(value instanceof net.minecraft.world.item.ItemStack stack && !stack.isEmpty())add(result,AEItemKey.of(stack),stack.getCount());
        }
        for(var handler:m.getCapabilitiesFlat(io,FluidRecipeCapability.CAP))for(var value:handler.getContents()) {
            if(value instanceof FluidStack stack && !stack.isEmpty())add(result,AEFluidKey.of(stack),stack.getAmount());
        }
        return Map.copyOf(result);
    }
    private static void add(Map<AEKey,BigInteger> target,AEKey key,long amount) {
        require(key!=null && amount>0,"GT_INVALID_NATIVE_OUTPUT");target.merge(key,BigInteger.valueOf(amount),BigInteger::add);
    }
    public static NativeCompletion completion(ServerLevel level,CompoundTag original,CompoundTag saved) {
        var structure=original.copy();
        // Only owned muffler inventories are restored here; energy hatches have a separate ledger.
        var updates=saved.getList("mufflers",Tag.TAG_COMPOUND);
        for(var update:updates) {
            var tag=(CompoundTag)update;
            var part=structure.getList("parts",Tag.TAG_COMPOUND).stream().map(CompoundTag.class::cast)
                    .filter(p->p.getLong("pos")==tag.getLong("pos")).findFirst().orElseThrow();
            require(machine(load(part,level)) instanceof MufflerPartMachine,"INVALID_MUFFLER_CHECKPOINT");
            part.put("data",tag.getCompound("data").copy());
        }
        var controller=probe(level,structure,Map.of());
        return completion(controller,level,saved);
    }
    public static NativeCompletion worldCompletion(BlockEntity be,CompoundTag saved) {
        var nativeCompletion=completion(controller(be),(ServerLevel)be.getLevel(),saved);if(nativeCompletion==null)return null;
        var parts=List.copyOf(controller(be).getParts());
        return new NativeCompletion() {
            public boolean online(){return be.getLevel().getBlockEntity(be.getBlockPos())==be && controller(be).isFormed()
                    && parts.equals(controller(be).getParts()) && parts.stream().allMatch(p->be.getLevel().getBlockEntity(block(p).getBlockPos())==block(p))
                    && nativeCompletion.online();}
            public boolean ready(UUID id,BigInteger count){return nativeCompletion.ready(id,count);}
            public boolean tick(ProcessingRuntime runtime){
                return worldLocked(be,()->{
                    if(!nativeCompletion.tick(runtime))return false;
                    be.setChanged();for(var part:parts)block(part).setChanged();return true;
                });
            }
            public CompoundTag save(){return nativeCompletion.save();}
            public void restoreTo(CompoundTag lane){nativeCompletion.restoreTo(lane);}
        };
    }
    private static NativeCompletion completion(WorkableElectricMultiblockMachine controller,ServerLevel level,CompoundTag saved) {
        var mufflers=controller.getParts().stream().map(p->machine(block(p)))
                .filter(MufflerPartMachine.class::isInstance).map(MufflerPartMachine.class::cast).toList();
        if(mufflers.isEmpty())return null;
        var applied=new HashMap<UUID,BigInteger>();
        for(var value:saved.getList("applied",Tag.TAG_COMPOUND)) {
            var tag=(CompoundTag)value;var n=new BigInteger(tag.getByteArray("count"));
            require(n.signum()>=0 && applied.put(tag.getUUID("id"),n)==null,"INVALID_MUFFLER_COMPLETION");
        }
        return new NativeCompletion() {
            public boolean online() {
                return mufflers.stream().allMatch(m->level.hasChunkAt(block(m).getBlockPos().relative(m.getFrontFacing())) && m.isFrontFaceFree());
            }
            public boolean ready(UUID id,BigInteger completed) {
                return applied.getOrDefault(id,BigInteger.ZERO).equals(completed);
            }
            public boolean tick(ProcessingRuntime runtime) {
                boolean changed=false;int budget=64;
                var live=new HashSet<UUID>();
                for(var job:runtime.snapshots()) {
                    live.add(job.id());var done=applied.getOrDefault(job.id(),BigInteger.ZERO);
                    require(done.compareTo(job.completed())<=0,"MUFFLER_COMPLETION_EXCEEDS_WORK");
                    // Native random recovery and environmental effects must run, not be approximated.
                    while(done.compareTo(job.completed())<0 && budget>0) {
                        for(var muffler:mufflers)muffler.afterWorking(controller);
                        done=done.add(BigInteger.ONE);applied.put(job.id(),done);changed=true;budget--;
                    }
                }
                changed|=applied.keySet().removeIf(id->!live.contains(id));
                return changed;
            }
            public CompoundTag save() {
                var tag=new CompoundTag();var parts=new ListTag();var counts=new ListTag();
                for(var part:controller.getParts())if(machine(block(part)) instanceof MufflerPartMachine)parts.add(GtMultiblockAdapter.save(block(part)));
                applied.forEach((id,n)->{var count=new CompoundTag();count.putUUID("id",id);count.putByteArray("count",n.toByteArray());counts.add(count);});
                tag.put("mufflers",parts);tag.put("applied",counts);return tag;
            }
            public void restoreTo(CompoundTag lane) {
                for(var value:save().getList("mufflers",Tag.TAG_COMPOUND)) {
                    var update=(CompoundTag)value;
                    for(var list:List.of(lane.getCompound("gtStructure").getList("parts",Tag.TAG_COMPOUND),lane.getList("blocks",Tag.TAG_COMPOUND)))
                        for(var entry:list)if(((CompoundTag)entry).getLong("pos")==update.getLong("pos"))
                            ((CompoundTag)entry).put("data",update.getCompound("data").copy());
                }
            }
        };
    }

    public static void saveCharge(ServerLevel level,CompoundTag lane,BigInteger amount) {
        var structure=lane.getCompound("gtStructure");boolean found=false;
        require(amount.signum()>=0,"NEGATIVE_GT_ENERGY");
        var left=amount;
        for(var value:structure.getList("parts",Tag.TAG_COMPOUND)) {
            var part=(CompoundTag)value;var be=load(part,level);
            if(machine(be) instanceof EnergyHatchPartMachine hatch) {
                found=true;
                var energy=hatch.energyContainer;var share=left.min(BigInteger.valueOf(energy.getEnergyCapacity()));
                energy.changeEnergy(share.longValueExact()-energy.getEnergyStored());left=left.subtract(share);
                require(energy.getEnergyStored()==share.longValueExact(),"GT_ENERGY_RESTORE_FAILED");
                part.put("data",PlatformData.saveBlock(be));
                for(var block:lane.getList("blocks",Tag.TAG_COMPOUND))if(((CompoundTag)block).getLong("pos")==part.getLong("pos"))((CompoundTag)block).put("data",part.getCompound("data").copy());
            }
        }
        require(found,"GT_ENERGY_HATCH_MISSING");
        require(left.signum()==0,"GT_ENERGY_EXCEEDS_RESTORED_CAPACITY");
    }
}

