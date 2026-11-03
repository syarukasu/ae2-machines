package com.syaru.ae2machines.platform;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraftforge.common.util.ITeleporter;
import java.util.function.Function;

public final class NativeEntityPlatform {
    public static Entity move(Entity entity,ServerLevel target) {
        var position=entity.position();var velocity=entity.getDeltaMovement();float yaw=entity.getYRot(),pitch=entity.getXRot();
        return entity.changeDimension(target,new ITeleporter(){
            public Entity placeEntity(Entity e,ServerLevel source,ServerLevel dest,float rotation,Function<Boolean,Entity> create){return create.apply(false);}
            public PortalInfo getPortalInfo(Entity e,ServerLevel dest,Function<ServerLevel,PortalInfo> fallback){return new PortalInfo(position,velocity,yaw,pitch);}
        });
    }
}
