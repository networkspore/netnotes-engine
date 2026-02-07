package io.netnotes.engine.ui;

@FunctionalInterface
public interface VisibilityPredicate<R> {
    boolean allowVisibilityChange(R renderable, boolean requestVisible);
}
