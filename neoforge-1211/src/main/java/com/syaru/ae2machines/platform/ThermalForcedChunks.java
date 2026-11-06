package com.syaru.ae2machines.platform;
import net.minecraft.server.level.ServerLevel;
public final class ThermalForcedChunks {
    private ThermalForcedChunks(){}
    public static boolean present(ServerLevel level){return net.neoforged.neoforge.common.world.chunk.ForcedChunkManager.hasForcedChunks(level);}
}
