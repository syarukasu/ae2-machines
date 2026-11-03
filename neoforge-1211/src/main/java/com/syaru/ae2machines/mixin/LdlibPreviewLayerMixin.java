package com.syaru.ae2machines.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Local;
import com.syaru.ae2machines.client.LdlibFactorySceneView;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;

/** Native dynamic models choose their layer from their own snapshot entity, not a static block table. */
@Pseudo
@Mixin(targets="com.lowdragmc.lowdraglib.client.scene.WorldSceneRenderer",remap=false)
public abstract class LdlibPreviewLayerMixin {
    @Shadow @Final public Level world;
    @WrapOperation(method="renderBlocks",at=@At(value="INVOKE",target="Lcom/lowdragmc/lowdraglib/client/scene/WorldSceneRenderer;canRenderInLayer(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/client/renderer/RenderType;)Z",remap=false),remap=false)
    private boolean snapshotLayer(BlockState state,RenderType layer,Operation<Boolean> original,@Local BlockPos pos,@Local(argsOnly=true) BlockRenderDispatcher dispatcher){
        if(!LdlibFactorySceneView.ownsSnapshot(world))return original.call(state,layer);
        var be=world.getBlockEntity(pos);var model=dispatcher.getBlockModel(state);
        var data=model.getModelData(world,pos,state,be==null?ModelData.EMPTY:be.getModelData());
        return model.getRenderTypes(state,RandomSource.create(state.getSeed(pos)),data).contains(layer);
    }
}
