package io.netnotes.engine.io.input.events;

import io.netnotes.engine.io.ContextPath;

public final class FocusLostEvent implements RoutedEvent {
    private final ContextPath sourcePath;

    public FocusLostEvent(ContextPath sourcePath) {
        this.sourcePath = sourcePath;
    }

    @Override
    public ContextPath getSourcePath() { return sourcePath; }
}

