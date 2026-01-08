package io.netnotes.engine.core.system;

import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.core.system.control.terminal.TerminalRenderState;
import io.netnotes.engine.core.system.control.terminal.TerminalRenderable;
import io.netnotes.engine.core.system.control.terminal.elements.Invalidatable;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.utils.LoggingHelpers.Log;

/**
 * TerminalScreen - Base class for all screens (REFACTORED for pull-based rendering)
 * 
 * DIRTY FLAG PATTERN:
 * - Screens track their own dirty state
 * - Call invalidate() after state changes
 * - ClientRenderManager polls needsRender()
 * - clearRenderFlag() called after successful render
 */
abstract class TerminalScreen implements TerminalRenderable, Invalidatable {
    protected final String name;
    protected SystemApplication systemApplication;
    protected TerminalScreen parent;
    private volatile boolean isShowing = false;
    
    // DIRTY FLAG for rendering
    private volatile boolean needsRender = false;
    
    public TerminalScreen(String name, SystemApplication terminal) {
        this.name = name;
        this.systemApplication = terminal;
    }
    
    public String getName() {
        return name;
    }
    
    public void setSystemApplication(SystemApplication systemApplication) {
        this.systemApplication = systemApplication;
    }
    
    public void setParent(TerminalScreen parent) {
        this.parent = parent;
    }
    
    public TerminalScreen getParent() {
        return parent;
    }
    
    /**
     * Called when screen becomes visible
     * 
     * Default implementation makes this screen active in ClientRenderManager.
     * Override to add custom initialization (event handlers, data loading, etc.)
     * 
     * IMPORTANT: Always call super.onShow() or manually set as active renderable
     */
    public CompletableFuture<Void> onShow() {
        if (isShowing) {
            Log.logMsg("[" + name + "] Already showing, skipping duplicate onShow()");
            return CompletableFuture.completedFuture(null);
        }
        
        isShowing = true;

        // Make this screen the active renderable
        systemApplication.setRenderable(this);
        
        // Mark for initial render
        invalidate();
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Called when screen becomes hidden
     * Override to cleanup (unregister handlers, release resources, etc.)
     */
    public void onHide() {
        isShowing = false;
        needsRender = false; // Clear dirty flag on hide
    }
    

    public boolean isShowing(){
        return isShowing;
    }

    /**
     * Get render state (PULL-BASED)
     * 
     * ClientRenderManager calls this to get what to draw.
     * This should be FAST and thread-safe.
     * 
     * Build RenderState from current screen state.
     * Don't modify state here - just read and return.
     */
    @Override
    public abstract TerminalRenderState getRenderState();
    
    /**
     * Check if needs rendering
     * Called by ClientRenderManager to check if worth polling
     */
    @Override
    public boolean needsRender() {
        return needsRender;
    }
    
    /**
     * Clear render flag after successful render
     * Called by ClientRenderManager after render completes
     */
    @Override
    public void clearRenderFlag() {
        this.needsRender = false;
    }
    
    /**
     * Mark as needing render
     * Call this after updating state to trigger redraw
     * 
     * Public so component renderables can call it
     */
    public void invalidate() {
        this.needsRender = true;
        // Notify container that renderable needs update
        systemApplication.invalidate();
    }
    
    /**
     * Handle messages (optional)
     * Override if screen needs to handle routed messages
     */
    public CompletableFuture<Void> handleMessage(RoutedPacket packet) {
        return CompletableFuture.completedFuture(null);
    }
}