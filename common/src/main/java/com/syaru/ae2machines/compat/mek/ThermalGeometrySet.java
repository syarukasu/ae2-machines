package com.syaru.ae2machines.compat.mek;

import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;

/** Native iteration/storage, with exact structural arithmetic between membership changes. */
public final class ThermalGeometrySet extends ObjectOpenHashSet<BlockPos> {
    private long revision;
    private boolean immutableElements = true;
    private volatile boolean hashReady;
    private int cachedHash;
    private record Comparison(WeakReference<ThermalGeometrySet> peer, long ownRevision,
                              long peerRevision, boolean equal) {}
    private transient volatile Comparison comparison;
    private static long hashHits, equalityHits, fullHashes, fullEqualities;

    private void changed() { revision++; hashReady = false; comparison = null; }

    @Override public boolean add(BlockPos pos) {
        if (pos != null && pos.getClass() != BlockPos.class) immutableElements = false;
        boolean result = super.add(pos);
        if (result) changed();
        return result;
    }

    @Override public boolean remove(Object pos) {
        boolean result = super.remove(pos);
        if (result) changed();
        return result;
    }

    @Override public BlockPos addOrGet(BlockPos pos) {
        if (pos != null && pos.getClass() != BlockPos.class) immutableElements = false;
        int before = size();
        var result = super.addOrGet(pos);
        if (size() != before) changed();
        return result;
    }

    @Override public void clear() { super.clear(); changed(); }

    @Override public ObjectIterator<BlockPos> iterator() {
        var nativeIterator = super.iterator();
        return new ObjectIterator<>() {
            public boolean hasNext() { return nativeIterator.hasNext(); }
            public BlockPos next() { return nativeIterator.next(); }
            public void remove() { nativeIterator.remove(); changed(); }
        };
    }

    @Override public boolean addAll(Collection<? extends BlockPos> values) {
        changed();
        for (var pos : values) if (pos != null && pos.getClass() != BlockPos.class) immutableElements = false;
        return super.addAll(values);
    }

    @Override public boolean removeAll(Collection<?> values) {
        changed();
        return super.removeAll(values);
    }

    @Override public boolean retainAll(Collection<?> values) {
        changed();
        return super.retainAll(values);
    }

    @Override public boolean removeIf(Predicate<? super BlockPos> filter) {
        changed();
        return super.removeIf(filter);
    }

    @Override public int hashCode() {
        if (immutableElements && hashReady) { hashHits++; return cachedHash; }
        fullHashes++;
        int result = super.hashCode();
        if (immutableElements) { cachedHash = result; hashReady = true; }
        return result;
    }

    @Override public boolean equals(Object other) {
        if (other == this) return true;
        if (!(other instanceof ThermalGeometrySet set) || !immutableElements || !set.immutableElements)
            return super.equals(other);
        var cached = comparison;
        if (cached != null && cached.peer().get() == set && cached.ownRevision() == revision && cached.peerRevision() == set.revision) {
            equalityHits++;
            return cached.equal();
        }
        fullEqualities++;
        boolean result = super.equals(other);
        // Concurrent readers must not see one peer with another comparison's result.
        comparison = new Comparison(new WeakReference<>(set), revision, set.revision, result);
        return result;
    }

    public static long[] statistics() { return new long[]{hashHits, equalityHits, fullHashes, fullEqualities}; }
}
