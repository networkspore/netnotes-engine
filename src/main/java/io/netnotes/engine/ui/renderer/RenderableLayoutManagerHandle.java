package io.netnotes.engine.ui.renderer;

import java.util.concurrent.CompletableFuture;

/**
 * Handle for Renderables to communicate with their LayoutManager
 * Replaces scattered Consumer<R> callbacks with unified interface
 * 
 * Design: Manager creates handle, injects into Renderable during registration
 * Benefits: Single attachment point, batch operations, type-safe queries
 */
public interface RenderableLayoutManagerHandle<R, LCB,G, GCB> {
    boolean isInCurrentPass(R r);
    void markLayoutDirty(R r);
    void markLayoutDirtyImmediate(R r);
    CompletableFuture<Void> flushLayout();
    void migrateToFloating(R r, R anchor);
    void migrateToRegular(R r);
    void registerChild(R child, LCB layoutCallback);
    void unregister(R r);
    void beginBatch();
    void endBatch();
    boolean isLayoutExecuting();
    void runWhenLayoutIdle(Runnable mutation);
    void requestFocus(R r);
    void createLayoutGroup(String id);
    void addToLayoutGroup(R r, String id);
    void removeLayoutGroupMember(R r);
    void destroyLayoutGroup(String id);
    void setGroupLayoutCallback(String id, GCB cb);
    String getLayoutGroupIdByRenderable(R r);
    void deferInvalidateWhenLayoutIdle(Runnable r);
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
