package io.netnotes.engine.ui.containers;

import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.ui.SpatialRegion;

public interface ContainerLayoutManager<
    S extends SpatialRegion<?,S>,
    T extends Container<?,S,?,T>
> {
    CompletableFuture<Void> onContainerAdded(T container);
    CompletableFuture<Void> onContainerRemoved(T container);
    CompletableFuture<Void> onViewportResized(S viewportBounds);
}