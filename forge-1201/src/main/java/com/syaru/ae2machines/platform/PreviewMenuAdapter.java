package com.syaru.ae2machines.platform;

import appeng.menu.AEBaseMenu;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

public abstract class PreviewMenuAdapter extends AEBaseMenu {
    protected PreviewMenuAdapter(MenuType<?> type,int id,Inventory inventory,Object host){super(type,id,inventory,host);}
    protected abstract void previewSynchronized();
    @Override public void onServerDataSync(){super.onServerDataSync();previewSynchronized();}
}
