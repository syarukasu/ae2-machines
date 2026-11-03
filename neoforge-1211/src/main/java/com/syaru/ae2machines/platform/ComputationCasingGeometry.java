package com.syaru.ae2machines.platform;

import com.syaru.ae2machines.client.ComputationCasingTiles;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import net.neoforged.neoforge.client.model.geometry.*;

final class ComputationCasingGeometry implements IUnbakedGeometry<ComputationCasingGeometry> {
    @Override public BakedModel bake(IGeometryBakingContext context,ModelBaker baker,Function<Material,TextureAtlasSprite> sprites,ModelState state,ItemOverrides overrides){
        var fallback=baker.bake(PlatformData.id("ae2machines:block/computation/casing"),state);
        var tiles=new BakedModel[ComputationCasingTiles.COUNT];
        for(int i=0;i<tiles.length;i++)tiles[i]=baker.bake(PlatformData.id(ComputationCasingTiles.model(i)),state);
        return new HousingModel(fallback,tiles);
    }
    private static final class HousingModel extends BakedModelWrapper<BakedModel> {
        private static final ModelProperty<Integer> TILE=new ModelProperty<>();
        private final BakedModel[] tiles;
        HousingModel(BakedModel fallback,BakedModel[] tiles){super(fallback);this.tiles=tiles;}
        @Override public ModelData getModelData(BlockAndTintGetter level,BlockPos pos,BlockState state,ModelData data){
            return data.derive().with(TILE,ComputationCasingTiles.index(state,pos,level::getBlockState)).build();
        }
        @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,ModelData data,RenderType type){
            var tile=data.get(TILE);
            return tile!=null && tile>=0?tiles[tile].getQuads(state,side,random,data,type):originalModel.getQuads(state,side,random,data,type);
        }
    }
}
