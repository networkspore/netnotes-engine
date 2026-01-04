package io.netnotes.engine.core.system;

import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.core.system.control.terminal.ClientRenderManager.RenderState;
import io.netnotes.engine.core.system.control.terminal.ClientRenderManager.Renderable;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.utils.LoggingHelpers.Log;

/**
 * TerminalScreen - Base class for all screens (REFACTORED for pull-based rendering)
 */
abstract class TerminalScreen implements Renderable {
    protected final String name;
    protected SystemTerminalContainer terminal;
    protected TerminalScreen parent;
    private volatile boolean isShowing = false;
    
    public TerminalScreen(String name, SystemTerminalContainer terminal) {
        this.name = name;
        this.terminal = terminal;
    }
    
    public String getName() {
        return name;
    }
    
    public void setTerminal(SystemTerminalContainer terminal) {
        this.terminal = terminal;
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
     * IMPORTANT: Always call super.onShow() or manually activate in ClientRenderManager
     */
    public CompletableFuture<Void> onShow() {
        if (isShowing) {
            Log.logMsg("[" + name + "] Already showing, skipping duplicate onShow()");
            return CompletableFuture.completedFuture(null);
        }
        
        isShowing = true;

        // Make this screen active in ClientRenderManager
        terminal.getRenderManager().setActive(this);
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Called when screen becomes hidden
     * Override to cleanup (unregister handlers, release resources, etc.)
     *
     */
    public void onHide() {
        isShowing = false;
    }
    

    public boolean isShowing(){
        return this.isShowing;
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
    public abstract RenderState getRenderState();
    
    /**
     * Invalidate rendering
     * Call this after updating state to trigger redraw
     */
    protected void invalidate() {
        terminal.getRenderManager().invalidate();
    }
    
    /**
     * Handle messages (optional)
     * Override if screen needs to handle routed messages
     */
    public CompletableFuture<Void> handleMessage(RoutedPacket packet) {
        return CompletableFuture.completedFuture(null);
    }
    
  
}