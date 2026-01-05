package io.netnotes.engine.core.system.control.terminal;

import io.netnotes.engine.core.system.control.containers.Container;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.io.process.StreamChannel;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.state.BitFlagStateMachine;
import io.netnotes.engine.utils.LoggingHelpers.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ClientTerminalRenderManager - Client-side render coordinator
 * 
 * CORRECTED ARCHITECTURE:
 * - Manager is a FlowProcess that OWNS container handles as children
 * - Manager creates handles and tracks them automatically
 * - Handles inherit manager as parent via FlowProcess hierarchy
 * - Pure pull: manager polls its child handles for render state
 * 
 * OWNERSHIP HIERARCHY:
 * <pre>
 * Application Process
 *   â"œâ"€ ClientTerminalRenderManager (FlowProcess)
 *   â"‚   â"œâ"€ TerminalContainerHandle 1 (child)
 *   â"‚   â"œâ"€ TerminalContainerHandle 2 (child)
 *   â"‚   â""â"€ TerminalContainerHandle 3 (child)
 * </pre>
 * 
 * USAGE:
 * <pre>
 * // Create manager
 * ClientTerminalRenderManager manager = new ClientTerminalRenderManager(
 *     "render-manager", renderingServicePath
 * );
 * registry.registerChild(appPath, manager);
 * registry.startProcess(manager.getContextPath());
 * 
 * // Create containers through manager
 * TerminalContainerHandle terminal = manager.createTerminal("my-terminal")
 *     .size(80, 24)
 *     .build();
 * 
 * terminal.waitUntilReady().thenRun(() -> {
 *     terminal.setRenderable(myScreen);
 *     terminal.invalidate(); // Manager will pick it up
 * });
 * </pre>
 */
public class ClientTerminalRenderManager extends FlowProcess {
    
    private final ContextPath renderingServicePath;
    
    // Container tracking (via process registry)
    private final Map<NoteBytes, TerminalContainerHandle> handles = new ConcurrentHashMap<>();
    
    // Rendering coordination
    private final AtomicBoolean rendering = new AtomicBoolean(false);
    private volatile boolean running = false;
    private CompletableFuture<Void> renderLoop;
    
    private static final long FRAME_TIME_MS = 16; // ~60fps
    
    /**
     * Create render manager
     * 
     * @param name Process name
     * @param renderingServicePath Path to RenderingService
     */
    public ClientTerminalRenderManager(String name, ContextPath renderingServicePath) {
        super(name, ProcessType.BIDIRECTIONAL);
        this.renderingServicePath = renderingServicePath;
    }
    
    // ===== LIFECYCLE =====
    
    @Override
    public CompletableFuture<Void> run() {
        Log.logMsg("[ClientRenderManager] Starting at: " + contextPath);
        
        // Start render loop
        startRenderLoop();
        
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public void onStop() {
        Log.logMsg("[ClientRenderManager] Stopping");
        stopRenderLoop();
        
        // Cleanup all handles
        handles.clear();
        
        super.onStop();
    }
    
    // ===== CONTAINER CREATION =====
    
    /**
     * Create terminal container handle
     * 
     * Handle is registered as child of this manager
     * Returns builder for configuration
     * 
     * @param name Container name
     * @return Builder for configuration
     */
    public TerminalContainerHandle.TerminalBuilder createTerminal(String name) {
        
        // Override build to register with manager
        return new TerminalContainerHandle.TerminalBuilder(name) {
            @Override
            public TerminalContainerHandle build() {
                TerminalContainerHandle handle = super.build();
                
                // Register as child of manager
                registerChild(handle);
                
                // Track handle
                handles.put(handle.getId().toNoteBytes(), handle);
                
                Log.logMsg(String.format(
                    "[ClientRenderManager] Created handle: %s at %s",
                    handle.getId(), handle.getContextPath()
                ));
                
                // Start handle automatically
                startProcess(handle.getContextPath())
                    .exceptionally(ex -> {
                        Log.logError("[ClientRenderManager] Failed to start handle: " + 
                            ex.getMessage());
                        return null;
                    });
                
                return handle;
            }
        }.renderingService(renderingServicePath)
         .name(name);
    }
    
    /**
     * Get all container handles
     * 
     * Pure pull - gets children from process registry
     */
    private List<TerminalContainerHandle> getHandles() {
        // Option 1: From tracked map (faster)
        return new ArrayList<>(handles.values());
        
        // Option 2: From process registry (more reliable)
        // return getChildren().stream()
        //     .filter(p -> p instanceof TerminalContainerHandle)
        //     .map(p -> (TerminalContainerHandle) p)
        //     .toList();
    }
    
    /**
     * Get handle by ID
     */
    public TerminalContainerHandle getHandle(NoteBytes containerId) {
        return handles.get(containerId);
    }
    
    /**
     * Get container count
     */
    public int getContainerCount() {
        return handles.size();
    }
    
    /**
     * Destroy handle
     * 
     * @param containerId Container ID to destroy
     */
    public CompletableFuture<Void> destroyHandle(NoteBytes containerId) {
        TerminalContainerHandle handle = handles.remove(containerId);
        if (handle == null) {
            return CompletableFuture.completedFuture(null);
        }
        
        return handle.destroy()
            .thenRun(() -> {
                registry.unregisterProcess(handle.getContextPath());
                Log.logMsg("[ClientRenderManager] Destroyed handle: " + containerId);
            });
    }
    
    // ===== RENDERING =====
    
    /**
     * Render all dirty containers
     * 
     * PURE PULL MODEL:
     * 1. Get all handles (our children)
     * 2. Check each handle's dirty flag
     * 3. Check readiness (STATE_ACTIVE, stream ready)
     * 4. Get renderable from handle
     * 5. Render focused handle first, then others
     * 6. Clear dirty flags on success
     * 
     * @return CompletableFuture that completes when all renders done
     */
    public CompletableFuture<Void> render() {
        if (!rendering.compareAndSet(false, true)) {
            // Already rendering
            return CompletableFuture.completedFuture(null);
        }
        
        try {
            List<CompletableFuture<Void>> renderFutures = new ArrayList<>();
            
            // Get all handles (PULL from our children)
            List<TerminalContainerHandle> handleList = getHandles();
            
            // Find focused handle first (priority)
            TerminalContainerHandle focusedHandle = handleList.stream()
                .filter(TerminalContainerHandle::isContainerFocused)
                .findFirst()
                .orElse(null);
            
            if (focusedHandle != null) {
                CompletableFuture<Void> future = renderHandle(focusedHandle);
                if (future != null) {
                    renderFutures.add(future);
                }
            }
            
            // Render other dirty handles
            for (TerminalContainerHandle handle : handleList) {
                // Skip if already rendered (focused)
                if (handle == focusedHandle) {
                    continue;
                }
                
                CompletableFuture<Void> future = renderHandle(handle);
                if (future != null) {
                    renderFutures.add(future);
                }
            }
            
            // Wait for all renders to complete
            return CompletableFuture.allOf(
                renderFutures.toArray(new CompletableFuture[0])
            ).whenComplete((v, ex) -> {
                rendering.set(false);
                
                if (ex != null) {
                    Log.logError("[ClientRenderManager] Render error: " + ex.getMessage());
                }
            });
            
        } catch (Exception e) {
            rendering.set(false);
            Log.logError("[ClientRenderManager] Render error: " + e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * Render single handle
     * 
     * Pure pull model - reads all state from handle
     * 
     * @return CompletableFuture or null if not ready
     */
    private CompletableFuture<Void> renderHandle(TerminalContainerHandle handle) {
        // PULL: Check if dirty
        if (!handle.isDirty()) {
            return null;
        }
        
        // PULL: Get renderable from handle
        Renderable renderable = handle.getRenderable();
        if (renderable == null) {
            handle.clearDirtyFlag();
            return null;
        }
        
        // PULL: Check handle readiness
        BitFlagStateMachine.StateSnapshot snap = handle.getStateSnapshot();
        
        if (!snap.hasState(Container.STATE_ACTIVE)) {
            // Container not active, defer render
            return null;
        }
        
        if (!handle.isRenderStreamReady()) {
            // Stream not ready, defer render
            return null;
        }
        
        // PULL: Check if renderable needs rendering
        if (!renderable.needsRender()) {
            handle.clearDirtyFlag();
            return null;
        }
        
        try {
            long currentGen = handle.getCurrentRenderGeneration();
            
            // PULL: Get render state from renderable
            RenderState state = renderable.getRenderState();
            
            // Convert to batch
            BatchBuilder batch = state.toBatch(handle, currentGen);
            
            // Execute batch
            return handle.executeBatch(batch)
                .thenRun(() -> {
                    // Clear flags on success
                    handle.clearDirtyFlag();
                    renderable.clearRenderFlag();
                    
                    Log.logMsg(String.format(
                        "[ClientRenderManager] Rendered %s (gen=%d, %d elements)",
                        handle.getId(), currentGen, state.getElementCount()
                    ));
                })
                .exceptionally(ex -> {
                    Log.logError(String.format(
                        "[ClientRenderManager] Render failed for %s: %s",
                        handle.getId(), ex.getMessage()
                    ));
                    return null;
                });
            
        } catch (Exception e) {
            Log.logError(String.format(
                "[ClientRenderManager] Render error for %s: %s",
                handle.getId(), e.getMessage()
            ));
            return null;
        }
    }
    
    // ===== RENDER LOOP =====
    
    /**
     * Start auto-render loop
     * 
     * Continuously polls child handles and renders dirty ones
     */
    private void startRenderLoop() {
        if (running) {
            return;
        }
        
        running = true;
        
        renderLoop = CompletableFuture.runAsync(() -> {
            Log.logMsg("[ClientRenderManager] Render loop started");
            
            while (running) {
                try {
                    render().join();
                    Thread.sleep(FRAME_TIME_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    Log.logError("[ClientRenderManager] Loop error: " + e.getMessage());
                }
            }
            
            Log.logMsg("[ClientRenderManager] Render loop stopped");
        });
    }
    
    /**
     * Stop render loop
     */
    private void stopRenderLoop() {
        running = false;
        
        if (renderLoop != null) {
            renderLoop.cancel(false);
            renderLoop = null;
        }
    }
    
    /**
     * Check if running
     */
    public boolean isRunning() {
        return running;
    }
    
    // ===== MESSAGE HANDLING =====
    
    @Override
    public CompletableFuture<Void> handleMessage(RoutedPacket packet) {
        // Manager doesn't handle messages
        // All interaction is via method calls
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public void handleStreamChannel(StreamChannel channel, ContextPath fromPath) {
        // Manager doesn't handle streams
        throw new UnsupportedOperationException(
            "ClientTerminalRenderManager does not handle streams");
    }
    
    // ===== INTERFACES =====
    
    /**
     * RenderElement - adds commands to a batch
     */
    @FunctionalInterface
    public interface RenderElement {
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
        
        public BatchBuilder toBatch(TerminalContainerHandle terminal, long generation) {
            BatchBuilder batch = terminal.batch(generation);
            
            for (RenderElement element : elements) {
                element.addToBatch(batch);
            }
            
            return batch;
        }
        
        public int getElementCount() {
            return elements.size();
        }
        
        public List<RenderElement> getElements() {
            return elements;
        }
        
        public boolean isEmpty() {
            return elements.isEmpty();
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private final List<RenderElement> elements = new ArrayList<>();
            
            public Builder add(RenderElement element) {
                elements.add(element);
                return this;
            }
            
            public Builder addAll(List<RenderElement> elements) {
                this.elements.addAll(elements);
                return this;
            }
            
            public Builder addAll(RenderElement... elements) {
                this.elements.addAll(List.of(elements));
                return this;
            }
            
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