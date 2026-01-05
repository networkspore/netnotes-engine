package io.netnotes.engine.core.system.control.terminal.elements;

/**
 * Interface for renderables that can be invalidated
 * Used to avoid circular dependencies with TerminalScreen
 */
public interface Invalidatable {
    void invalidate();
}