package com.syaru.ae2machines.mixin;

import com.syaru.ae2machines.client.LdlibFactorySceneView;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** LDLib must not reuse the player's model-data cache for our detached cell image. */
@Pseudo
@Mixin(targets="com.lowdragmc.lowdraglib.client.scene.forge.WorldSceneRendererImpl",remap=false)
public abstract class LdlibPreviewModelDataMixin {
    @Inject(method="getModelData",at=@At("HEAD"),cancellable=true,remap=false)
    private static void snapshotModel(BakedModel model,BlockState state,BlockPos pos,BlockAndTintGetter world,CallbackInfoReturnable<ModelData> result){
        if(!(world instanceof Level level) || !LdlibFactorySceneView.ownsSnapshot(level))return;
        var be=world.getBlockEntity(pos);
        result.setReturnValue(model.getModelData(world,pos,state,be==null?ModelData.EMPTY:be.getModelData()));
    }
}
