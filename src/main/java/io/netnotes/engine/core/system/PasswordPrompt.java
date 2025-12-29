package io.netnotes.engine.core.system;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.netnotes.engine.core.system.control.PasswordReader;
import io.netnotes.engine.io.input.events.EventHandlerRegistry;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.engine.utils.LoggingHelpers.Log;
import io.netnotes.engine.utils.virtualExecutors.VirtualExecutors;

/**
 * PasswordPrompt - Reusable secure password entry
 * 
 * Responsibilities:
 * - Claim password keyboard if configured
 * - Display prompt
 * - Manage PasswordReader lifecycle
 * - Handle timeout
 * - Release password keyboard when done
 * - Clean error handling
 * 
 * Usage:
 * <pre>
 * new PasswordPrompt(terminal)
 *     .withTitle("Authentication Required")
 *     .withPrompt("Enter password:")
 *     .withTimeout(30)
 *     .onPassword(password -> {
 *         // Use password
 *         password.close();
 *     })
 *     .onTimeout(() -> handleTimeout())
 *     .onCancel(() -> handleCancel())
 *     .show();
 * </pre>
 */
public class PasswordPrompt {
    
    private final SystemTerminalContainer terminal;
    
    // Configuration
    private String title = "Authentication";
    private String prompt = "Enter password:";
    private int timeoutSeconds = 30;
    private int promptRow = 5;
    private int promptCol = 10;
    
    // Callbacks
    private java.util.function.Consumer<NoteBytesEphemeral> onPassword;
    private Runnable onTimeout;
    private Runnable onCancel;
    private Runnable onError;
    
    // Runtime state
    private PasswordReader passwordReader;
    private CompletableFuture<Void> timeoutFuture;
    private boolean isActive = false;
    
    public PasswordPrompt(SystemTerminalContainer terminal) {
        this.terminal = terminal;
    }
    
    // ===== CONFIGURATION (Builder pattern) =====
    
    public PasswordPrompt withTitle(String title) {
        this.title = title;
        return this;
    }
    
    public PasswordPrompt withPrompt(String prompt) {
        this.prompt = prompt;
        return this;
    }
    
    public PasswordPrompt withTimeout(int seconds) {
        this.timeoutSeconds = seconds;
        return this;
    }
    
    public PasswordPrompt withPosition(int row, int col) {
        this.promptRow = row;
        this.promptCol = col;
        return this;
    }
    
    public PasswordPrompt onPassword(java.util.function.Consumer<NoteBytesEphemeral> handler) {
        this.onPassword = handler;
        return this;
    }
    
    public PasswordPrompt onTimeout(Runnable handler) {
        this.onTimeout = handler;
        return this;
    }
    
    public PasswordPrompt onCancel(Runnable handler) {
        this.onCancel = handler;
        return this;
    }
    
    public PasswordPrompt onError(Runnable handler) {
        this.onError = handler;
        return this;
    }
    
    // ===== LIFECYCLE =====
    
    /**
     * Show password prompt
     * 
     * Flow:
     * 1. Claim password keyboard if needed
     * 2. Render prompt
     * 3. Start password reader
     * 4. Start timeout
     */
    public CompletableFuture<Void> show() {
        if (isActive) {
            Log.logError("[PasswordPrompt] Already active");
            return CompletableFuture.completedFuture(null);
        }
        
        isActive = true;
        
        return terminal.claimPasswordKeyboard()
            .thenCompose(v -> renderPrompt())
            .thenRun(() -> {
                startPasswordEntry();
                startTimeout();
            })
            .exceptionally(ex -> {
                Log.logError("[PasswordPrompt] Show failed: " + ex.getMessage());
                cleanup();
                if (onError != null) {
                    onError.run();
                }
                return null;
            });
    }
    
    /**
     * Cancel prompt (user initiated)
     */
    public void cancel() {
        if (!isActive) return;
        
        Log.logMsg("[PasswordPrompt] Cancelled");
        cleanup();
        
        if (onCancel != null) {
            onCancel.run();
        }
    }
    
    // ===== RENDERING =====
    
    private CompletableFuture<Void> renderPrompt() {
        return terminal.clear()
            .thenCompose(v -> terminal.printTitle(title))
            .thenCompose(v -> terminal.printAt(promptRow, promptCol, prompt))
            .thenCompose(v -> terminal.moveCursor(promptRow, promptCol + prompt.length() + 1));
    }
    
    // ===== PASSWORD ENTRY =====
    
    private void startPasswordEntry() {
        EventHandlerRegistry registry = terminal.getPasswordEventHandlerRegistry();
        passwordReader = new PasswordReader(registry);
        
        passwordReader.setOnPassword(password -> {
            Log.logMsg("[PasswordPrompt] Password received");
            cleanup();
            
            if (onPassword != null) {
                onPassword.accept(password);
            } else {
                // No handler - close password immediately
                password.close();
                Log.logError("[PasswordPrompt] No password handler set");
            }
        });
    }
    
    // ===== TIMEOUT =====
    
    private void startTimeout() {
        if (timeoutSeconds <= 0) {
            return; // No timeout
        }
        
        timeoutFuture = CompletableFuture.runAsync(() -> {
            try {
                TimeUnit.SECONDS.sleep(timeoutSeconds);
                
                if (isActive) {
                    Log.logMsg("[PasswordPrompt] Timeout after " + timeoutSeconds + "s");
                    handleTimeout();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                // Cancelled - normal
            }
        }, VirtualExecutors.getVirtualExecutor());
    }
    
    private void handleTimeout() {
        cleanup();
        
        if (onTimeout != null) {
            onTimeout.run();
        }
    }
    
    // ===== CLEANUP =====
    
    private void cleanup() {
        if (!isActive) return;
        
        isActive = false;
        
        // Cancel timeout
        if (timeoutFuture != null) {
            timeoutFuture.cancel(true);
            timeoutFuture = null;
        }
        
        // Close password reader
        if (passwordReader != null) {
            passwordReader.close();
            passwordReader = null;
        }
        
        // Release password keyboard if needed
        terminal.releasePasswordKeyboard()
            .exceptionally(ex -> {
                Log.logError("[PasswordPrompt] Cleanup error: " + ex.getMessage());
                return null;
            });
    }
    
    // ===== GETTERS =====
    
    public boolean isActive() {
        return isActive;
    }
}