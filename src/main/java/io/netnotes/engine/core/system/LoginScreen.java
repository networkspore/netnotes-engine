package io.netnotes.engine.core.system;

import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.core.system.control.PasswordReader;
import io.netnotes.engine.io.input.InputDevice;
import io.netnotes.engine.utils.TimeHelpers;


/**
 * LoginScreen - Authenticate to existing system
 * Uses PasswordReader for secure password verification
 */
class LoginScreen extends TerminalScreen {
    
    private PasswordReader passwordReader;

    public LoginScreen(String name, SystemTerminalContainer terminal, InputDevice keyboard) {
        super(name, terminal, keyboard);
    }
    
    @Override
    public CompletableFuture<Void> onShow() {
        passwordReader = new PasswordReader();
        return render();
           
    }
    
    @Override
    public void onHide() {
        cleanup();
    }
    
    @Override
    public CompletableFuture<Void> render() {
        return terminal.clear()
            .thenCompose(v -> terminal.printTitle("Netnotes Login"))
            .thenCompose(v -> terminal.printAt(5, 10, "Enter password:"))
            .thenCompose(v -> terminal.moveCursor(5, 26))
            .thenRun(this::startPasswordEntry);
    }
    
    private void startPasswordEntry() {
        // Create password reader
        
        
        
        // Register with keyboard
        keyboard.setEventConsumer( passwordReader.getEventConsumer());
        
        // Handle password when ready
        passwordReader.setOnPassword(password -> {
            // Unregister and close reader
            keyboard.setEventConsumer(null);
            passwordReader.escape();
    
            
            // Verify and load system
            terminal.verifyPasswordInternal(password)
                .thenAccept(valid -> {
                    password.close();
                    
                    if (!valid) {
                        terminal.clear()
                            .thenCompose(v->terminal.printError("Invalid password. Please try again."))
                            .thenRunAsync(()->TimeHelpers.timeDelay(2))
                            .thenCompose(v -> render());
                    }
                })
                .exceptionally(ex -> {
                    password.close();
                    terminal.clear()
                        .thenCompose(v->terminal.printError("Login failed: " + ex.getMessage()))
                        .thenCompose(v -> terminal.printAt(15, 10, "Press any key..."))
                        .thenRun(() -> waitForKeyPress(keyboard, () -> render()));
                    return null;
                });
        });
    }
    
    private void cleanup() {
        if (passwordReader != null) {
            keyboard.setEventConsumer(null);
            passwordReader.close();
            passwordReader = null;
        }
    }
}
