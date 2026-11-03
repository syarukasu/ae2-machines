package com.syaru.ae2machines.client.recipe;

import com.lowdragmc.lowdraglib.gui.widget.*;
import com.lowdragmc.lowdraglib.jei.IngredientIO;
import net.minecraft.world.SimpleContainer;
import com.syaru.ae2machines.client.LdlibFactorySceneView;
import com.syaru.ae2machines.platform.MachinesMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** LDLib's viewer wrappers own input dispatch. All slots are detached, read-only examples. */
public final class ComputationPreviewWidget extends WidgetGroup {
    public static final int WIDTH=176,HEIGHT=174;
    private final ComputationExample example;
    private final LdlibFactorySceneView view=new LdlibFactorySceneView(false);
    private final SimpleContainer materials=new SimpleContainer(9);
    private int units=1;
    private boolean formed=true;
    private boolean shown;
    public ComputationPreviewWidget(ComputationExample example){
        super(0,0,WIDTH,HEIGHT);this.example=example;setClientSideWidget();
        addWidget(new Canvas());
        for(int i=0;i<9;i++)addWidget(new SlotWidget(materials,i,6+i*18,154,false,false).setIngredientIO(IngredientIO.INPUT));
        reload();
    }
    private void reload(){
        var stacks=example.materials(units);for(int i=0;i<9;i++)materials.setItem(i,i<stacks.size()?stacks.get(i):net.minecraft.world.item.ItemStack.EMPTY);
        if(shown)view.show(example.snapshot(units,formed));
    }
    public void units(int count){units=Math.max(1,Math.min(32,count));reload();}
    public int units(){return units;}
    public void formed(boolean value){formed=value;reload();}
    public boolean formed(){return formed;}
    private final class Canvas extends Widget {
        Canvas(){super(0,0,WIDTH,152);setClientSideWidget();}
        @Override public void drawInBackground(GuiGraphics g,int mouseX,int mouseY,float partial){
            int x=getPositionX(),y=getPositionY();
            if(!shown){shown=true;view.show(example.snapshot(units,formed));}
            g.fill(x,y,x+WIDTH,y+152,0xffd7d9df);
            view.draw(g,x,y+22,WIDTH,108,mouseX,mouseY,partial);
            var font=Minecraft.getInstance().font;
            g.drawString(font,Component.translatable("ae2machines.preview.units",units),x+23,y+7,0x32333b,false);
            g.drawString(font,"T"+example.tier(),x+119,y+7,0x32333b,false);
            button(g,x+2,y+3,"-",mouseX,mouseY);button(g,x+92,y+3,"+",mouseX,mouseY);
            button(g,x+2,y+133,formed?"[=]":"[]",mouseX,mouseY);
            button(g,x+110,y+133,"R",mouseX,mouseY);button(g,x+132,y+133,"v",mouseX,mouseY);button(g,x+154,y+133,"^",mouseX,mouseY);
            g.drawString(font,Component.translatable(formed?"ae2machines.preview.formed":"ae2machines.preview.blocks"),x+25,y+138,0x32333b,false);
        }
        private void button(GuiGraphics g,int x,int y,String text,int mx,int my){
            g.fill(x,y,x+20,y+16,isMouseOver(x,y,20,16,mx,my)?0xffb0b5c8:0xffbec2cc);
            g.drawCenteredString(Minecraft.getInstance().font,text,x+10,y+4,0xff30313a);
        }
        @Override public void drawInForeground(GuiGraphics g,int mx,int my,float partial){
            int x=getPositionX(),y=getPositionY();String key=null;
            if(my>=y+133 && my<y+149){
                if(mx>=x+2 && mx<x+22)key="ae2machines.preview.toggle";
                else if(mx>=x+110 && mx<x+130)key="ae2machines.preview.reset";
                else if(mx>=x+132 && mx<x+174)key="ae2machines.preview.layer";
            }
            Component hovered=key==null?view.hovered():Component.translatable(key);
            if(hovered!=null && isMouseOverElement(mx,my))g.renderTooltip(Minecraft.getInstance().font,hovered,mx,my);
        }
        @Override public boolean mouseClicked(double mx,double my,int button){
            if(!isMouseOverElement(mx,my))return false;
            int x=(int)mx-getPositionX(),y=(int)my-getPositionY();
            if(button==0 && y>=3 && y<19){if(x<22){units(units-1);return true;}if(x>=92 && x<112){units(units+1);return true;}}
            if(button==0 && y>=133 && y<149){
                if(x<22)formed(!formed);else if(x>=110 && x<130)view.reset();else if(x>=132 && x<152)view.layer(-1);else if(x>=154)view.layer(1);else return false;return true;
            }
            return y>=22 && y<130 && view.mouseClicked(mx,my,button);
        }
        @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){return view.mouseDragged(x,y,button,dx,dy);}
        @Override public boolean mouseReleased(double x,double y,int button){return view.mouseReleased(x,y,button);}
        public boolean mouseWheelMove(double x,double y,double delta){return view.mouseScrolled(x,y,delta);}
        public boolean mouseWheelMove(double x,double y,double horizontal,double vertical){return view.mouseScrolled(x,y,vertical);}
    }
}
