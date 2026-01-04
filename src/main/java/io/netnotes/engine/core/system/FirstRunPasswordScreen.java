package io.netnotes.engine.core.system;

import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.core.system.control.terminal.ClientRenderManager.RenderState;

/**
 * FirstRunPasswordScreen - Create password for new system
 * Uses PasswordPrompt with confirmation
 */
class FirstRunPasswordScreen extends TerminalScreen {
    
    private PasswordPrompt passwordPrompt;
    
    public FirstRunPasswordScreen(String name, SystemTerminalContainer terminal) {
        super(name, terminal);
    }
    
    // ===== RENDERABLE INTERFACE =====
    
    /**
     * PasswordPrompt is the active renderable
     * We return empty state
     */
    @Override
    public RenderState getRenderState() {
        return RenderState.builder().build();
    }
    
    // ===== LIFECYCLE =====
    
    @Override
    public CompletableFuture<Void> onShow() {
        passwordPrompt = new PasswordPrompt(terminal)
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
        terminal.clear()
            .thenCompose(v -> terminal.printSuccess("Creating system..."))
            .thenCompose(v -> terminal.createNewSystem(password))
            .thenRun(() -> {
                password.close();
                terminal.printSuccess("System created successfully!");
            })
            .exceptionally(ex -> {
                password.close();
                terminal.printError("Failed to create system: " + ex.getMessage());
                return null;
            });
    }
    
    private void handleTimeout() {
        terminal.clear()
            .thenCompose(v -> terminal.printError("Setup timeout"))
            .thenCompose(v -> terminal.printAt(10, 10, "Press any key to retry..."))
            .thenRun(() -> terminal.waitForKeyPress(() -> onShow()));
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