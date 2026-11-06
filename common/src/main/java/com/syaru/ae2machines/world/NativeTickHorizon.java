package com.syaru.ae2machines.world;

import com.syaru.ae2machines.mixin.NativeChunkTicksAccess;
import com.syaru.ae2machines.mixin.NativeScheduledTicksAccess;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.LevelTicks;

/** Number of complete ticks strictly before a native callback. Never drains a queue. */
final class NativeTickHorizon {
    private NativeTickHorizon() {}

    static int before(long now, int offered, long event) {
        if (now < 0 || offered <= 0 || event <= now) return 0;
        return (int) Math.min(offered, event - now - 1);
    }

    static int world(ServerLevel world, int offered) {
        long now = world.getGameTime();
        return scheduled(world.getFluidTicks(), now, scheduled(world.getBlockTicks(), now, offered));
    }

    // At the native boundary, packing/loading is covered by the topology witness.
    // Use vanilla's live head index to catch a new earlier schedule without rescanning chunks.
    static boolean due(LevelTicks<?> ticks, long through) {
        var queues = (NativeScheduledTicksAccess<?>) ticks;
        for (var it = queues.machines$deadlines().values().iterator(); it.hasNext(); )
            if (it.nextLong() <= through) return true;
        return !queues.machines$collecting().isEmpty() || !queues.machines$running().isEmpty()
                || !queues.machines$ran().isEmpty() || !queues.machines$runningSet().isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static <T> int scheduled(LevelTicks<T> ticks, long now, int offered) {
        if (offered == 0 || ticks.getClass() != LevelTicks.class
                || !com.syaru.ae2machines.compat.mek.MekThermalHooks.safe(LevelTicks.class)
                || !com.syaru.ae2machines.compat.mek.MekThermalHooks.safe(LevelChunkTicks.class)) return 0;
        var queues = (NativeScheduledTicksAccess<T>) ticks;
        if (!queues.machines$collecting().isEmpty() || !queues.machines$running().isEmpty()
                || !queues.machines$ran().isEmpty() || !queues.machines$runningSet().isEmpty()) return 0;
        for(var it=queues.machines$deadlines().values().iterator();it.hasNext() && offered>0;)
            offered=before(now,offered,it.nextLong());
        return containers(queues.machines$containers().values(), now, offered, chunk -> {
            var pending = ((NativeChunkTicksAccess<T>) chunk).machines$packed();
            return pending != null && !pending.isEmpty();
        });
    }

    static <T> int containers(Iterable<LevelChunkTicks<T>> chunks, long now, int offered,
            Predicate<LevelChunkTicks<T>> packed) {
        int count = offered;
        for (var chunk : chunks) {
            if (chunk.getClass() != LevelChunkTicks.class || packed.test(chunk)) return 0;
            var head = chunk.peek();
            if (head != null) count = before(now, count, head.triggerTick());
            if (count == 0) break;
        }
        return count;
    }
}
