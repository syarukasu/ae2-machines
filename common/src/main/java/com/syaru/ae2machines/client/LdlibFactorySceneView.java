package com.syaru.ae2machines.client;

import com.lowdragmc.lowdraglib.gui.widget.SceneWidget;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import com.mojang.blaze3d.systems.RenderSystem;
import com.syaru.ae2machines.menu.FactoryPreviewData;
import com.syaru.ae2machines.platform.PlatformData;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.joml.Vector3f;

/** A non-ticking dummy world. Pointer input changes the camera only, never a server widget. */
public final class LdlibFactorySceneView implements FactorySceneView {
    private static final Set<net.minecraft.world.level.Level> SNAPSHOTS=Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    public static boolean ownsSnapshot(net.minecraft.world.level.Level level){return SNAPSHOTS.contains(level);}
    private SceneWidget scene;
    private FactoryPreviewData.Snapshot snapshot;
    private int minY,maxY,layer;
    private boolean degraded;
    private Vector3f center;
    private Vector3f extent;
    private float fitZoom;
    private float initialPitch;
    private boolean warm,fitPending;
    private int viewportWidth,viewportHeight;
    private int panButton=-1;
    private final boolean allowBuffers;
    private static final class ReadOnlyScene extends SceneWidget {
        ReadOnlyScene(){
            super(0,0,1,1,Minecraft.getInstance().level,false);setClientSideWidget();
            // SceneWidget otherwise forwards reads to the player's world instead of its stored snapshot.
            getDummyWorld().proxyWorld.clear();
            SNAPSHOTS.add(getDummyWorld());
        }
        @Override public boolean mouseReleased(double x,double y,int button){
            boolean consumed=dragging;dragging=false;clickPosFace=null;selectedPosFace=null;return consumed;
        }
    }
    public LdlibFactorySceneView(){this(true);}
    public LdlibFactorySceneView(boolean allowBuffers){this.allowBuffers=allowBuffers;}
    @Override public void show(FactoryPreviewData.Snapshot snapshot){
        this.snapshot=snapshot;degraded=false;
        if(snapshot.blocks().isEmpty()){closeScene();return;}
        int x0=Integer.MAX_VALUE,z0=Integer.MAX_VALUE,x1=Integer.MIN_VALUE,z1=Integer.MIN_VALUE;
        minY=Integer.MAX_VALUE;maxY=Integer.MIN_VALUE;
        for(var block:snapshot.blocks()){
            var p=block.position();x0=Math.min(x0,p.getX());x1=Math.max(x1,p.getX());
            minY=Math.min(minY,p.getY());maxY=Math.max(maxY,p.getY());z0=Math.min(z0,p.getZ());z1=Math.max(z1,p.getZ());
        }
        layer=maxY;center=new Vector3f((float)(((double)x0+x1+1)/2),((float)minY+maxY+1)/2,(float)(((double)z0+z1+1)/2));
        initialPitch=-135;
        if(snapshot.mode()==FactoryPreviewData.STRUCTURE)for(var block:snapshot.blocks())if(block.state().is(com.syaru.ae2machines.platform.MachinesMod.COMPUTATION_CONTROLLER.get()))
            initialPitch=45+block.state().getValue(com.syaru.ae2machines.block.AcceleratorBlock.FACING).toYRot();
        extent=new Vector3f((float)((long)x1-x0+1),maxY-minY+1,(float)((long)z1-z0+1));
        fitZoom=PreviewCameraFit.distance(extent.x,extent.y,extent.z,25,initialPitch,(float)AcceleratorScreen.VIEW_WIDTH/AcceleratorScreen.VIEW_HEIGHT);
        rebuild(false);
    }
    private void rebuild(boolean preserveCamera){
        try{buildScene(preserveCamera);}
        catch(RuntimeException failure){
            if(!degraded){
                degraded=true;org.slf4j.LoggerFactory.getLogger(LdlibFactorySceneView.class).warn("Native visual data could not initialize; retaining block-state preview",failure);
                try{buildScene(preserveCamera);}catch(RuntimeException fallbackFailure){closeScene();org.slf4j.LoggerFactory.getLogger(LdlibFactorySceneView.class).error("LDLib block-state preview could not initialize",fallbackFailure);}
            }else{closeScene();org.slf4j.LoggerFactory.getLogger(LdlibFactorySceneView.class).error("LDLib block-state preview could not initialize",failure);}
        }
    }
    private void buildScene(boolean preserveCamera){
        float yaw=scene==null?25:scene.getRotationYaw(),pitch=scene==null?initialPitch:scene.getRotationPitch(),zoom=scene==null?fitZoom:scene.getZoom();
        var cameraCenter=preserveCamera && scene!=null?new Vector3f(scene.getCenter()):new Vector3f(center);
        closeScene();if(snapshot==null || snapshot.blocks().isEmpty() || Minecraft.getInstance().level==null)return;
        scene=new ReadOnlyScene();warm=false;fitPending=!preserveCamera;scene.setDraggable(true).setScalable(true).setRenderFacing(false).setRenderSelect(false).setHoverTips(false);
        scene.setClearColor(0xff171b1c);scene.getRenderer().setFov(45);
        var positions=new ArrayList<BlockPos>();
        for(var block:snapshot.blocks()){
            if(block.position().getY()>layer)continue;
            var info=new BlockInfo(block.state(),!degraded && !snapshot.status().equals("example") && block.state().hasBlockEntity());
            scene.getDummyWorld().addBlock(block.position(),info);positions.add(block.position());
        }
        if(positions.isEmpty())return;
        scene.setRenderedCore(positions);
        // Populate LDLib's render filter before looking up native visual entities.
        if(!degraded)for(var block:snapshot.blocks())if(block.position().getY()<=layer && block.state().hasBlockEntity()){
            var be=scene.getDummyWorld().getBlockEntity(block.position());
            if(be!=null){be.setLevel(scene.getDummyWorld());if(!block.visual().isEmpty())PlatformData.applyVisualTag(be,block.visual().copy());}
        }
        scene.setCenter(cameraCenter);scene.setZoom(preserveCamera?zoom:fitZoom);
        scene.setCameraYawAndPitch(preserveCamera?yaw:25,preserveCamera?pitch:initialPitch);
    }
    @Override public void draw(GuiGraphics graphics,int x,int y,int width,int height,int mouseX,int mouseY,float partialTick){
        if(scene==null)return;scene.setSelfPosition(x,y);scene.setSize(width,height);
        if(fitPending || width!=viewportWidth || height!=viewportHeight){
            float next=PreviewCameraFit.distance(extent.x,extent.y,extent.z,scene.getRotationYaw(),scene.getRotationPitch(),(float)width/height);
            scene.setZoom(fitPending?next:scene.getZoom()*next/fitZoom);fitZoom=next;fitPending=false;viewportWidth=width;viewportHeight=height;
        }
        try{
            graphics.flush();scene.drawInBackground(graphics,mouseX,mouseY,partialTick);
            if(!warm){warm=true;if(allowBuffers && snapshot.blocks().size()>128)scene.useCacheBuffer(true);}
        }
        catch(RuntimeException failure){
            if(!degraded){degraded=true;org.slf4j.LoggerFactory.getLogger(LdlibFactorySceneView.class).warn("Preview block-entity renderer failed; retaining block-state preview",failure);rebuild(true);}
            else {closeScene();org.slf4j.LoggerFactory.getLogger(LdlibFactorySceneView.class).error("LDLib block-state preview failed",failure);}
        }finally{RenderSystem.depthMask(true);RenderSystem.enableDepthTest();RenderSystem.setShaderColor(1,1,1,1);}
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        if(scene==null || !scene.isMouseOverElement(x,y))return false;
        if(PreviewCameraPan.gesture(button,net.minecraft.client.gui.screens.Screen.hasShiftDown())){
            scene.mouseReleased(x,y,button);panButton=button;return true;
        }
        return scene.mouseClicked(x,y,button);
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){
        if(scene!=null && panButton==button && viewportHeight>0){
            if(!Double.isFinite(dx) || !Double.isFinite(dy))return false;
            scene.setCenter(new Vector3f(scene.getCenter()).add(PreviewCameraPan.displacement(dx,dy,scene.getZoom(),viewportHeight,scene.getRotationYaw(),scene.getRotationPitch())));return true;
        }
        if(scene==null || !scene.isDragging())return false;scene.mouseDragged(x,y,button,dx,dy);return true;
    }
    @Override public boolean mouseReleased(double x,double y,int button){
        if(panButton==button){panButton=-1;return true;}
        return scene!=null && scene.mouseReleased(x,y,button);
    }
    @Override public boolean mouseScrolled(double x,double y,double delta){
        if(scene==null || !scene.isMouseOverElement(x,y))return false;
        scene.setZoom(Math.max(.5f,Math.min(2048f,scene.getZoom()*(float)Math.pow(.9,delta))));return true;
    }
    @Override public void reset(){layer=maxY;rebuild(false);}
    @Override public void layer(int delta){int next=Math.max(minY,Math.min(maxY,layer+delta));if(next!=layer){layer=next;rebuild(true);}}
    @Override public int layerY(){return layer;}
    @Override public int maximumY(){return maxY;}
    @Override public boolean degraded(){return degraded;}
    @Override public Component hovered(){
        if(scene==null || scene.getRenderer().getLastTraceResult()==null)return null;
        return scene.getDummyWorld().getBlockState(scene.getRenderer().getLastTraceResult().getBlockPos()).getBlock().getName();
    }
    private void closeScene(){panButton=-1;if(scene!=null){scene.getRenderer().deleteCacheBuffer();SNAPSHOTS.remove(scene.getDummyWorld());scene.getDummyWorld().clear();scene=null;}}
    @Override public void close(){closeScene();snapshot=null;}
}
