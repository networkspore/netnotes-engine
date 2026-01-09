package io.netnotes.engine.core.system;

import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.core.system.control.terminal.TerminalRenderState;
import io.netnotes.engine.core.system.control.terminal.TextStyle;
import io.netnotes.engine.utils.TimeHelpers;

/**
 * LoginScreen - REFACTORED for pull-based rendering
 * 
 * Uses PasswordPrompt which is also pull-based.
 * This screen acts as a coordinator, delegating rendering to PasswordPrompt.
 * 
 * RENDERABLE OWNERSHIP:
 * - When PasswordPrompt is active, it's the container's renderable
 * - When showing error/processing, this screen is the renderable
 * - Only ONE is active at a time
 */
class LoginScreen extends TerminalScreen {
    
    private enum State {
        SHOWING_PROMPT,     // PasswordPrompt is active renderable
        PROCESSING,         // This screen is active, showing processing
        ERROR               // This screen is active, showing error
    }
    
    private volatile State currentState = State.SHOWING_PROMPT;
    private volatile String errorMessage = null;
    
    private PasswordPrompt passwordPrompt;

    public LoginScreen(String name, SystemApplication systemApplication) {
        super(name, systemApplication);
    }
    
    // ===== RENDERABLE INTERFACE =====
    
    @Override
    public TerminalRenderState getRenderState() {
        return switch (currentState) {
            case SHOWING_PROMPT -> buildPromptState();
            case PROCESSING -> buildProcessingState();
            case ERROR -> buildErrorState();
        };
    }
    
    /**
     * PasswordPrompt is active renderable, we return empty
     */
    private TerminalRenderState buildPromptState() {
        // PasswordPrompt is the active renderable
        // We shouldn't be rendering
        return TerminalRenderState.builder().build();
    }
    
    /**
     * Show processing indicator
     */
    private TerminalRenderState buildProcessingState() {
        int row = systemApplication.getHeight() / 2;
        int col = systemApplication.getWidth() / 2 - 15;
        
        return TerminalRenderState.builder()
            .add((term) -> {
                term.printAt(row, col, "Verifying password...", 
                    TextStyle.INFO);
            })
            .build();
    }
    
    /**
     * Show error message
     */
    private TerminalRenderState buildErrorState() {
        int errorRow = systemApplication.getHeight() / 2;
        int promptRow = errorRow + 2;
        
        String message = errorMessage != null ? errorMessage : "Authentication failed";
        
        return TerminalRenderState.builder()
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
        String title = systemApplication.isAuthenticated() ? "System Locked" : "Netnotes";
        
        currentState = State.SHOWING_PROMPT;
        // Don't call invalidate() - PasswordPrompt will become active
        
        passwordPrompt = new PasswordPrompt(systemApplication)
            .withTitle(title)
            .withPrompt("Enter password:")
            .withTimeout(30)
            .onPassword(this::handlePassword)
            .onTimeout(this::handleTimeout)
            .onCancel(this::handleCancel);
        
        // PasswordPrompt.show() will call terminal.setRenderable(passwordPrompt)
        return passwordPrompt.show();
    }
    
    @Override
    public void onHide() {
        cleanup();
        super.onHide();
    }
    
    // ===== EVENT HANDLING =====
    
    private void handlePassword(io.netnotes.engine.noteBytes.NoteBytesEphemeral password) {
        currentState = State.PROCESSING;
        
        // Make THIS screen the active renderable to show processing
        systemApplication.setRenderable(this);
        invalidate();
        
        systemApplication.authenticate(password)
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
                systemApplication.waitForKeyPress()
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
        
        // Make THIS screen the active renderable to show error
        systemApplication.getTerminal().setRenderable(this);
        invalidate();
        
        systemApplication.waitForKeyPress()
            .thenRun(() -> {
                if (systemApplication.isAuthenticated()) {
                    // Was unlocking → back to locked screen
                    systemApplication.showScreen("locked");
                } else {
                    // Was logging in → show locked screen
                    systemApplication.showScreen("locked");
                }
            });
    }
    
    private void handleCancel() {
        systemApplication.goBack();
    }
    
    // ===== CLEANUP =====
    
    private void cleanup() {
        if (passwordPrompt != null && passwordPrompt.isActive()) {
            passwordPrompt.cancel();
            passwordPrompt = null;
        }
    }
}