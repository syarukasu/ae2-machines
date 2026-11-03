package com.syaru.ae2machines.compat.mek;

import mekanism.common.tile.prefab.TileEntityConfigurableMachine;
import net.minecraft.world.item.ItemStack;

/** One native slot representing identical virtual lanes; the native slot owns every item. */
public interface MekVirtualSlot {
    void machines$owner(TileEntityConfigurableMachine machine);
    int machines$nativeLimit(ItemStack stack);
    default boolean machines$parallel(){return false;}
    default boolean machines$captured(){return false;}
}
