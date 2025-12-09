package io.netnotes.engine.core.system;

import java.util.concurrent.CompletableFuture;
import io.netnotes.engine.core.system.control.PasswordReader;
import io.netnotes.engine.io.input.InputDevice;
import io.netnotes.engine.utils.TimeHelpers;


/**
 * LockedScreen - Re-authenticate after lock
 * Uses PasswordReader for unlock
 */
class LockedScreen extends TerminalScreen {
    
    private PasswordReader passwordReader;

    public LockedScreen(String name, SystemTerminalContainer terminal, InputDevice keyboard) {
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
            .thenCompose(v -> terminal.printTitle("System Locked"))
            .thenCompose(v -> terminal.printAt(5, 10, "Enter password to unlock:"))
            .thenCompose(v -> terminal.moveCursor(5, 35))
            .thenRun(this::startPasswordEntry);
    }
    
    private void startPasswordEntry() {

        // Register with keyboard
        keyboard.setEventConsumer(passwordReader.getEventConsumer());
        
        // Handle password when ready
        passwordReader.setOnPassword(password -> {
            // Unregister and close reader
            keyboard.setEventConsumer(null);
            passwordReader.escape();
 
            // Verify for unlock
            terminal.verifyPasswordForUnlock(password)
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
                        .thenCompose(v->terminal.printError("Unlock failed: " + ex.getMessage()))
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

