package io.netnotes.engine.ui;

import java.util.ArrayDeque;

/**
 * Thread-local pool implementation with adaptive sizing
 * 
 * HYBRID STRATEGY:
 * - Soft minimum (always kept warm): 8 objects
 * - Target size (adapts to usage): starts at 16, grows to 64
 * - Hard maximum (never exceed): 128 objects
 * - Growth trigger: Pool exhaustion during steady state
 * - Shrink trigger: Pool consistently underutilized
 * 
 * This prevents both:
 * - Constant allocation when pool too small
 * - Memory waste when pool too large
 * 
 * @param <S> The spatial region type
 */
public abstract class ThreadLocalSpatialRegionPool<S extends SpatialRegion<?,S>> 
        implements SpatialRegionPool<S> {
    
    private static final int SOFT_MIN_SIZE = 8;
    private static final int DEFAULT_TARGET_SIZE = 16;
    private static final int HARD_MAX_SIZE = 128;
    private static final int INITIAL_POOL_CAPACITY = 16;
    
    // Adaptive parameters
    private static final int GROWTH_THRESHOLD_MISSES = 5;  // Grow after N consecutive misses
    private static final int SHRINK_CHECK_INTERVAL = 100;  // Check for shrink every N recycles
    
    // Thread-local storage for the pool
    private final ThreadLocal<PoolData> threadLocalPool = ThreadLocal.withInitial(this::createPoolData);
    
    protected ThreadLocalSpatialRegionPool() {
    }


    
    private PoolData createPoolData() {
        return new PoolData();
    }
    
    @Override
    public S obtain() {
        PoolData data = threadLocalPool.get();
        data.totalObtained++;
        
        S region = data.pool.poll();
        if (region != null) {
            region.setToIdentity();
            data.consecutiveMisses = 0;  // Reset miss counter on hit
            return region;
        }
        
        // Pool miss - track for adaptive sizing
        data.consecutiveMisses++;
        
        // Consider growing pool if consistently missing
        if (data.consecutiveMisses >= GROWTH_THRESHOLD_MISSES) {
            considerGrowth(data);
            data.consecutiveMisses = 0;  // Reset after considering
        }
        
        // Pool empty - create new instance
        return createNew();
    }
    
    @Override
    public void recycle(S region) {
        if (region == null) return;
        
        PoolData data = threadLocalPool.get();
        
        // Only add to pool if not at hard maximum
        if (data.pool.size() < HARD_MAX_SIZE) {
            data.pool.offer(region);
            data.totalRecycled++;
            
            // Periodically check if we should shrink
            if (data.totalRecycled % SHRINK_CHECK_INTERVAL == 0) {
                considerShrink(data);
            }
        }
        // If at hard max, let GC handle it
    }
    
    /**
     * Consider growing the target pool size
     * Called when pool consistently misses (exhausts)
     */
    private void considerGrowth(PoolData data) {
        if (data.targetSize < HARD_MAX_SIZE) {
            // Double target size, but don't exceed hard max
            int newTarget = Math.min(data.targetSize * 2, HARD_MAX_SIZE);
            data.targetSize = newTarget;
            
            // Pre-warm the pool to new target
            int toCreate = newTarget - data.pool.size();
            for (int i = 0; i < toCreate && data.pool.size() < newTarget; i++) {
                data.pool.offer(createNew());
            }
        }
    }
    
    /**
     * Consider shrinking the pool
     * Called periodically during recycle operations
     */
    private void considerShrink(PoolData data) {
        int currentSize = data.pool.size();
        
        // If pool is consistently larger than target, reduce target
        if (currentSize > data.targetSize * 1.5 && data.targetSize > DEFAULT_TARGET_SIZE) {
            // Shrink target by 25%
            data.targetSize = Math.max(DEFAULT_TARGET_SIZE, 
                                      (int)(data.targetSize * 0.75));
        }
        
        // Trim pool down to target if significantly over
        while (data.pool.size() > data.targetSize && data.pool.size() > SOFT_MIN_SIZE) {
            data.pool.poll();  // Remove excess (oldest) objects
        }
    }
    
    @Override
    public PoolStats getStats() {
        PoolData data = threadLocalPool.get();
        return new PoolStats(
            data.pool.size(),
            data.totalObtained,
            data.totalRecycled
        );
    }
    
    @Override
    public void clear() {
        PoolData data = threadLocalPool.get();
        data.pool.clear();
        data.targetSize = DEFAULT_TARGET_SIZE;
        data.consecutiveMisses = 0;
    }
    
    /**
     * Create a new instance of the spatial region
     * Called when pool is empty
     */
    protected abstract S createNew();
    
    /**
     * Per-thread pool data
     */
    private class PoolData {
        final ArrayDeque<S> pool = new ArrayDeque<>(INITIAL_POOL_CAPACITY);
        int targetSize = DEFAULT_TARGET_SIZE;
        int consecutiveMisses = 0;
        int totalObtained = 0;
        int totalRecycled = 0;
        
        PoolData() {
            // Pre-warm pool to soft minimum
            for (int i = 0; i < SOFT_MIN_SIZE; i++) {
                pool.offer(createNew());
            }
        }
    }
}