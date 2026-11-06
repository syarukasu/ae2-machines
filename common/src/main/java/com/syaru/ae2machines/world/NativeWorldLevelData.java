package com.syaru.ae2machines.world;

import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.WorldData;

/** World services and scheduled block/fluid events retain the unscaled world clock. */
public final class NativeWorldLevelData extends DerivedLevelData {
    private final NativeWorldState state;
    public NativeWorldLevelData(WorldData world, ServerLevelData wrapped, NativeWorldState state) {
        super(world,wrapped); this.state=state;
    }
    @Override public long getGameTime() { return state.ticks(); }
    @Override public long getDayTime() { return state.dayTime(); }
    @Override public GameRules getGameRules() { return state.rules(); }
    @Override public void setGameTime(long time) { state.setTime(time,state.dayTime()); }
    @Override public void setDayTime(long time) { state.setTime(state.ticks(),time); }
}
