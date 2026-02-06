package io.netnotes.engine.core.system.control.ui;


/**
 * PooledRegion - Auto-recycling wrapper for spatial regions
 * 
 * SAFETY GUARANTEE: Using try-with-resources ensures region is recycled.
 * After close(), the region reference is poisoned and throws on access.
 * 
 * Usage:
 * <pre>
 * try (PooledRegion<UIRectangle> pr = pool.obtainPooled()) {
 *     UIRectangle rect = pr.get();
 *     rect.set(x, y, w, h);
 *     parent.invalidateChild(rect);
 * } // Automatically recycled here, rect becomes unusable
 * </pre>
 * 
 * @param <S> The spatial region type
 */
public final class PooledRegion<S extends SpatialRegion<?,S>> implements AutoCloseable {
    
    private final SpatialRegionPool<S> pool;
    private S region;
    private boolean closed = false;
    
    PooledRegion(SpatialRegionPool<S> pool, S region) {
        this.pool = pool;
        this.region = region;
    }
    
    /**
     * Get the underlying region
     * 
     * @return The spatial region
     * @throws IllegalStateException if already closed
     */
    public S get() {
        if (closed) {
            throw new IllegalStateException(
                "Region already recycled - cannot use after close()"
            );
        }
        return region;
    }
    
    /**
     * Check if this pooled region is still open
     */
    public boolean isOpen() {
        return !closed;
    }
    
    /**
     * Manually recycle the region
     * Automatically called by try-with-resources
     */
    @Override
    public void close() {
        if (!closed) {
            closed = true;
            if (region != null) {
                pool.recycle(region);
                region = null;  // Poison the reference
            }
        }
    }
    
    /**
     * Leak the region (take ownership, prevent auto-recycle)
     * Use with caution - caller becomes responsible for recycling
     * 
     * @return The region (caller must manually recycle later)
     */
    public S leak() {
        if (closed) {
            throw new IllegalStateException("Region already closed");
        }
        S leaked = region;
        region = null;
        closed = true;  // Prevent double-recycle
        return leaked;
    }
}