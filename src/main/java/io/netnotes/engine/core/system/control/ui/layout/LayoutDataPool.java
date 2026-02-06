package io.netnotes.engine.core.system.control.ui.layout;

/**
 * Pool for LayoutData instances
 * 
 * Manages recycling of LayoutData to minimize allocations
 * 
 * @param <LD> LayoutData type
 */
public interface LayoutDataPool<LD extends LayoutData<?,?,?,LD,?>>  {
    /**
     * Obtain a region from the pool
     * Returns a recycled region if available, otherwise creates new
     * 
     * @return A region from the pool (may contain stale data - caller must initialize)
     */
    LD obtain();
    
    /**
     * Return a region to the pool for reuse
     * 
     * @param region The region to recycle (caller must not use after recycling)
     */
    void recycle(LD region);
    
    /**
     * Clear the pool (for cleanup)
     */
    default void clear() {}

   
}
