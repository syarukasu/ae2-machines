package com.syaru.ae2machines.client;

import com.syaru.ae2machines.menu.FactoryPreviewData;
import com.syaru.ae2machines.platform.PlatformData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public interface FactorySceneView extends AutoCloseable {
    static FactorySceneView create(){
        if(!PlatformData.ldlibPresent())return null;
        try{return (FactorySceneView)Class.forName("com.syaru.ae2machines.client.LdlibFactorySceneView").getConstructor().newInstance();}
        catch(ReflectiveOperationException|LinkageError failure){org.slf4j.LoggerFactory.getLogger(FactorySceneView.class).error("LDLib preview could not initialize",failure);return null;}
    }
    void show(FactoryPreviewData.Snapshot snapshot);
    void draw(GuiGraphics graphics,int x,int y,int width,int height,int mouseX,int mouseY,float partialTick);
    boolean mouseClicked(double x,double y,int button);
    boolean mouseDragged(double x,double y,int button,double dx,double dy);
    boolean mouseReleased(double x,double y,int button);
    boolean mouseScrolled(double x,double y,double delta);
    void reset();
    void layer(int delta);
    int layerY();
    int maximumY();
    Component hovered();
    boolean degraded();
    @Override void close();
}
