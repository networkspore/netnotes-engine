package io.netnotes.engine.core.system;

import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.core.system.control.PasswordReader;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.engine.utils.TimeHelpers;


/**
 * FirstRunPasswordScreen - Create password for new system
 * Uses PasswordReader directly for secure input
 */
class FirstRunPasswordScreen extends TerminalScreen {
    
    private PasswordReader passwordReader = null;
    private NoteBytesEphemeral firstPassword = null;
    
    public FirstRunPasswordScreen(String name, SystemTerminalContainer terminal) {
        super(name, terminal);
    }
    
    @Override
    public CompletableFuture<Void> onShow() {
        passwordReader = new PasswordReader(terminal.getPasswordEventHandlerRegistry());
        return render();
    }
    
    @Override
    public void onHide() {
        cleanup();
    }
    
    @Override
    public CompletableFuture<Void> render() {
        return onStart();
    }

    private CompletableFuture<Void> onStart(){
        return terminal.clear()
            .thenCompose(v -> terminal.printTitle("Welcome to Netnotes"))
            .thenCompose(v -> terminal.printAt(5, 10, "Create a master password"))
            .thenCompose(v -> terminal.printAt(7, 10, "Enter password:"))
            .thenCompose(v -> terminal.moveCursor(7, 26))
            .thenRun(this::startPasswordEntry);
    }
    
    private void startPasswordEntry() {

    
        
        // Handle password when ready
        passwordReader.setOnPassword(password -> {
            // Store first password for confirmation
            firstPassword = password.copy();
            password.close();
            
            passwordReader.escape();
            
            onConfirm();
        });
    }

    private void onConfirm(){
        terminal.clear()
            .thenCompose(v -> terminal.printTitle("Welcome to Netnotes"))
            .thenCompose(v -> terminal.printAt(5, 10, "Create a master password"))   
            .thenCompose(v->terminal.printAt(9, 10, "Confirm password:"))
                .thenCompose(v -> terminal.moveCursor(9, 28))
                .thenRun(this::startConfirmationEntry);
    }
    
    private void startConfirmationEntry() {
        // Create confirmation reader
        // Handle confirmation
        passwordReader.setOnPassword(password -> {
            passwordReader.escape();
            // Compare passwords
            boolean match = firstPassword.equals(password);
            firstPassword.close();
            if (match) {
                // Passwords match - create system
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
            } else {
                password.close();
                terminal.clear()
                    .thenCompose(v->terminal.printError("Passwords do not match. Please try again."))
                    .thenRunAsync(()->TimeHelpers.timeDelay(2))
                    .thenCompose(v ->onStart());
            }
        });
    }

 
    private void cleanup() {
        if (passwordReader != null) {
            passwordReader.close();
            passwordReader = null;
        }
        if (firstPassword != null) {
            firstPassword.close();
            firstPassword = null;
        }
    }
}

