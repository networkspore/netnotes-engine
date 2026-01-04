package io.netnotes.engine.core.system.control.terminal;

import io.netnotes.engine.utils.LoggingHelpers.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ClientRenderManager - Client-side pull-based rendering coordinator
 * 
 * This manages rendering on the CLIENT side (TerminalContainerHandle)
 * NOT to be confused with ConsoleRenderManager (server-side)
 * 
 * RESPONSIBILITIES:
 * 1. Track active Renderable
 * 2. Track generation for stale render detection
 * 3. Provide invalidate() to mark dirty
 * 4. Render when needed (converts Renderable → RenderState → BatchBuilder → execute)
 * 
 * PATTERN:
 * - User code calls setActive(renderable)
 * - User code or renderable calls invalidate()
 * - ClientRenderManager calls renderable.getRenderState()
 * - Converts RenderState to BatchBuilder
 * - Executes batch
 */
public class ClientRenderManager {
    
    private final TerminalContainerHandle terminal;
    private final AtomicReference<Renderable> activeRenderable = new AtomicReference<>(null);
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private final AtomicBoolean rendering = new AtomicBoolean(false);
    
    // Manual rendering (no auto-loop by default)
    private volatile boolean autoRender = false;
    private volatile boolean running = false;
    private CompletableFuture<Void> renderLoop;
    
    private static final long FRAME_TIME_MS = 16; // ~60fps if auto-render enabled
    
    public ClientRenderManager(TerminalContainerHandle terminal) {
        this.terminal = terminal;
    }
    
    // ===== ACTIVE RENDERABLE MANAGEMENT =====
    
    /**
     * Set active renderable
     * Increments generation and triggers render
     */
    public void setActive(Renderable renderable) {
        Renderable previous = activeRenderable.getAndSet(renderable);
        
        if (previous != renderable) {
            terminal.nextRenderGeneration(); // Use terminal's generation
            invalidate();
            
            Log.logMsg("[RenderManager] Active renderable changed (gen=" + 
                getCurrentGeneration() + ")");
        }
    }
    
    /**
     * Get active renderable
     */
    public Renderable getActive() {
        return activeRenderable.get();
    }
    
    /**
     * Clear active renderable
     */
    public void clearActive() {
        activeRenderable.set(null);
        dirty.set(false);
    }
    
    // ===== INVALIDATION =====
    
    /**
     * Mark as needing re-render
     * Doesn't increment generation - reuses current generation
     */
    public void invalidate() {
        dirty.set(true);
        
        if (autoRender) {
            // Auto-render will pick it up
        } else {
            // Manual mode - caller must call render()
        }
    }
    
    /**
     * Check if needs render
     */
    public boolean isDirty() {
        return dirty.get();
    }
    
    // ===== GENERATION MANAGEMENT =====
    
    /**
     * Get current generation
     */
    public long getCurrentGeneration() {
        return terminal.getCurrentRenderGeneration();
    }
    
    /**
     * Check if generation is current
     */
    public boolean isGenerationCurrent(long gen) {
        return getCurrentGeneration() == gen;
    }
    
    /**
     * Increment generation (call on layout changes like resize)
     */
    public void incrementGeneration() {
        terminal.nextRenderGeneration();
        invalidate();
    }
    
    // ===== RENDERING =====
    
    /**
     * Render current active renderable
     * 
     * Flow:
     * 1. Get active renderable
     * 2. Call renderable.getRenderState()
     * 3. Convert RenderState to BatchBuilder
     * 4. Execute batch with current generation
     * 
     * @return CompletableFuture that completes when render done
     */
    /**
     * Render current active renderable
     */
    public CompletableFuture<Void> render() {
        Renderable renderable = activeRenderable.get();
        
        if (renderable == null) {
            dirty.set(false);
            return CompletableFuture.completedFuture(null);
        }
        
        if (!dirty.compareAndSet(true, false)) {
            return CompletableFuture.completedFuture(null);
        }
        
        if (!rendering.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(null);
        }
        
        try {
            long currentGen = getCurrentGeneration(); // Get from terminal
            
            RenderState state = renderable.getRenderState();
            BatchBuilder batch = state.toBatch(terminal, currentGen);
            
            return terminal.executeBatch(batch)
                .whenComplete((v, ex) -> {
                    rendering.set(false);
                    
                    if (ex != null) {
                        Log.logError("[RenderManager] Render error: " + ex.getMessage());
                    }
                });
            
        } catch (Exception e) {
            rendering.set(false);
            Log.logError("[RenderManager] Render error: " + e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * Render only if dirty
     */
    public CompletableFuture<Void> renderIfNeeded() {
        if (isDirty()) {
            return render();
        }
        return CompletableFuture.completedFuture(null);
    }
    
    // ===== AUTO-RENDER MODE =====
    
    /**
     * Start auto-render loop (optional - for animations)
     */
    public void start() {
        if (running) return;
        
        running = true;
        autoRender = true;
        
        renderLoop = CompletableFuture.runAsync(() -> {
            while (running) {
                try {
                    renderIfNeeded().join();
                    Thread.sleep(FRAME_TIME_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    Log.logError("[ClientRenderManager] Loop error: " + e.getMessage());
                }
            }
        });
        
        Log.logMsg("[ClientRenderManager] Auto-render started");
    }
    
    /**
     * Stop auto-render loop
     */
    public void stop() {
        running = false;
        autoRender = false;
        
        if (renderLoop != null) {
            renderLoop.cancel(false);
            renderLoop = null;
        }
        
        Log.logMsg("[ClientRenderManager] Auto-render stopped");
    }
    
    // ===== INTERFACES =====
    
    /**
     * Renderable - something that can be rendered
     */
    @FunctionalInterface
    public interface Renderable {
        /**
         * Get current render state
         * Should be fast and thread-safe
         */
        RenderState getRenderState();
    }
    
    /**
     * RenderElement - adds commands to a batch
     */
    @FunctionalInterface
    public interface RenderElement {
        /**
         * Add rendering commands to batch
         * 
         * @param batch The batch to add commands to
         */
        void addToBatch(BatchBuilder batch);
    }
    
    /**
     * RenderState - collection of render elements
     */
    public static class RenderState {
        private final List<RenderElement> elements;
        
        private RenderState(List<RenderElement> elements) {
            this.elements = List.copyOf(elements);
        }
        
        /**
         * Convert to batch with generation check
         */
        public BatchBuilder toBatch(TerminalContainerHandle terminal, long generation) {
            BatchBuilder batch = terminal.batch(generation);
            
            for (RenderElement element : elements) {
                element.addToBatch(batch);
            }
            
            return batch;
        }
        
        /**
         * Get number of elements
         */
        public int getElementCount() {
            return elements.size();
        }
        
        public List<RenderElement> getElements(){
            return elements;
        }
        /**
         * Check if empty
         */
        public boolean isEmpty() {
            return elements.isEmpty();
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private final List<RenderElement> elements = new ArrayList<>();
            
            /**
             * Add single element
             */
            public Builder add(RenderElement element) {
                elements.add(element);
                return this;
            }
            
            /**
             * Add multiple elements
             */
            public Builder addAll(List<RenderElement> elements) {
                this.elements.addAll(elements);
                return this;
            }
            
            /**
             * Add multiple elements (varargs)
             */
            public Builder addAll(RenderElement... elements) {
                this.elements.addAll(List.of(elements));
                return this;
            }
            
            /**
             * Conditionally add element
             */
            public Builder addIf(boolean condition, RenderElement element) {
                if (condition) {
                    elements.add(element);
                }
                return this;
            }
            
            public RenderState build() {
                return new RenderState(elements);
            }
        }
    }
}