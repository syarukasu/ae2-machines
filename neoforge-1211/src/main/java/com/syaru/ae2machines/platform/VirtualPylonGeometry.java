package com.syaru.ae2machines.platform;

import com.syaru.ae2machines.client.VirtualPylonTextures;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.neoforged.neoforge.client.model.geometry.*;
import java.util.function.Function;

final class VirtualPylonGeometry implements IUnbakedGeometry<VirtualPylonGeometry> {
    @Override public BakedModel bake(IGeometryBakingContext context,ModelBaker baker,Function<Material,TextureAtlasSprite> sprites,ModelState state,ItemOverrides overrides){
        return new appeng.client.render.tesr.spatial.SpatialPylonModel().bake(baker,m->sprites.apply(VirtualPylonTextures.remap(m)),state);
    }
}
