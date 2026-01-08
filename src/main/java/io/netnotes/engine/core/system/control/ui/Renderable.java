package io.netnotes.engine.core.system.control.ui;

/**
 * Renderable - something that can be rendered
 * 
 * PULL-BASED CONTRACT:
 * - Renderable stores its own state
 * - Returns RenderState when asked
 * - Tracks if it needs rendering
 * - Clears render flag after successful render
 */
/**
 * Renderable - something that can be rendered
 * 
 * @param <B> BatchBuilder type
 * @param <E> RenderElement type
 */
public interface Renderable<B extends BatchBuilder, E extends RenderElement<B>> {
    /**
     * Get current render state
     */
    RenderState<B, E> getRenderState();
    
    /**
     * Check if needs rendering
     */
    default boolean needsRender() {
        return true;
    }
    
    /**
     * Clear render flag after successful render
     */
    default void clearRenderFlag() {
        // Default: no-op
    }
}