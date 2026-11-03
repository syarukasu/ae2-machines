package com.syaru.ae2machines.platform;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.DimensionTransition;

public final class NativeEntityPlatform {
    public static Entity move(Entity entity,ServerLevel target) {
        return entity.changeDimension(new DimensionTransition(target,entity.position(),entity.getDeltaMovement(),entity.getYRot(),entity.getXRot(),DimensionTransition.DO_NOTHING));
    }
}
