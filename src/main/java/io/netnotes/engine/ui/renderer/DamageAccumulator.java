package io.netnotes.engine.ui.renderer;

import java.util.ArrayList;
import java.util.List;

import io.netnotes.engine.ui.SpatialPoint;
import io.netnotes.engine.ui.SpatialRegion;
import io.netnotes.engine.ui.SpatialRegionPool;

public class DamageAccumulator<
    P extends SpatialPoint<P>,
    S extends SpatialRegion<P,S>
>  {
    private static final int COLLAPSE_THRESHOLD = 32; 

    private int collapseThreshold;
    
    private final List<S> regions = new ArrayList<>(4);
    private final SpatialRegionPool<S> pool;
    
    public DamageAccumulator(SpatialRegionPool<S> pool){
        this(pool, COLLAPSE_THRESHOLD);
    }

    public DamageAccumulator(SpatialRegionPool<S> pool, int collapseThreshold) {
        this.pool = pool;
        this.collapseThreshold = collapseThreshold;
    }
    
    public void add(S region) {
        if (region.isEmpty()) {
            pool.recycle(region);
            return;
        }

        boolean merged;
        do {

            merged = false;
            for (int i = 0; i < regions.size(); i++) {
                S existing = regions.get(i);
           
                if (existing.contains(region)) {
                    pool.recycle(region);
                    return;
                }
                if (region.contains(existing)) {
                    pool.recycle(existing);
                    regions.set(i, region);
                    // Don't return — the new region might absorb more entries
                    region = regions.get(i); // continue with promoted region
                    // remove and re-add to continue checking remaining entries
                    regions.remove(i);
                    i--;
                    merged = true;
                    continue;
                }
                if (existing.intersects(region)) {
                    S mergedRegion = existing.union(region);
                    pool.recycle(existing);
                    pool.recycle(region);
                    regions.remove(i);
                    region = mergedRegion;
                    i--;
                    merged = true;
                    continue;
                }
            }
       } while (merged);

        regions.add(region);
    }
    
    public List<S> drainRegions() {
        if (regions.size() > collapseThreshold) {
            collapseToUnion();
        }
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

    public boolean isEmpty(){
        return regions.isEmpty();
    }


    public int getCollapseThreshold() {
        return collapseThreshold;
    }

    public void setCollapseThreshold(int collapseThreshold) {
        this.collapseThreshold = collapseThreshold;
    }

    
}