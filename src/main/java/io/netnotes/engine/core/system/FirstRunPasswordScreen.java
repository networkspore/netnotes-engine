package io.netnotes.engine.core.system;

import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.core.system.control.terminal.TerminalRenderState;


/**
 * FirstRunPasswordScreen - Create password for new system
 * Uses PasswordPrompt with confirmation
 */
class FirstRunPasswordScreen extends TerminalScreen {
    
    private PasswordPrompt passwordPrompt;
    
    public FirstRunPasswordScreen(String name, SystemApplication systemApplication) {
        super(name, systemApplication);
    }
    
    // ===== RENDERABLE INTERFACE =====
    
    /**
     * PasswordPrompt is the active renderable
     * We return empty state
     */
    @Override
    public TerminalRenderState getRenderState() {
        return TerminalRenderState.builder().build();
    }
    
    // ===== LIFECYCLE =====
    
    @Override
    public CompletableFuture<Void> onShow() {
        passwordPrompt = new PasswordPrompt(systemApplication)
            .withTitle("Welcome to Netnotes")
            .withPrompt("Enter password:")
            .withConfirmation("Confirm password:")
            .withTimeout(60)  // Longer timeout for first run
            .onPassword(this::handlePassword)
            .onTimeout(this::handleTimeout)
            .onMismatch(this::handleMismatch);
        
        return passwordPrompt.show();
    }
    
    @Override
    public void onHide() {
        cleanup();
    }
    
    private void handlePassword(io.netnotes.engine.noteBytes.NoteBytesEphemeral password) {
        systemApplication.getTerminal().clear()
            .thenCompose(v -> systemApplication.getTerminal().printSuccess("Creating system..."))
            .thenCompose(v -> systemApplication.createNewSystem(password))
            .thenRun(() -> {
                password.close();
                systemApplication.getTerminal().printSuccess("System created successfully!");
            })
            .exceptionally(ex -> {
                password.close();
                systemApplication.getTerminal().printError("Failed to create system: " + ex.getMessage());
                return null;
            });
    }
    
    private void handleTimeout() {
        systemApplication.getTerminal().clear()
            .thenCompose(v -> systemApplication.getTerminal().printError("Setup timeout"))
            .thenCompose(v -> systemApplication.getTerminal().printAt(10, 10, "Press any key to retry..."))
            .thenRun(() -> systemApplication.getTerminal().waitForKeyPress(() -> onShow()));
    }
    
    private void handleMismatch() {
        // Default handler will show error and restart
        // We can customize if needed
    }
    
    private void cleanup() {
        if (passwordPrompt != null && passwordPrompt.isActive()) {
            passwordPrompt.cancel();
            passwordPrompt = null;
        }
    }
}