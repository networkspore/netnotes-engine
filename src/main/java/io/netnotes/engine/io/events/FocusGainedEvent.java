package io.netnotes.engine.io.events;

import io.netnotes.engine.io.ContextPath;

public final class FocusGainedEvent implements RoutedEvent {
    private final ContextPath sourcePath;

    public FocusGainedEvent(ContextPath sourcePath) {
        this.sourcePath = sourcePath;
    }

    @Override
    public ContextPath getSourcePath() { return sourcePath; }
}
