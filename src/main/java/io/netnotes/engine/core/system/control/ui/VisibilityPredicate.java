package io.netnotes.engine.core.system.control.ui;

@FunctionalInterface
public interface VisibilityPredicate<R> {
    boolean allowVisibilityChange(R renderable, boolean requestVisible);
}
