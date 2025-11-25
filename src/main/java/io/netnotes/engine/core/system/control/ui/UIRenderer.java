package io.netnotes.engine.core.system.control.ui;


import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import java.util.concurrent.CompletableFuture;

/**
 * UIRenderer - Interface for UI implementations
 * 
 * Receives command messages, renders UI, returns user responses
 * 
 * Implementations:
 * - NanoVG (GUI)
 * - ConsoleRenderer (terminal)
 * - WebRenderer (browser)
 */
public interface UIRenderer {
    
    /**
     * Render a UI command
     * @param command Command to render
     * @return CompletableFuture that completes with user response (if applicable)
     */
    CompletableFuture<NoteBytesMap> render(NoteBytesMap command);
    
    /**
     * Check if this renderer is active/available
     */
    boolean isActive();
    
    /**
     * Initialize the renderer
     */
    CompletableFuture<Void> initialize();
    
    /**
     * Shutdown the renderer
     */
    void shutdown();
}