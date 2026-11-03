package com.syaru.ae2machines.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.inv.ICraftingInventory;
import appeng.me.service.CraftingService;
import com.syaru.ae2machines.compat.advanced.AdvancedCpuRecovery;
import com.syaru.ae2machines.runtime.FactorySession;
import com.syaru.ae2machines.world.ExternalHandoffs;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPU;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.*;

@Pseudo
@Mixin(targets="net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic",remap=false)
public abstract class AdvancedCpuHandoffMixin {
    @Shadow @Final private AdvCraftingCPU cpu;
    @Unique private Map<Long,CompoundTag> machines$before=Map.of();
    @Unique private ExternalHandoffs.Transaction machines$pending;
    @Unique private void machines$finish(){if(machines$pending!=null){machines$pending.commit();machines$pending=null;}}
    @Redirect(method="executeCrafting",at=@At(value="INVOKE",target="Lappeng/crafting/execution/CraftingCpuHelper;extractPatternInputs(Lappeng/api/crafting/IPatternDetails;Lappeng/crafting/inv/ICraftingInventory;Lnet/minecraft/world/level/Level;Lappeng/api/stacks/KeyCounter;Lappeng/api/stacks/KeyCounter;)[Lappeng/api/stacks/KeyCounter;"),require=1)
    private KeyCounter[] machines$inputs(IPatternDetails pattern,ICraftingInventory inventory,Level world,KeyCounter outputs,KeyCounter containers,
                                         int maxPatterns,CraftingService service,IEnergyService energy,Level level) {
        machines$finish();machines$before=Map.of();
        for(var provider:service.getProviders(pattern))if(provider instanceof FactorySession){machines$before=AdvancedCpuRecovery.before(cpu);break;}
        return CraftingCpuHelper.extractPatternInputs(pattern,inventory,world,outputs,containers);
    }
    @Redirect(method="executeCrafting",at=@At(value="INVOKE",target="Lappeng/api/networking/crafting/ICraftingProvider;pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)Z"),require=1)
    private boolean machines$push(ICraftingProvider provider,IPatternDetails pattern,KeyCounter[] holders) {
        if(!(provider instanceof FactorySession factory))return provider.pushPattern(pattern,holders);
        if(machines$before.isEmpty())throw new IllegalStateException("Missing AdvancedAE CPU before-image");
        ExternalHandoffs.Transaction transaction;
        try {transaction=ExternalHandoffs.begin(factory,machines$before);}
        catch(ExternalHandoffs.PersistenceFailure failure){CraftingCpuHelper.reinjectPatternInputs(cpu.getInventory(),holders);for(var holder:holders)holder.clear();throw failure;}
        try {
            boolean accepted=provider.pushPattern(pattern,holders);
            if(transaction!=null){if(accepted)machines$pending=transaction;else transaction.declined();}
            return accepted;
        }catch(RuntimeException failure){if(transaction!=null)throw new ExternalHandoffs.PersistenceFailure(failure);throw failure;}
    }
    @Inject(method="executeCrafting",at=@At("RETURN"),require=1)
    private void machines$accounted(int maxPatterns,CraftingService service,IEnergyService energy,Level level,CallbackInfoReturnable<Integer> callback) {
        machines$finish();machines$before=Map.of();
    }
}
