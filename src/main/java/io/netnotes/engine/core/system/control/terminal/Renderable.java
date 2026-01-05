package io.netnotes.engine.core.system.control.terminal;

import io.netnotes.engine.core.system.control.terminal.ClientTerminalRenderManager.RenderState;

/**
 * Renderable - something that can be rendered
 * 
 * PULL-BASED CONTRACT:
 * - Renderable stores its own state
 * - Returns RenderState when asked
 * - Tracks if it needs rendering
 * - Clears render flag after successful render
 */
public interface Renderable {
    /**
     * Get current render state
     * 
     * CRITICAL: This should be FAST and READ-ONLY
     * - Build and return RenderState from current internal state
     * - Do NOT modify state
     * - Do NOT send commands directly
     * - Thread-safe read of current state
     */
    RenderState getRenderState();
    
    /**
     * Check if needs rendering
     * 
     * Returns true if state has changed since last render
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