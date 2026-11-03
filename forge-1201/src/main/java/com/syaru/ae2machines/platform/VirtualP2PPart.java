package com.syaru.ae2machines.platform;

import appeng.api.parts.*;
import appeng.parts.p2p.P2PModels;
import appeng.parts.p2p.MEP2PTunnelPart;
import net.minecraft.resources.ResourceLocation;

/** Native channel transport. Virtual topology replacement is not enabled until validated. */
public final class VirtualP2PPart extends MEP2PTunnelPart {
    public static final ResourceLocation INPUT=new ResourceLocation("ae2machines","part/virtual_p2p_input");
    public static final ResourceLocation OUTPUT=new ResourceLocation("ae2machines","part/virtual_p2p_output");
    private static final P2PModels IN_MODELS=new P2PModels(INPUT), OUT_MODELS=new P2PModels(OUTPUT);
    public VirtualP2PPart(IPartItem<?> item) { super(item); }
    @Override public boolean onPartActivate(net.minecraft.world.entity.player.Player player,net.minecraft.world.InteractionHand hand,net.minecraft.world.phys.Vec3 pos){
        var stack=player.getItemInHand(hand);
        if(hand==net.minecraft.world.InteractionHand.MAIN_HAND && stack.getItem() instanceof appeng.api.implementations.items.IMemoryCard card){
            if(isClientSide())return true;
            var data=card.getData(stack);var id=ResourceLocation.tryParse(data.getString("p2pType"));
            var item=id==null?null:IPartItem.byId(id);
            if(item!=null && MEP2PTunnelPart.class.isAssignableFrom(item.getPartClass())){
                importSettings(appeng.util.SettingsFrom.MEMORY_CARD,data,player);
                card.notifyUser(player,appeng.api.implementations.items.MemoryCardMessages.SETTINGS_LOADED);return true;
            }
            card.notifyUser(player,appeng.api.implementations.items.MemoryCardMessages.INVALID_MACHINE);return false;
        }
        return super.onPartActivate(player,hand,pos);
    }
    @Override public MEP2PTunnelPart getInput(){
        var grid=getMainNode().getGrid();var input=grid==null || getFrequency()==0?null:appeng.me.service.P2PService.get(grid).getInput(getFrequency());
        return input instanceof MEP2PTunnelPart me?me:null;
    }
    @Override public java.util.stream.Stream<MEP2PTunnelPart> getOutputStream(){
        var grid=getMainNode().getGrid();return grid==null || !getMainNode().isOnline() || getFrequency()==0?java.util.stream.Stream.empty():appeng.me.service.P2PService.get(grid).getOutputs(getFrequency(),MEP2PTunnelPart.class);
    }
    @Override public IPartModel getStaticModels() { return (isOutput()?OUT_MODELS:IN_MODELS).getModel(isPowered(),isActive()); }
}
