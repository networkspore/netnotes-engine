package io.netnotes.engine.ui.renderer;

import java.util.ArrayList;
import java.util.List;

import io.netnotes.engine.ui.SpatialPoint;
import io.netnotes.engine.ui.SpatialRegion;
import io.netnotes.engine.ui.SpatialRegionPool;

public class DamageAccumulator<
    P extends SpatialPoint<P>,
    S extends SpatialRegion<P,S>
> implements AutoCloseable {
    private static final int MAX_REGIONS = 8;
    private final List<S> regions = new ArrayList<>(4);
    private final SpatialRegionPool<S> pool;
    
    public DamageAccumulator(SpatialRegionPool<S> pool) {
        this.pool = pool;
    }
    
    public void add(S region) {
        for (int i = 0; i < regions.size(); i++) {
            S existing = regions.get(i);
            if (existing.contains(region)) {
                pool.recycle(region);
                return;
            }
            if (region.contains(existing)) {
                pool.recycle(existing);
                regions.set(i, region);
                return;
            }
            if (existing.intersects(region)) {
                S merged = existing.union(region);
                pool.recycle(existing);
                pool.recycle(region);
                regions.set(i, merged);
                return;
            }
        }
        
        regions.add(region);
        
        if (regions.size() > MAX_REGIONS) {
            collapseToUnion();
        }
    }
    
    public List<S> drainRegions() {
        List<S> snapshot = new ArrayList<>(regions);
        regions.clear();
        return snapshot;
    }
    
    public void clear() {
        regions.forEach(pool::recycle);
        regions.clear();
    }
    
    private void collapseToUnion() {
        S union = regions.get(0);
        for (int i = 1; i < regions.size(); i++) {
            S merged = union.union(regions.get(i));
            pool.recycle(union);
            pool.recycle(regions.get(i));
            union = merged;
        }
        regions.clear();
        regions.add(union);
    }

    @Override
    public void close() throws Exception {
        clear();
    }
}