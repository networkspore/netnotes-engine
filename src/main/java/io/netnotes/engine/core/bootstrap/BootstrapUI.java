package io.netnotes.engine.core.bootstrap;

import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import java.util.concurrent.CompletableFuture;

/**
 * BootstrapUI - Interface for user interaction during bootstrap
 * 
 * Implementations can be:
 * - Swing/JavaFX GUI
 * - Terminal/Console
 * - Web interface
 * - Headless (automated responses)
 * 
 * All methods return CompletableFutures to support async interaction
 */
public interface BootstrapUI {
    
    // ===== WELCOME / PROGRESS =====
    
    /**
     * Show welcome message (first-time setup)
     */
    CompletableFuture<Void> showWelcome();
    
    /**
     * Update boot progress
     * @param stage Description of current stage
     * @param percent Progress percentage (0-100)
     */
    void showBootProgress(String stage, int percent);
    
    /**
     * Show informational message
     */
    void showMessage(String message);
    
    /**
     * Show error message
     */
    void showError(String message);
    
    // ===== FIRST-TIME CONFIGURATION =====
    
    /**
     * Prompt: Install secure input (IODaemon)?
     * @return true if user wants to install secure input
     */
    CompletableFuture<Boolean> promptInstallSecureInput();
    
    /**
     * Prompt: Use secure input for shell commands/passwords?
     * @return true if user wants to use secure input for shell
     */
    CompletableFuture<Boolean> promptUseSecureInputForShell();
    
    // ===== PASSWORD INPUT =====
    
    /**
     * Prompt for password
     * @param context Why we're asking (first setup, unlock, confirm)
     * @param source Which input source to use (ignored if source not available)
     */
    CompletableFuture<NoteBytesEphemeral> promptPassword(
        PasswordContext context, 
        PasswordInputSource source
    );
    
    /**
     * Prompt for command line input (shell)
     */
    CompletableFuture<String> promptCommand();
}