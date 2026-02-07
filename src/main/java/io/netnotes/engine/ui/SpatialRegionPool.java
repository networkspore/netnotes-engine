package io.netnotes.engine.ui;

/**
 * Pool interface for spatial regions to eliminate allocation overhead
 * 
 * Thread-local pools ensure thread safety without synchronization.
 * Each thread maintains its own pool of reusable region objects.
 * 
 * @param <S> The spatial region type
 */
public interface SpatialRegionPool<S extends SpatialRegion<?,S>> {
    
    /**
     * Obtain a region from the pool
     * Returns a recycled region if available, otherwise creates new
     * 
     * @return A region from the pool (may contain stale data - caller must initialize)
     */
    S obtain();
    
    /**
     * Return a region to the pool for reuse
     * 
     * @param region The region to recycle (caller must not use after recycling)
     */
    void recycle(S region);
    
    /**
     * Get pool statistics (for debugging/monitoring)
     */
    default PoolStats getStats() {
        return new PoolStats(0, 0, 0);
    }
    
    /**
     * Clear the pool (for cleanup)
     */
    default void clear() {}

    /**
     * Obtain a region wrapped for auto-recycling
     * Use with try-with-resources
     * 
     * @return Auto-recycling wrapper
     */
    default PooledRegion<S> obtainPooled() {
        return new PooledRegion<>(this, obtain());
    }
    
    /**
     * Pool statistics
     */
    class PoolStats {
        public final int poolSize;      // Current number of pooled objects
        public final int totalObtained; // Total objects obtained (lifetime)
        public final int totalRecycled; // Total objects recycled (lifetime)
        
        public PoolStats(int poolSize, int totalObtained, int totalRecycled) {
            this.poolSize = poolSize;
            this.totalObtained = totalObtained;
            this.totalRecycled = totalRecycled;
        }
        
        @Override
        public String toString() {
            return String.format("Pool[size=%d, obtained=%d, recycled=%d, created=%d]",
                poolSize, totalObtained, totalRecycled, totalObtained - totalRecycled);
        }
    }
}