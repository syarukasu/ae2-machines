package com.syaru.ae2machines.platform;

import appeng.api.storage.MEStorage;
import appeng.parts.automation.HandlerStrategy;
import net.minecraft.world.Container;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

public final class CapturedInventoryAccess {
    private CapturedInventoryAccess(){}
    public static MEStorage internal(appeng.api.inventories.InternalInventory inventory,Runnable changed) {
        var facade=HandlerStrategy.ITEMS.getFacade(inventory.toItemHandler());
        facade.setChangeListener(changed);return facade;
    }
    public static MEStorage container(Container inventory,Runnable changed) {
        var facade=HandlerStrategy.ITEMS.getFacade(new InvWrapper(inventory));
        facade.setChangeListener(changed);return facade;
    }
}
