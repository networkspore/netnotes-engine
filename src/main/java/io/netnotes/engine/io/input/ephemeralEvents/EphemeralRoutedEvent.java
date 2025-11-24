package io.netnotes.engine.io.input.ephemeralEvents;


import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.input.events.RoutedEvent;

/**
 * EphemeralRoutedEvent - Base class for ephemeral events
 * These events contain sensitive data that must be wiped after use
 * SECURITY CRITICAL: Always use try-with-resources
 */
public abstract class EphemeralRoutedEvent implements RoutedEvent, AutoCloseable {
    private final ContextPath sourcePath;

    protected EphemeralRoutedEvent(ContextPath sourcePath) {
        this.sourcePath = sourcePath;
    }
    
    public ContextPath getSourcePath(){
        return sourcePath;
    }
    /**
     * Close and wipe all sensitive data
     */
    @Override
    public abstract void close();
}

