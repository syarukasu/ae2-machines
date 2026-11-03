package com.syaru.ae2machines.client;

import com.syaru.ae2machines.platform.PlatformData;
import net.minecraft.client.resources.model.Material;

/** Retains AE2's connected geometry and power/error states, only replacing its artwork. */
public final class VirtualPylonTextures {
    private VirtualPylonTextures(){}
    public static Material remap(Material original){
        var name=original.texture().getPath();
        if(!name.contains("spatial_pylon"))return original;
        var state=name.substring(name.indexOf("spatial_pylon")+"spatial_pylon".length()).replaceFirst("^[_/]","");
        // Keep native online/error state logic; the virtual device uses AE2's violet/cyan palette.
        if(state.startsWith("red"))state="dim"+state.substring(3);
        return new Material(original.atlasLocation(),PlatformData.id("ae2machines:block/virtual_pylon/"+state));
    }
}
