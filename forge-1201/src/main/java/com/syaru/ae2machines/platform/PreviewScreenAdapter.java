package com.syaru.ae2machines.platform;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.menu.AEBaseMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public abstract class PreviewScreenAdapter<M extends AEBaseMenu> extends AEBaseScreen<M> {
    protected PreviewScreenAdapter(M menu,Inventory inventory,Component title,ScreenStyle style){super(menu,inventory,title,style);}
    protected abstract boolean previewScroll(double x,double y,double delta);
    @Override public boolean mouseScrolled(double x,double y,double delta){return previewScroll(x,y,delta) || super.mouseScrolled(x,y,delta);}
}
