package io.netnotes.engine.core.system;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.netnotes.engine.core.system.control.PasswordReader;
import io.netnotes.engine.core.system.control.terminal.RenderManager;
import io.netnotes.engine.core.system.control.terminal.RenderManager.RenderElement;
import io.netnotes.engine.core.system.control.terminal.RenderManager.RenderState;
import io.netnotes.engine.core.system.control.terminal.RenderManager.Renderable;
import io.netnotes.engine.core.system.control.terminal.TextStyle;
import io.netnotes.engine.core.system.control.terminal.TextStyle.BoxStyle;
import io.netnotes.engine.core.system.control.terminal.elements.TerminalTextBox;
import io.netnotes.engine.io.input.events.EventHandlerRegistry;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.engine.utils.LoggingHelpers.Log;
import io.netnotes.engine.utils.virtualExecutors.VirtualExecutors;

/**
 * PasswordPrompt - REFACTORED for pull-based rendering
 * 
 * Now implements Renderable and works with RenderManager.
 * Can be used as the active screen during password entry.
 * 
 * Usage:
 * <pre>
 * PasswordPrompt prompt = new PasswordPrompt(terminal)
 *     .withTitle("Authentication")
 *     .withPrompt("Enter password:")
 *     .onPassword(password -> {
 *         // Use password
 *         password.close();
 *     })
 *     .show();
 * </pre>
 */
public class PasswordPrompt implements Renderable {
    
    private final SystemTerminalContainer terminal;
    private final RenderManager renderManager;
    
    // Configuration
    private String title = "Authentication";
    private String prompt = "Enter password:";
    private String confirmPrompt = null;
    private int timeoutSeconds = 30;
    private int promptRow = -1;
    private int promptCol = -1;
    
    // Callbacks
    private java.util.function.Consumer<NoteBytesEphemeral> onPassword;
    private Runnable onTimeout;
    private Runnable onCancel;
    private Runnable onError;
    private Runnable onMismatch;
    
    // Runtime state (mutable)
    private enum State {
        INACTIVE,           // Not shown
        PROMPTING,          // Showing first password prompt
        CONFIRMING,         // Showing confirmation prompt
        PROCESSING          // Processing password (verifying, etc.)
    }
    
    private volatile State currentState = State.INACTIVE;
    private volatile String currentPromptText = null;
    private volatile String statusMessage = null;  // For error/info messages
    
    // Internal components
    private PasswordReader passwordReader;
    private CompletableFuture<Void> timeoutFuture;
    private NoteBytesEphemeral firstPassword = null;
    
    public PasswordPrompt(SystemTerminalContainer terminal) {
        this.terminal = terminal;
        this.renderManager = terminal.getRenderManager();
    }
    
    // ===== RENDERABLE INTERFACE =====
    
    @Override
    public RenderState getRenderState() {
        return switch (currentState) {
            case INACTIVE -> buildInactiveState();
            case PROMPTING, CONFIRMING -> buildPromptState();
            case PROCESSING -> buildProcessingState();
        };
    }
    
    /**
     * Build inactive state (shouldn't be rendered)
     */
    private RenderState buildInactiveState() {
        return RenderState.builder().build();
    }
    
    /**
     * Build password prompt state
     */
    private RenderState buildPromptState() {
        // Calculate box position - if position specified, use as top-left; otherwise center
        int boxRow = promptRow >= 0 ? promptRow : terminal.getRows() / 2 - 5;
        int boxCol = promptCol >= 0 ? promptCol : terminal.getCols() / 2 - 25;
        
        // Build text box for prompt
        TerminalTextBox promptBox = TerminalTextBox.builder()
            .position(boxRow, boxCol)
            .size(50, 10)
            .title(title, TerminalTextBox.TitlePlacement.BORDER_TOP)
            .style(BoxStyle.DOUBLE)
            .titleStyle(TextStyle.BOLD)
            .contentAlignment(TerminalTextBox.ContentAlignment.CENTER)
            .addLine("")
            .addLine(currentPromptText != null ? currentPromptText : prompt)
            .addLine("")
            .build();
        
        RenderState.Builder builder = RenderState.builder();
        builder.add(promptBox.asRenderElement());
        
        // Add status message if present
        if (statusMessage != null && !statusMessage.isEmpty()) {
            int statusRow = boxRow + 11;  // Below the box
            int statusCol = boxCol + 25 - statusMessage.length() / 2;  // Center relative to box
            
            builder.add((term, gen) -> {
                term.printAt(statusRow, statusCol, statusMessage, 
                    TextStyle.WARNING, gen);
            });
        }
        
        // Add footer
        builder.add(buildFooter("ESC: Cancel"));
        
        // PasswordReader renders at its configured position
        // We show the prompt prefix here
        int inputRow = boxRow + 5;  // Center of box vertically
        int inputCol = boxCol + 25 - 10;  // Center of box horizontally, offset for "> "
        
        builder.add((term, gen) -> {
            term.printAt(inputRow, inputCol, "> ", TextStyle.NORMAL, gen);
            // PasswordReader renders masked input after this
        });
        
        return builder.build();
    }
    
    /**
     * Build processing state
     */
    private RenderState buildProcessingState() {
        int row = terminal.getRows() / 2;
        int col = terminal.getCols() / 2 - 15;
        
        return RenderState.builder()
            .add((term, gen) -> {
                term.printAt(row, col, "Processing...", TextStyle.INFO, gen);
            })
            .build();
    }
    
    /**
     * Build footer element
     */
    private RenderElement buildFooter(String text) {
        int row = terminal.getRows() - 2;
        int col = terminal.getCols() / 2 - text.length() / 2;
        
        return (term, gen) -> {
            term.drawHLine(row - 1, 0, terminal.getCols(), gen);
            term.printAt(row, col, text, TextStyle.INFO, gen);
        };
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
    
    public PasswordPrompt onMismatch(Runnable handler) {
        this.onMismatch = handler;
        return this;
    }
    
    // ===== LIFECYCLE =====
    
    /**
     * Show password prompt
     * Makes this the active renderable
     */
    public CompletableFuture<Void> show() {
        if (currentState != State.INACTIVE) {
            Log.logError("[PasswordPrompt] Already active");
            return CompletableFuture.completedFuture(null);
        }
        
        // Update state
        currentState = State.PROMPTING;
        currentPromptText = prompt;
        statusMessage = null;
        
        // Make this the active renderable
        renderManager.setActive(this);
        
        return terminal.claimPasswordKeyboard()
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
        if (currentState == State.INACTIVE) return;
        
        Log.logMsg("[PasswordPrompt] Cancelled");
        cleanup();
        
        if (onCancel != null) {
            onCancel.run();
        }
    }
    
    // ===== PASSWORD ENTRY =====
    
    private void startPasswordEntry() {
        EventHandlerRegistry registry = terminal.getPasswordEventHandlerRegistry();
        
        if (passwordReader == null) {
            passwordReader = new PasswordReader(registry);
        } else {
            passwordReader.escape();
        }
        
        passwordReader.setOnPassword(password -> {
            cancelTimeout();
            
            if (confirmPrompt != null && currentState == State.PROMPTING) {
                handleFirstPassword(password);
            } else if (currentState == State.CONFIRMING) {
                handleConfirmation(password);
            } else {
                // No confirmation needed
                cleanup();
                if (onPassword != null) {
                    onPassword.accept(password);
                } else {
                    password.close();
                }
            }
        });
        
        renderManager.invalidate();
    }
    
    private void handleFirstPassword(NoteBytesEphemeral password) {
        Log.logMsg("[PasswordPrompt] First password entered, requesting confirmation");
        
        // Store first password
        firstPassword = password.copy();
        password.close();
        
        // Switch to confirmation mode
        currentState = State.CONFIRMING;
        currentPromptText = confirmPrompt;
        statusMessage = null;
        renderManager.invalidate();
        
        // Restart input
        startPasswordEntry();
        startTimeout();
    }
    
    private void handleConfirmation(NoteBytesEphemeral password) {
        boolean match = firstPassword.equals(password);
        
        if (match) {
            Log.logMsg("[PasswordPrompt] Passwords match");
            password.close();
            
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
            
            if (onMismatch != null) {
                cleanup();
                onMismatch.run();
            } else {
                // Default: show error and restart
                currentState = State.PROMPTING;
                currentPromptText = prompt;
                statusMessage = "Passwords do not match - try again";
                renderManager.invalidate();
                
                // Brief delay, then restart
                CompletableFuture.runAsync(() -> {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).thenRun(() -> {
                    statusMessage = null;
                    renderManager.invalidate();
                    startPasswordEntry();
                    startTimeout();
                });
            }
        }
    }
    
    // ===== TIMEOUT =====
    
    private void startTimeout() {
        cancelTimeout();
        
        if (timeoutSeconds <= 0) {
            return;
        }
        
        timeoutFuture = CompletableFuture.runAsync(() -> {
            try {
                TimeUnit.SECONDS.sleep(timeoutSeconds);
                
                if (currentState != State.INACTIVE) {
                    Log.logMsg("[PasswordPrompt] Timeout after " + timeoutSeconds + "s");
                    handleTimeout();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
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
        if (currentState == State.INACTIVE) return;
        
        currentState = State.INACTIVE;
        
        cancelTimeout();
        
        if (passwordReader != null) {
            passwordReader.close();
            passwordReader = null;
        }
        
        if (firstPassword != null) {
            firstPassword.close();
            firstPassword = null;
        }
        
        terminal.releasePasswordKeyboard()
            .exceptionally(ex -> {
                Log.logError("[PasswordPrompt] Cleanup error: " + ex.getMessage());
                return null;
            });
    }
    
    // ===== GETTERS =====
    
    public boolean isActive() {
        return currentState != State.INACTIVE;
    }
    
    public boolean isConfirming() {
        return currentState == State.CONFIRMING;
    }
}