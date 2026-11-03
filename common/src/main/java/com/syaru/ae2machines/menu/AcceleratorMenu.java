package com.syaru.ae2machines.menu;

import com.syaru.ae2machines.block.*;
import com.syaru.ae2machines.platform.*;
import java.util.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

public final class AcceleratorMenu extends PreviewMenuAdapter {
    private final BaseAccelerator controller;
    private final ContainerData data;
    private List<BaseAccelerator> bays=List.of();
    private int selectedBay,previewMode=FactoryPreviewData.STRUCTURE;
    private long previewRequest;
    private UUID previewOwner;
    private FactoryPreviewCapture capture;
    private boolean previewRequested;
    private final int[] adjustmentAvailability=new int[4];
    private BaseAccelerator settingsHost;
    private long nextSettingsPoll=Long.MIN_VALUE;
    private final FactoryPreviewData.Receiver received=new FactoryPreviewData.Receiver();
    @appeng.menu.guisync.GuiSync(100)
    public PreviewSync preview=new PreviewSync(new net.minecraft.nbt.CompoundTag());
    @appeng.menu.guisync.GuiSync(101)
    public String selectedFactory="";

    public AcceleratorMenu(int id,Inventory inventory,BaseAccelerator controller) {
        super(MachinesMod.MENU.get(),id,inventory,controller);this.controller=controller;
        if(controller!=null && !ComputationStructure.guiController(controller))throw new IllegalArgumentException("GUI_REQUIRES_COMPUTATION_CONTROLLER");
        refreshBays();
        data=controller==null?new SimpleContainerData(17):new ContainerData(){
            public int get(int i){
                var host=selected();
                if(i==10)return selectedBay;if(i==11)return bays.size();if(i==12)return previewMode;
                if(host==null)return 0;
                if(i>=13)return adjustmentAvailability[i-13];
                return switch(i){
                    case 0->host.speedPower();case 1->host.lanePower();case 2->host.powerPermille();
                    case 3->host.factoryOnline()?1:0;case 5->host.hasFactory()?1:0;case 6->host.workState();
                    case 7->ComputationStructure.maximumPower(host,true);case 8->ComputationStructure.maximumPower(host,false);
                    case 9->host.settingsFit()?1:0;
                    default->host.loadFailed() || host.machineRuntimes().stream().anyMatch(com.syaru.ae2machines.runtime.ProcessingRuntime::quarantined)?1:0;
                };
            }
            public void set(int i,int value){}
            public int getCount(){return 17;}
        };
        addDataSlots(data);
        if(controller!=null)previewMode=selected()!=null && selected().hasFactory()?FactoryPreviewData.FACTORY:FactoryPreviewData.STRUCTURE;
    }
    public static AcceleratorMenu fromNetwork(int id,Inventory inventory,FriendlyByteBuf buf){buf.readBlockPos();return new AcceleratorMenu(id,inventory,null);}
    private void refreshBays(){
        if(controller==null)return;
        bays=ComputationStructure.menuBays(controller);selectedBay=Math.max(0,Math.min(selectedBay,bays.size()-1));
    }
    private BaseAccelerator selected(){return selectedBay<bays.size()?bays.get(selectedBay):null;}
    private void refreshAdjustmentAvailability(){
        var host=selected();long now=controller.getLevel().getGameTime();
        if(host==settingsHost && now<nextSettingsPoll)return;
        settingsHost=host;nextSettingsPoll=now+20;
        for(int action=0;action<4;action++)adjustmentAvailability[action]=host!=null && canManageSelected(getPlayer())
                && host.canSetSettings(host.speedPower()+(action==0?-1:action==1?1:0),host.lanePower()+(action==2?-1:action==3?1:0))?1:0;
    }
    private boolean canManageSelected(Player player){
        var host=selected();return host!=null && controller.canAccess(player) && ComputationStructure.controls(controller,host)
                && appeng.util.Platform.hasPermissions(new appeng.api.util.DimensionalBlockPos(host),player);
    }
    private void requestPreview(int mode){
        previewRequested=true;previewMode=mode;previewOwner=selected()==null?null:selected().factoryId();previewRequest++;
        if(!controller.canAccess(getPlayer()) || mode==FactoryPreviewData.FACTORY && !canManageSelected(getPlayer())){
            capture=FactoryPreviewCapture.empty(previewRequest,mode,selected()==null?"no_cell":"denied");return;
        }
        try{capture=FactoryPreviewCapture.create(previewRequest,mode,controller,selected());}
        catch(RuntimeException failure){
            capture=FactoryPreviewCapture.empty(previewRequest,mode,"unavailable");
            org.slf4j.LoggerFactory.getLogger(AcceleratorMenu.class).debug("Factory preview unavailable",failure);
        }
    }
    public int value(int i){return data.get(i);}
    public FactoryPreviewData.Receiver previewData(){return received;}
    @Override protected void previewSynchronized(){received.accept(preview.payload());}
    @Override public void broadcastChanges(){
        if(controller!=null){
            refreshBays();
            refreshAdjustmentAvailability();
            if(!ComputationStructure.guiController(controller) || !controller.canAccess(getPlayer())){capture=null;setValidMenu(false);}
            else {
                var owner=selected()==null?null:selected().factoryId();
                selectedFactory=owner!=null && canManageSelected(getPlayer())?owner.toString():"";
                if(previewRequested && previewMode==FactoryPreviewData.FACTORY && !Objects.equals(owner,previewOwner))requestPreview(previewMode);
                if(capture!=null && !capture.finished() && previewMode==FactoryPreviewData.FACTORY && !canManageSelected(getPlayer()))requestPreview(previewMode);
                if(capture!=null && !capture.finished())preview=new PreviewSync(capture.next());
            }
        }
        super.broadcastChanges();
    }
    @Override public boolean stillValid(Player player){return super.stillValid(player) && (controller==null || ComputationStructure.guiController(controller) && controller.canAccess(player));}
    @Override public void removed(Player player){capture=null;super.removed(player);}
    @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
    @Override public boolean clickMenuButton(Player player,int action){
        if(controller==null || !ComputationStructure.guiController(controller) || !controller.canAccess(player) || action<0 || action>=10+ComputationStructure.MAX_UNITS)return false;
        refreshBays();
        if(action>=10){
            if(action-10>=bays.size())return false;
            selectedBay=action-10;requestPreview(previewMode);broadcastChanges();return true;
        }
        if(action>=5){
            if(action==5)requestPreview(FactoryPreviewData.FACTORY);
            if(action==6)requestPreview(FactoryPreviewData.STRUCTURE);
            if(action==7)requestPreview(previewMode);
            if(action==8 || action==9){selectedBay=Math.max(0,Math.min(bays.size()-1,selectedBay+(action==8?-1:1)));requestPreview(previewMode);}
            broadcastChanges();return true;
        }
        if(!canManageSelected(player))return false;var host=selected();
        if(action==4){boolean result=host.ejectFactoryFromController(player,controller);requestPreview(previewMode);broadcastChanges();return result;}
        int speed=host.speedPower(),lanes=host.lanePower();
        if(action==0)speed--;if(action==1)speed++;if(action==2)lanes--;if(action==3)lanes++;
        host.settings(speed,lanes);nextSettingsPoll=Long.MIN_VALUE;broadcastChanges();return true;
    }
}
