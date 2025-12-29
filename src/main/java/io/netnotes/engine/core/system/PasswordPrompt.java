package io.netnotes.engine.core.system;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.netnotes.engine.core.system.control.PasswordReader;
import io.netnotes.engine.io.input.events.EventHandlerRegistry;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.engine.utils.LoggingHelpers.Log;
import io.netnotes.engine.utils.virtualExecutors.VirtualExecutors;

/**
 * PasswordPrompt - Reusable secure password entry with optional confirmation
 * 
 * Features:
 * - Automatic password keyboard claiming/releasing
 * - Optional confirmation step (for password creation/change)
 * - Timeout handling
 * - Keeps keyboard claimed during confirmation (no release between steps)
 * - Clean error handling and lifecycle management
 * 
 * Usage (Simple):
 * <pre>
 * new PasswordPrompt(terminal)
 *     .withTitle("Authentication")
 *     .withPrompt("Enter password:")
 *     .onPassword(password -> {
 *         // Use password
 *         password.close();
 *     })
 *     .show();
 * </pre>
 * 
 * Usage (With Confirmation):
 * <pre>
 * new PasswordPrompt(terminal)
 *     .withTitle("Create Password")
 *     .withPrompt("Enter password:")
 *     .withConfirmation("Confirm password:")
 *     .onPassword(password -> {
 *         // Password confirmed and matches
 *         password.close();
 *     })
 *     .onMismatch(() -> {
 *         // Passwords didn't match, user can retry
 *     })
 *     .show();
 * </pre>
 */
public class PasswordPrompt {
    
    private final SystemTerminalContainer terminal;
    
    // Configuration
    private String title = "Authentication";
    private String prompt = "Enter password:";
    private String confirmPrompt = null;  // null = no confirmation
    private int timeoutSeconds = 30;
    private int promptRow = 5;
    private int promptCol = 10;
    
    // Callbacks
    private java.util.function.Consumer<NoteBytesEphemeral> onPassword;
    private Runnable onTimeout;
    private Runnable onCancel;
    private Runnable onError;
    private Runnable onMismatch;  // Called when confirmation doesn't match
    
    // Runtime state
    private PasswordReader passwordReader;
    private CompletableFuture<Void> timeoutFuture;
    private boolean isActive = false;
    private boolean isConfirming = false;
    private NoteBytesEphemeral firstPassword = null;  // Stored during confirmation
    
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
    
    /**
     * Enable confirmation mode
     * When set, user must enter password twice
     * Password is only passed to onPassword if both match
     */
    public PasswordPrompt withConfirmation(String confirmPrompt) {
        this.confirmPrompt = confirmPrompt;
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
    
    /**
     * Called when confirmation passwords don't match
     * If not set, will automatically restart the prompt
     */
    public PasswordPrompt onMismatch(Runnable handler) {
        this.onMismatch = handler;
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
     * 5. If confirmation enabled and first password entered:
     *    - Keep keyboard claimed
     *    - Show confirmation prompt
     *    - Compare passwords
     *    - Call onPassword only if match
     */
    public CompletableFuture<Void> show() {
        if (isActive) {
            Log.logError("[PasswordPrompt] Already active");
            return CompletableFuture.completedFuture(null);
        }
        
        isActive = true;
        isConfirming = false;
        
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
        String currentPrompt = isConfirming ? confirmPrompt : prompt;
        
        return terminal.clear()
            .thenCompose(v -> terminal.printTitle(title))
            .thenCompose(v -> terminal.printAt(promptRow, promptCol, currentPrompt))
            .thenCompose(v -> terminal.moveCursor(promptRow, promptCol + currentPrompt.length() + 1));
    }
    
    // ===== PASSWORD ENTRY =====
    
    private void startPasswordEntry() {
        EventHandlerRegistry registry = terminal.getPasswordEventHandlerRegistry();
        
        // Reuse existing reader if in confirmation mode
        if (passwordReader == null) {
            passwordReader = new PasswordReader(registry);
        } else {
            // Reset reader for confirmation
            passwordReader.escape();
        }
        
        passwordReader.setOnPassword(password -> {
            cancelTimeout();  // Got password, cancel timeout
            
            if (confirmPrompt != null && !isConfirming) {
                // First password entry - need confirmation
                handleFirstPassword(password);
            } else if (isConfirming) {
                // Confirmation entry - compare
                handleConfirmation(password);
            } else {
                // No confirmation needed - done
                cleanup();
                if (onPassword != null) {
                    onPassword.accept(password);
                } else {
                    password.close();
                }
            }
        });
    }
    
    private void handleFirstPassword(NoteBytesEphemeral password) {
        Log.logMsg("[PasswordPrompt] First password entered, requesting confirmation");
        
        // Store first password
        firstPassword = password.copy();
        password.close();
        
        // Switch to confirmation mode
        isConfirming = true;
        
        // Show confirmation prompt (keyboard stays claimed)
        renderPrompt()
            .thenRun(() -> {
                startPasswordEntry();
                startTimeout();  // Restart timeout for confirmation
            })
            .exceptionally(ex -> {
                Log.logError("[PasswordPrompt] Confirmation prompt failed: " + ex.getMessage());
                cleanup();
                if (onError != null) {
                    onError.run();
                }
                return null;
            });
    }
    
    private void handleConfirmation(NoteBytesEphemeral password) {
        boolean match = firstPassword.equals(password);
        
        if (match) {
            Log.logMsg("[PasswordPrompt] Passwords match");
            password.close();
            
            // Success - pass first password to callback
            NoteBytesEphemeral confirmedPassword = firstPassword;
            firstPassword = null;
            
            cleanup();
            
            if (onPassword != null) {
                onPassword.accept(confirmedPassword);
            } else {
                confirmedPassword.close();
            }
        } else {
            Log.logMsg("[PasswordPrompt] Passwords don't match");
            
            // Clean up both passwords
            firstPassword.close();
            firstPassword = null;
            password.close();
            
            cleanup();
            
            if (onMismatch != null) {
                onMismatch.run();
            } else {
                // Default: show error and restart
                terminal.clear()
                    .thenCompose(v -> terminal.printError("Passwords do not match"))
                    .thenRunAsync(() -> {
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    })
                    .thenRun(() -> show());
            }
        }
    }
    
    // ===== TIMEOUT =====
    
    private void startTimeout() {
        cancelTimeout();  // Cancel any existing
        
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
    
    private void cancelTimeout() {
        if (timeoutFuture != null) {
            timeoutFuture.cancel(true);
            timeoutFuture = null;
        }
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
        isConfirming = false;
        
        // Cancel timeout
        cancelTimeout();
        
        // Close password reader
        if (passwordReader != null) {
            passwordReader.close();
            passwordReader = null;
        }
        
        // Clean up stored password
        if (firstPassword != null) {
            firstPassword.close();
            firstPassword = null;
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
    
    public boolean isConfirming() {
        return isConfirming;
    }
}