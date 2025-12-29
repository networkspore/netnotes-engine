package io.netnotes.engine.core.system;

import java.util.concurrent.CompletableFuture;

/**
 * FirstRunPasswordScreen - Create password for new system
 * Uses PasswordPrompt with confirmation
 */
class FirstRunPasswordScreen extends TerminalScreen {
    
    private PasswordPrompt passwordPrompt;
    
    public FirstRunPasswordScreen(String name, SystemTerminalContainer terminal) {
        super(name, terminal);
    }
    
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
    
    @Override
    public CompletableFuture<Void> render() {
        // PasswordPrompt handles rendering
        return CompletableFuture.completedFuture(null);
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