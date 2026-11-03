package com.syaru.ae2machines.compat.mek;

import java.util.Set;
import mekanism.common.tile.factory.TileEntityFactory;
import mekanism.common.tile.prefab.TileEntityConfigurableMachine;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Additional native CachedRecipe machines, kept separate from exact receipt eligibility. */
public final class MekRecipeMachines {
    private static final Set<String> EVOLVED_TYPES=Set.of(
            "fr.iglee42.evolvedmekanism.tiles.machine.TileEntityAlloyer",
            "fr.iglee42.evolvedmekanism.tiles.machine.TileEntityChemixer",
            "fr.iglee42.evolvedmekanism.tiles.machine.TileEntityMelter",
            "fr.iglee42.evolvedmekanism.tiles.machine.TileEntitySolidifier",
            "fr.iglee42.evolvedmekanism.tiles.machine.TileEntityFluidAlloyer",
            "fr.iglee42.evolvedmekanism.tiles.machine.TileEntityFluidCombiner",
            "fr.iglee42.evolvedmekanism.tiles.factory.TileEntityAlloyingFactory",
            "fr.iglee42.evolvedmekanism.tiles.TileEntityLunarNeutronActivator");
    private static final Set<String> TYPES=Set.of(
            "mekanism.common.tile.machine.TileEntityCombiner",
            "mekanism.common.tile.machine.TileEntityPrecisionSawmill",
            "mekanism.common.tile.machine.TileEntityMetallurgicInfuser",
            "mekanism.common.tile.machine.TileEntityOsmiumCompressor",
            "mekanism.common.tile.machine.TileEntityPurificationChamber",
            "mekanism.common.tile.machine.TileEntityChemicalInjectionChamber",
            "mekanism.common.tile.machine.TileEntityChemicalDissolutionChamber",
            "mekanism.common.tile.machine.TileEntityAntiprotonicNucleosynthesizer",
            "mekanism.common.tile.machine.TileEntityPigmentExtractor",
            "mekanism.common.tile.machine.TileEntityPigmentMixer",
            "mekanism.common.tile.machine.TileEntityPaintingMachine",
            "mekanism.common.tile.machine.TileEntityNutritionalLiquifier",
            "mekanism.common.tile.machine.TileEntitySolarNeutronActivator",
            "mekanism.common.tile.factory.TileEntityCombiningFactory",
            "mekanism.common.tile.factory.TileEntitySawingFactory",
            "mekanism.common.tile.factory.TileEntityMetallurgicInfuserFactory",
            "mekanism.common.tile.factory.TileEntityItemStackGasToItemStackFactory",
            "mekanism.common.tile.factory.TileEntityItemStackChemicalToItemStackFactory");
    private MekRecipeMachines() {}
    public static boolean evolved(BlockEntity machine){return machine!=null && EVOLVED_TYPES.contains(machine.getClass().getName());}
    public static boolean supports(BlockEntity machine){return machine!=null && (TYPES.contains(machine.getClass().getName()) || evolved(machine));}
    public static boolean processingSlot(mekanism.api.inventory.IInventorySlot slot){
        return slot instanceof mekanism.common.inventory.slot.InputInventorySlot || slot instanceof mekanism.common.inventory.slot.OutputInventorySlot
                || slot.getClass().getName().equals("fr.iglee42.evolvedmekanism.tiles.LimitedInputInventorySlot");
    }
    public static int processes(TileEntityConfigurableMachine machine){return machine instanceof TileEntityFactory<?> f?f.progress.length:1;}
    public interface Usage {
        void machines$usageOwner(TileEntityConfigurableMachine machine,int process);
    }
    public static long perLaneUsage(TileEntityConfigurableMachine machine,int process,long used) {
        if(machine==null || MekWorldParallel.lanes(machine)<=1 || MekWorldParallel.progress(machine,process)==0)return used;
        int group=MekWorldParallel.operations(machine,process);
        if(used%group!=0)throw new IllegalStateException("NATIVE_USAGE_GROUP_NOT_EXACT");
        return used/group;
    }
}
