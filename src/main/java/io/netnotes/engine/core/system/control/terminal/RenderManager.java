package io.netnotes.engine.core.system.control.terminal;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import io.netnotes.engine.utils.LoggingHelpers.Log;

/**
 * RenderManager - Single owner of rendering (pull-based)
 * 
 * KEY PRINCIPLE: Nothing renders itself. Only RenderManager renders.
 * 
 * Components (screens, menus, etc) are PASSIVE:
 * - They expose state (what to draw)
 * - They DO NOT draw themselves
 * 
 * RenderManager is ACTIVE:
 * - It polls for changes
 * - It decides when to render
 * - It calls getRenderState() on the active component
 * - It batches all rendering within a single generation
 * 
 * Generation Management:
 * - Generation increments ONLY when setActive() is called
 * - All rendering within a batch uses the SAME generation
 * - This prevents mid-batch generation conflicts
 */
public class RenderManager {
    
    private final TerminalContainerHandle terminal;
    private final AtomicLong generation = new AtomicLong(0);
    
    // The ONE active renderable
    private Renderable activeRenderable = null;
    private volatile boolean dirty = true;
    
    // Render loop control
    private volatile boolean running = false;
    private CompletableFuture<Void> renderLoop;
    
    // Frame rate control
    private static final long FRAME_TIME_MS = 16; // ~60fps
    
    // Rendering state
    private final AtomicBoolean isRendering = new AtomicBoolean(false);
    
    public RenderManager(TerminalContainerHandle terminal) {
        this.terminal = terminal;
    }
    
    /**
     * Start the render loop (pull-based)
     */
    public void start() {
        if (running) return;
        
        running = true;
        renderLoop = CompletableFuture.runAsync(this::renderLoopImpl);
        Log.logMsg("[RenderManager] Render loop started");
    }
    
    /**
     * Stop the render loop
     */
    public void stop() {
        running = false;
        if (renderLoop != null) {
            renderLoop.cancel(false);
        }
        Log.logMsg("[RenderManager] Render loop stopped");
    }
    
    /**
     * The render loop - PULLS state and renders
     */
    private void renderLoopImpl() {
        while (running) {
            try {
                // PULL: Check if anything needs rendering
                if (dirty && activeRenderable != null && !isRendering.get()) {
                    
                    // Mark as rendering to prevent concurrent renders
                    if (isRendering.compareAndSet(false, true)) {
                        try {
                            // Get current generation ONCE for entire batch
                            long currentGen = generation.get();
                            
                            // PULL: Get current state and render it
                            renderContent(activeRenderable, currentGen)
                                .thenRun(() -> {
                                    dirty = false;
                                    isRendering.set(false);
                                })
                                .exceptionally(ex -> {
                                    Log.logError("[RenderManager] Render failed: " + ex.getMessage());
                                    isRendering.set(false);
                                    return null;
                                });
                            
                        } catch (Exception e) {
                            Log.logError("[RenderManager] Render setup error: " + e.getMessage());
                            isRendering.set(false);
                        }
                    }
                }
                
                // Sleep briefly
                Thread.sleep(FRAME_TIME_MS);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                Log.logError("[RenderManager] Render loop error: " + e.getMessage());
            }
        }
    }
    
    /**
     * Render the given component (PULL its state and draw)
     * 
     * CRITICAL: This entire method operates under a SINGLE generation.
     * Generation is captured at the start and used throughout the batch.
     */
    private CompletableFuture<Void> renderContent(Renderable renderable, long gen) {
        try {
            // PULL: Ask component for its current state
            RenderState state = renderable.getRenderState();
            
            if (state == null || state.getElements().isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            
            // Render the state within a batch, using the SAME generation throughout
            return terminal.batchWithGeneration(gen, () -> {
                drawState(state, gen);
            });
            
        } catch (Exception e) {
            Log.logError("[RenderManager] Render error: " + e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * Draw the render state
     * 
     * This is called within a batch, so all operations use the same generation.
     */
    private void drawState(RenderState state, long gen) {
        // Clear screen first
        terminal.clear(gen);
        
        // Draw each element from state
        for (RenderElement element : state.getElements()) {
            try {
                element.draw(terminal, gen);
            } catch (Exception e) {
                Log.logError("[RenderManager] Element draw error: " + e.getMessage());
            }
        }
    }
    
    /**
     * Set the active renderable (changes screen)
     * 
     * This is the ONLY place where generation should increment.
     * Incrementing here ensures:
     * - Old renders from previous screen are invalidated
     * - New screen gets a clean generation to render with
     */
    public long setActive(Renderable renderable) {
        // Notify old renderable it's being deactivated
        if (this.activeRenderable != null && this.activeRenderable != renderable) {
            try {
                this.activeRenderable.onInactive();
            } catch (Exception e) {
                Log.logError("[RenderManager] onInactive error: " + e.getMessage());
            }
        }
        
        this.activeRenderable = renderable;
        
        // Increment generation for new screen
        long gen = generation.incrementAndGet();
        
        // Also increment terminal's generation to stay in sync
        terminal.nextRenderGeneration();
        
        dirty = true;
        
        // Notify new renderable it's being activated
        if (renderable != null) {
            try {
                renderable.onActive();
            } catch (Exception e) {
                Log.logError("[RenderManager] onActive error: " + e.getMessage());
            }
        }
        
        Log.logMsg("[RenderManager] Active renderable changed, gen=" + gen);
        return gen;
    }
    
    /**
     * Mark as dirty (needs redraw)
     * Components call this, but DON'T render themselves
     * 
     * This does NOT increment generation - we reuse the current generation.
     */
    public void invalidate() {
        dirty = true;
    }
    
    /**
     * Handle resize - just invalidate
     */
    public void onResize(int width, int height) {
        Log.logMsg("[RenderManager] Resize: " + width + "x" + height);
        invalidate();
    }
    
    /**
     * Get current generation
     */
    public long getCurrentGeneration() {
        return generation.get();
    }
    
    /**
     * Get active renderable
     */
    public Renderable getActiveRenderable() {
        return activeRenderable;
    }
    
    /**
     * Check if dirty (needs render)
     */
    public boolean isDirty() {
        return dirty;
    }
    
    /**
     * Check if currently rendering
     */
    public boolean isRendering() {
        return isRendering.get();
    }
    
    // ===== INTERFACES =====
    
    /**
     * Interface for renderable components
     * Components are PASSIVE - they expose state, not behavior
     */
    public interface Renderable {
        /**
         * Get current render state (PULL)
         * This should be fast - just return current state
         * 
         * IMPORTANT: This may be called from render thread, so must be thread-safe
         */
        RenderState getRenderState();
        
        /**
         * Called when becoming inactive (for cleanup)
         */
        default void onInactive() {}
        
        /**
         * Called when becoming active (for setup)
         */
        default void onActive() {}
    }
    
    /**
     * Render state - describes WHAT to draw
     * Immutable snapshot of visual state
     */
    public static class RenderState {
        private final java.util.List<RenderElement> elements;
        
        public RenderState(java.util.List<RenderElement> elements) {
            this.elements = elements;
        }
        
        public java.util.List<RenderElement> getElements() {
            return elements;
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private final java.util.List<RenderElement> elements = new java.util.ArrayList<>();
            
            public Builder add(RenderElement element) {
                elements.add(element);
                return this;
            }
            
            public Builder addAll(java.util.List<RenderElement> elements) {
                this.elements.addAll(elements);
                return this;
            }
            
            public RenderState build() {
                return new RenderState(new java.util.ArrayList<>(elements));
            }
        }
    }
    
    /**
     * Render element - a drawable thing
     * 
     * Each element knows how to draw itself on the terminal.
     * Elements should be lightweight and fast to create.
     */
    public interface RenderElement {
        /**
         * Draw this element to the terminal
         * 
         * @param terminal The terminal to draw on
         * @param generation The render generation (for batching)
         */
        void draw(TerminalContainerHandle terminal, long generation);
    }
}