package io.netnotes.engine.core.system;

import java.util.concurrent.CompletableFuture;
import io.netnotes.engine.core.system.control.terminal.ClientRenderManager.RenderState;
import io.netnotes.engine.core.system.control.terminal.TextStyle;
import io.netnotes.engine.utils.TimeHelpers;

/**
 * LoginScreen - REFACTORED for pull-based rendering
 * 
 * Uses PasswordPrompt which is also pull-based.
 * This screen acts as a coordinator, delegating rendering to PasswordPrompt.
 */
class LoginScreen extends TerminalScreen {
    
    private enum State {
        SHOWING_PROMPT,     // PasswordPrompt is active
        PROCESSING,         // Verifying password
        ERROR               // Showing error message
    }
    
    private volatile State currentState = State.SHOWING_PROMPT;
    private volatile String errorMessage = null;
    
    private PasswordPrompt passwordPrompt;

    public LoginScreen(String name, SystemTerminalContainer terminal) {
        super(name, terminal);
    }
    
    // ===== RENDERABLE INTERFACE =====
    
    @Override
    public RenderState getRenderState() {
        return switch (currentState) {
            case SHOWING_PROMPT -> buildPromptState();
            case PROCESSING -> buildProcessingState();
            case ERROR -> buildErrorState();
        };
    }
    
    /**
     * PasswordPrompt is active renderable, we return empty
     */
    private RenderState buildPromptState() {
        return RenderState.builder().build();
    }
    
    /**
     * Show processing indicator
     */
    private RenderState buildProcessingState() {
        int row = terminal.getRows() / 2;
        int col = terminal.getCols() / 2 - 15;
        
        return RenderState.builder()
            .add((term) -> {
                term.printAt(row, col, "Verifying password...", 
                    TextStyle.INFO);
            })
            .build();
    }
    
    /**
     * Show error message
     */
    private RenderState buildErrorState() {
        int errorRow = terminal.getRows() / 2;
        int promptRow = errorRow + 2;
        
        String message = errorMessage != null ? errorMessage : "Authentication failed";
        
        return RenderState.builder()
            .add((term) -> {
                term.printAt(errorRow, 10, message, TextStyle.ERROR);
                term.printAt(promptRow, 10, "Press any key to try again...", 
                    TextStyle.NORMAL);
            })
            .build();
    }
    
    // ===== LIFECYCLE =====
    
    @Override
    public CompletableFuture<Void> onShow() {
        String title = terminal.isAuthenticated() ? "System Locked" : "Netnotes";
        
        currentState = State.SHOWING_PROMPT;
        invalidate();
        
        passwordPrompt = new PasswordPrompt(terminal)
            .withTitle(title)
            .withPrompt("Enter password:")
            .withTimeout(30)
            .onPassword(this::handlePassword)
            .onTimeout(this::handleTimeout)
            .onCancel(this::handleCancel);
        
        return passwordPrompt.show();
    }
    
    @Override
    public void onHide() {
        cleanup();
    }
    
    // ===== EVENT HANDLING =====
    
    private void handlePassword(io.netnotes.engine.noteBytes.NoteBytesEphemeral password) {
        currentState = State.PROCESSING;
        
        // Make this screen active to show processing
        terminal.getRenderManager().setActive(this);
        
        terminal.authenticate(password)
            .thenAccept(valid -> {
                password.close();
                
                if (!valid) {
                    // Authentication failed
                    errorMessage = "Invalid password";
                    currentState = State.ERROR;
                    invalidate();
                    
                    // Wait a bit, then retry
                    CompletableFuture.runAsync(() -> {
                        TimeHelpers.timeDelay(2);
                    }).thenRun(() -> {
                        currentState = State.SHOWING_PROMPT;
                        onShow();
                    });
                }
                // On success, terminal.authenticate() handles navigation
            })
            .exceptionally(ex -> {
                password.close();
                
                errorMessage = "Login failed: " + ex.getMessage();
                currentState = State.ERROR;
                invalidate();
                
                // Wait for keypress, then retry
                terminal.waitForKeyPress()
                    .thenRun(() -> {
                        currentState = State.SHOWING_PROMPT;
                        onShow();
                    });
                
                return null;
            });
    }
    
    private void handleTimeout() {
        errorMessage = "Authentication timeout";
        currentState = State.ERROR;
        
        // Make this screen active to show error
        terminal.getRenderManager().setActive(this);
        
        terminal.waitForKeyPress()
            .thenRun(() -> {
                if (terminal.isAuthenticated()) {
                    // Was unlocking → back to locked screen
                    terminal.showScreen("locked");
                } else {
                    // Was logging in → show locked screen
                    terminal.showScreen("locked");
                }
            });
    }
    
    private void handleCancel() {
        terminal.goBack();
    }
    
    // ===== CLEANUP =====
    
    private void cleanup() {
        if (passwordPrompt != null && passwordPrompt.isActive()) {
            passwordPrompt.cancel();
            passwordPrompt = null;
        }
    }
}