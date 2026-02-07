package io.netnotes.engine.ui;

import java.util.concurrent.CompletableFuture;

/**
 * Handle for Renderables to communicate with their LayoutManager
 * Replaces scattered Consumer<R> callbacks with unified interface
 * 
 * Design: Manager creates handle, injects into Renderable during registration
 * Benefits: Single attachment point, batch operations, type-safe queries
 */
public interface RenderableLayoutManagerHandle<R, LCB,G,GCE, GCB> {


    void markLayoutDirtyImmediate(R renderable);
  
    CompletableFuture<Void> flushLayout();
    

    // ==== LAYOUT GROUP API ====
    
    String getLayoutGroupIdByRenderable(R renderable);
    void createLayoutGroup(String groupId);
    void addToLayoutGroup(R renderable, String groupId);
    void removeLayoutGroupMember(R renderable);
    void destroyLayoutGroup(String groupId);
    void registerLayoutGroupCallback( String groupId, GCE entry);    
    void unregisterLayoutGroupCallback(String groupId, String callbackId);
    GCE getLayoutGroupCallback(String groupId, String callbackId);


    // ===== DIRTY MARKING =====
    
    void markLayoutDirty(R renderable);
    void markVisibilityDirty(R renderable);
    void markRequestPending(R renderable);
    
    // ===== LIFECYCLE =====
    
    void registerChild(R child, LCB callback);
    void unregister(R renderable);
    
    void migrateToFloating(R renderable, R anchor);
    void migrateToRegular(R renderable);
    /*
    
    void registerFloating(R renderable, LCB callback, R anchor);
    
    void unregisterFloating(R renderable);
    void markFloatingDirty(R renderable);
     */
    // ===== FOCUS =====
    
    void requestFocus(R renderable);
    
    // ===== BATCHING =====
    
    /**
     * Begin batching layout requests
     * Defers layout pass until endBatch()
     */
    void beginBatch();
    
    /**
     * End batching and trigger single layout pass
     */
    void endBatch();
    
    /**
     * Execute operations within a batch transaction
     * Ensures single layout pass regardless of how many dirty marks
     */
    default void batch(Runnable operations) {
        beginBatch();
        try {
            operations.run();
        } finally {
            endBatch();
        }
    }
}