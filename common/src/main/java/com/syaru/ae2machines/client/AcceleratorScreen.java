package com.syaru.ae2machines.client;

import appeng.api.config.ActionItems;
import appeng.client.gui.Icon;
import appeng.client.gui.style.StyleManager;
import appeng.client.gui.widgets.*;
import com.syaru.ae2machines.menu.*;
import com.syaru.ae2machines.platform.*;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Presentation only: the native AE2 menu remains authoritative for every operation. */
public final class AcceleratorScreen extends PreviewScreenAdapter<AcceleratorMenu> {
    public static final int WIDTH=384,HEIGHT=256,VIEW_X=52,VIEW_Y=52,VIEW_WIDTH=324,VIEW_HEIGHT=144;
    private static final int VISIBLE_BAYS=7;
    private static final int FRAME=0xff485154,BODY=0xff23292b,HEADER=0xff191e20,NAV=0xff292f31;
    private static final int LINE=0xff3b4548,TEXT=0xffe5ecec,MUTED=0xff9aa9ab,CYAN=0xff80d4c5,VIOLET=0xffc3b0ec;
    private final IconButton[] adjustments=new IconButton[4];
    private final TabButton[] bayButtons=new TabButton[VISIBLE_BAYS];
    private final TabButton factoryTab,structureTab;
    private final ActionButton eject;
    private final IconButton previous,next,refresh,reset,layerDown,layerUp;
    private final FactorySceneView scene=FactorySceneView.create();
    private long shown=-1,observed=-1;
    private int minimumY,firstBay;
    private boolean requested;
    private String state="structure_incomplete";
    private Component previewStatus=Component.empty();

    public AcceleratorScreen(AcceleratorMenu menu,Inventory inventory,Component title){
        super(menu,inventory,title,StyleManager.loadStyleDoc("/screens/ae2machines/accelerator.json"));
        setTextHidden(TEXT_ID_DIALOG_TITLE,true);
        for(int i=0;i<VISIBLE_BAYS;i++){
            int row=i;
            bayButtons[i]=tab("bay"+i,new ItemStack(MachinesMod.CELL_BAY.get()),tr("gui.ae2machines.bay",i+1,0),()->{
                int index=firstBay+row;if(index!=menu.value(10))send(10+index);
            });
        }
        for(int i=0;i<adjustments.length;i++){
            int action=i;
            adjustments[i]=icon("adjust"+i,i%2==0?Icon.ARROW_DOWN:Icon.ARROW_UP,
                    tr(i<2?"gui.ae2machines.spatial_accelerator.speed":"gui.ae2machines.spatial_accelerator.quantum")
                            .append(": ").append(tr(i%2==0?"ae2machines.halve":"ae2machines.double")),()->send(action));
        }
        factoryTab=tab("factory",new ItemStack(MachinesMod.CELL.get()),tr("gui.ae2machines.preview.factory"),()->send(5));
        structureTab=tab("structure",new ItemStack(MachinesMod.COMPUTATION_CONTROLLER.get()),tr("gui.ae2machines.preview.structure"),()->send(6));
        eject=new ActionButton(ActionItems.STASH_TO_PLAYER_INV,()->send(4)){
            @Override public void renderWidget(GuiGraphics g,int mx,int my,float partial){paintIcon(g,this,getIcon());}
            @Override public Rect2i getTooltipArea(){return new Rect2i(getX(),getY(),getWidth(),getHeight());}
        };
        widgets.add("eject",eject);eject.setMessage(tr("ae2machines.eject"));
        previous=icon("previous",Icon.ARROW_UP,tr("gui.ae2machines.previous_bay"),()->page(-visibleBays()));
        next=icon("next",Icon.ARROW_DOWN,tr("gui.ae2machines.next_bay"),()->page(visibleBays()));
        refresh=icon("refresh",Icon.SCHEDULING_ROUND_ROBIN,tr("gui.ae2machines.preview.refresh"),()->send(7));
        reset=icon("reset",Icon.TERMINAL_STYLE_FULL,tr("gui.ae2machines.preview.reset"),()->{if(scene!=null)scene.reset();});
        layerDown=icon("layerDown",Icon.ARROW_DOWN,tr("gui.ae2machines.preview.lower"),()->{if(scene!=null)scene.layer(-1);});
        layerUp=icon("layerUp",Icon.ARROW_UP,tr("gui.ae2machines.preview.raise"),()->{if(scene!=null)scene.layer(1);});
    }
    private static net.minecraft.network.chat.MutableComponent tr(String key,Object...args){return Component.translatable(key,args);}
    private void paintIcon(GuiGraphics g,IconButton button,Icon glyph){
        int x=button.getX(),y=button.getY();
        int color=button.active?(button.isHoveredOrFocused()?0xff84928f:0xff73817f):0xff4c5654;
        g.fill(x,y,x+button.getWidth(),y+button.getHeight(),color);
        glyph.getBlitter().dest(x+1,y+1).opacity(button.active?1f:.4f).blit(g);
        if(button.isFocused())g.renderOutline(x-1,y-1,button.getWidth()+2,button.getHeight()+2,CYAN);
    }
    private IconButton icon(String id,Icon glyph,Component tooltip,Runnable action){
        var button=new IconButton(ignored->action.run()){
            @Override protected Icon getIcon(){return glyph;}
            @Override public void renderWidget(GuiGraphics g,int mx,int my,float partial){paintIcon(g,this,glyph);}
            @Override public Rect2i getTooltipArea(){return new Rect2i(getX(),getY(),getWidth(),getHeight());}
        };
        button.setMessage(tooltip);widgets.add(id,button);return button;
    }
    private TabButton tab(String id,ItemStack item,Component tooltip,Runnable action){
        boolean bay=id.startsWith("bay");int row=bay?Integer.parseInt(id.substring(3)):-1;
        var button=new TabButton(item,tooltip,ignored->action.run()){
            @Override public void renderWidget(GuiGraphics g,int mx,int my,float partial){
                int x=getX(),y=getY();
                g.fill(x,y,x+width,y+height,isSelected()?0xff30423e:isHoveredOrFocused()?0xff354043:NAV);
                if(isSelected()){
                    if(bay)g.fill(x,y,x+2,y+height,CYAN);
                    else g.fill(x,y+height-2,x+width,y+height,CYAN);
                }
                g.renderItem(item,x+(bay?3:2),y+2);
                String label=bay?Integer.toString(firstBay+row+1):getMessage().getString();
                g.drawString(font,label,bay?x+width-3-font.width(label):x+21,y+6,isSelected()?TEXT:MUTED,false);
                if(isFocused())g.renderOutline(x-1,y-1,width+2,height+2,CYAN);
            }
            @Override public Rect2i getTooltipArea(){return new Rect2i(getX(),getY(),getWidth(),getHeight());}
        };
        widgets.add(id,button);return button;
    }
    private int visibleBays(){return Math.min(VISIBLE_BAYS,Math.max(1,(imageHeight-88)/22));}
    private int controlWidth(){return (imageWidth-68)/2;}
    private int controlX(int index){return VIEW_X+index*(controlWidth()+8);}
    private int controlY(){return imageHeight-54;}
    private int sceneWidth(){return imageWidth-VIEW_X-8;}
    private int sceneHeight(){return imageHeight-VIEW_Y-60;}
    private Component statusLine(){return menu.value(11)>0 && menu.value(5)==0?tr("gui.ae2machines.preview.no_cell"):tr("ae2machines."+state);}
    private void page(int delta){firstBay=Math.max(0,Math.min(Math.max(0,menu.value(11)-visibleBays()),firstBay+delta));}
    private void send(int action){if(minecraft!=null && minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,action);}
    @Override protected void init(){
        imageWidth=Math.min(WIDTH,width-12);imageHeight=Math.min(HEIGHT,height-12);super.init();
        for(int i=0;i<4;i++){
            adjustments[i].setX(leftPos+controlX(i/2)+controlWidth()-(i%2==0?42:22));
            adjustments[i].setY(topPos+controlY()+18);
        }
        if(scene!=null && !requested){requested=true;send(7);}
    }
    @Override protected void updateBeforeRender(){
        super.updateBeforeRender();page(0);
        state=menu.value(11)==0?"structure_incomplete":menu.value(4)!=0?"quarantined":menu.value(5)==0?"uncompiled"
                :menu.value(9)==0?"processor_limit":menu.value(3)==0?"offline":switch(menu.value(6)){case 1->"working";case 2->"no_power";case 3->"output_wait";default->"loaded";};
        for(int i=0;i<VISIBLE_BAYS;i++){
            int index=firstBay+i;var button=bayButtons[i];button.visible=i<visibleBays() && index<menu.value(11);
            button.setSelected(index==menu.value(10));button.setMessage(tr("gui.ae2machines.bay",index+1,menu.value(11)));
        }
        previous.visible=next.visible=menu.value(11)>visibleBays();previous.active=firstBay>0;next.active=firstBay+visibleBays()<menu.value(11);
        for(int i=0;i<adjustments.length;i++)adjustments[i].active=menu.value(13+i)!=0;
        eject.active=menu.value(5)!=0 && menu.value(4)==0;
        factoryTab.active=structureTab.active=refresh.active=scene!=null;
        factoryTab.setSelected(menu.value(12)==FactoryPreviewData.FACTORY);structureTab.setSelected(menu.value(12)==FactoryPreviewData.STRUCTURE);
        var receiver=menu.previewData();
        if(receiver.request()!=observed){if(scene!=null)scene.close();observed=receiver.request();shown=-1;}
        var snapshot=receiver.complete();
        if(snapshot!=null && snapshot.request()!=shown){if(scene!=null)scene.show(snapshot);shown=snapshot.request();minimumY=snapshot.blocks().stream().mapToInt(b->b.position().getY()).min().orElse(0);}
        reset.active=scene!=null && snapshot!=null && !snapshot.blocks().isEmpty();
        layerDown.active=reset.active && scene.layerY()>minimumY;layerUp.active=reset.active && scene.layerY()<scene.maximumY();
        var layer=scene==null || scene.layerY()==scene.maximumY()?tr("gui.ae2machines.preview.all"):Component.literal("Y "+scene.layerY());
        layerDown.setMessage(tr("gui.ae2machines.preview.lower").append(" / ").append(layer));layerUp.setMessage(tr("gui.ae2machines.preview.raise").append(" / ").append(layer));
        if(scene==null)previewStatus=tr("gui.ae2machines.preview.ldlib_missing");
        else if(snapshot==null)previewStatus=tr("gui.ae2machines.preview."+(receiver.status().equals("interrupted") || receiver.status().equals("invalid")?"unavailable":"loading"),receiver.scanned(),receiver.total());
        else if(scene.degraded() || snapshot.omitted()>0 || snapshot.unloaded()>0)previewStatus=tr("gui.ae2machines.preview.partial");
        else previewStatus=tr("gui.ae2machines.preview."+snapshot.status());
    }
    private static String multiplier(int power){return (power>16?"2^"+power:java.text.NumberFormat.getIntegerInstance(java.util.Locale.ROOT).format(1L<<Math.max(0,power)))+"\u00d7";}
    private ItemStack processor(boolean speed){
        if(menu.value(11)==0)return ItemStack.EMPTY;
        int tier=Math.max(0,Math.min(8,menu.value(speed?7:8)-(speed?1:4)));
        return new ItemStack((speed?MachinesMod.SPEED_PROCESSORS:MachinesMod.PARALLEL_PROCESSORS).get(tier).get());
    }
    @Override public void drawBG(GuiGraphics g,int x,int y,int mx,int my,float partialTick){
        g.fill(x,y,x+imageWidth,y+imageHeight,FRAME);g.fill(x+1,y+1,x+imageWidth-1,y+imageHeight-1,BODY);
        g.fill(x+1,y+1,x+imageWidth-1,y+26,HEADER);g.fill(x+1,y+26,x+45,y+imageHeight-1,NAV);
        g.fill(x+44,y+26,x+45,y+imageHeight-1,LINE);g.fill(x+1,y+25,x+imageWidth-1,y+26,LINE);
        g.renderItem(new ItemStack(MachinesMod.COMPUTATION_CONTROLLER.get()),x+9,y+5);
        g.fill(x+VIEW_X,y+VIEW_Y,x+imageWidth-8,y+VIEW_Y+sceneHeight(),0xff171b1c);
        if(scene!=null)scene.draw(g,x+VIEW_X,y+VIEW_Y,sceneWidth(),sceneHeight(),mx,my,partialTick);
        g.fill(x+VIEW_X,y+controlY()-3,x+imageWidth-8,y+controlY()-2,LINE);
        int divider=x+controlX(1)-4;g.fill(divider,y+controlY()+3,divider+1,y+imageHeight-9,LINE);
        for(int i=0;i<2;i++){
            g.renderItem(processor(i==0),x+controlX(i)+5,y+controlY()+17);
            int max=Math.max(0,menu.value(i==0?7:8)),current=Math.max(0,Math.min(max,menu.value(i)));
            int segments=Math.max(1,Math.min(17,max+1)),track=controlWidth()-74;
            for(int segment=0;segment<segments;segment++){
                int start=x+controlX(i)+28+segment*track/segments,end=x+controlX(i)+28+(segment+1)*track/segments-1;
                g.fill(start,y+controlY()+40,Math.max(start+1,end),y+controlY()+42,menu.value(11)>0 && segment<=current?(i==0?VIOLET:CYAN):LINE);
            }
        }
        var snapshot=menu.previewData().complete();
        if(snapshot!=null && (scene!=null && scene.degraded() || snapshot.omitted()>0 || snapshot.unloaded()>0))Icon.INVALID.getBlitter().dest(x+VIEW_X+4,y+VIEW_Y+4).blit(g);
    }
    @Override public void drawFG(GuiGraphics g,int x,int y,int mx,int my){
        g.drawString(font,font.plainSubstrByWidth(getTitle().getString(),132),30,9,TEXT,false);
        if(menu.value(11)>0){
            String bays=String.format(java.util.Locale.ROOT,"%02d/%02d",menu.value(10)+1,menu.value(11));
            g.drawString(font,bays,(44-font.width(bays))/2,34,MUTED,false);
        }
        int statusColor=menu.value(4)!=0?0xffed8f86:menu.value(3)!=0?CYAN:state.equals("no_power") || state.equals("processor_limit")?0xffe1bd7c:MUTED;
        String status=font.plainSubstrByWidth(statusLine().getString(),imageWidth-186);
        int statusX=imageWidth-10-font.width(status);g.fill(statusX-8,11,statusX-5,14,statusColor);g.drawString(font,status,statusX,9,statusColor,false);
        for(int i=0;i<2;i++){
            int start=controlX(i);g.drawString(font,tr(i==0?"gui.ae2machines.spatial_accelerator.speed":"gui.ae2machines.spatial_accelerator.quantum"),start+4,controlY()+3,MUTED,false);
            String value=menu.value(11)==0?"-":multiplier(menu.value(i));
            float scale=Math.min(1.2f,(controlWidth()-82)/(float)Math.max(1,font.width(value)));
            g.pose().pushPose();g.pose().translate(start+28,controlY()+21,0);g.pose().scale(scale,scale,1);
            g.drawString(font,value,0,0,i==0?VIOLET:CYAN,false);g.pose().popPose();
        }
        var snapshot=menu.previewData().complete();
        if(scene==null || snapshot==null || snapshot.blocks().isEmpty()){
            int line=0;var lines=font.split(previewStatus,sceneWidth()-32);int top=VIEW_Y+(sceneHeight()-lines.size()*10)/2;
            for(var part:lines)g.drawString(font,part,VIEW_X+(sceneWidth()-font.width(part))/2,top+10*line++,MUTED,false);
        }
        if(scene!=null && overScene(mx,my)){var hovered=scene.hovered();if(hovered!=null)drawTooltip(g,mx,my,List.of(hovered));}
        double localX=mx-leftPos,localY=my-topPos;
        if(localY>=1 && localY<25 && localX>=30 && localX<164 && font.width(getTitle())>132)drawTooltip(g,mx,my,List.of(getTitle()));
        if(localY>=1 && localY<25 && localX>=170)drawTooltip(g,mx,my,List.of(statusLine()));
        if(localY>=controlY() && localY<imageHeight-8)for(int i=0;i<2;i++)if(localX>=controlX(i) && localX<controlX(i)+controlWidth()-44)
            drawTooltip(g,mx,my,List.of(tr(i==0?"gui.ae2machines.spatial_accelerator.speed":"gui.ae2machines.spatial_accelerator.quantum"),tr("gui.ae2machines.maximum",multiplier(menu.value(i==0?7:8)))));
        if(localX>=VIEW_X && localX<VIEW_X+18 && localY>=VIEW_Y && localY<VIEW_Y+18 && snapshot!=null)drawTooltip(g,mx,my,List.of(previewStatus));
    }
    private boolean overScene(double x,double y){return x>=leftPos+VIEW_X && x<leftPos+VIEW_X+sceneWidth() && y>=topPos+VIEW_Y && y<topPos+VIEW_Y+sceneHeight();}
    @Override protected boolean previewScroll(double x,double y,double delta){
        if(x>=leftPos+6 && x<leftPos+42 && y>=topPos+52 && y<topPos+imageHeight-36){page(delta>0?-1:1);return true;}
        return scene!=null && overScene(x,y) && scene.mouseScrolled(x,y,delta);
    }
    @Override public boolean mouseClicked(double x,double y,int button){return scene!=null && overScene(x,y) && scene.mouseClicked(x,y,button) || super.mouseClicked(x,y,button);}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){return scene!=null && scene.mouseDragged(x,y,button,dx,dy) || super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){return scene!=null && scene.mouseReleased(x,y,button) || super.mouseReleased(x,y,button);}
    @Override public void removed(){if(scene!=null)scene.close();super.removed();}
}
