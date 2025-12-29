package io.netnotes.engine.core.system;

import java.util.concurrent.CompletableFuture;
import io.netnotes.engine.utils.TimeHelpers;

/**
 * LoginScreen - System authentication using PasswordPrompt
 */
class LoginScreen extends TerminalScreen {
    
    private PasswordPrompt passwordPrompt;

    public LoginScreen(String name, SystemTerminalContainer terminal) {
        super(name, terminal);
    }
    
    @Override
    public CompletableFuture<Void> onShow() {
        String title = terminal.isAuthenticated() ? "System Locked" : "Netnotes";
        
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
    
    @Override
    public CompletableFuture<Void> render() {
        // PasswordPrompt handles rendering
        return CompletableFuture.completedFuture(null);
    }
    
    private void handlePassword(io.netnotes.engine.noteBytes.NoteBytesEphemeral password) {
        terminal.authenticate(password)
            .thenAccept(valid -> {
                password.close();
                
                if (!valid) {
                    terminal.clear()
                        .thenCompose(v -> terminal.printError("Invalid password"))
                        .thenRunAsync(() -> TimeHelpers.timeDelay(2))
                        .thenCompose(v -> onShow());
                }
                // On success, terminal.authenticate() handles navigation
            })
            .exceptionally(ex -> {
                password.close();
                terminal.clear()
                    .thenCompose(v -> terminal.printError("Login failed: " + ex.getMessage()))
                    .thenCompose(v -> terminal.printAt(15, 10, "Press any key..."))
                    .thenRun(() -> terminal.waitForKeyPress(() -> onShow()));
                return null;
            });
    }
    
    private void handleTimeout() {
        terminal.clear()
            .thenCompose(v -> terminal.printError("Authentication timeout"))
            .thenCompose(v -> terminal.printAt(15, 10, "Press any key..."))
            .thenRun(() -> terminal.waitForKeyPress(() -> {
                if (terminal.isAuthenticated()) {
                    // Was unlocking → back to locked screen
                    terminal.showScreen("locked");
                } else {
                    // Was logging in → show locked screen
                    terminal.showScreen("locked");
                }
            }));
    }
    
    private void handleCancel() {
        terminal.goBack();
    }
    
    private void cleanup() {
        if (passwordPrompt != null && passwordPrompt.isActive()) {
            passwordPrompt.cancel();
            passwordPrompt = null;
        }
    }
}