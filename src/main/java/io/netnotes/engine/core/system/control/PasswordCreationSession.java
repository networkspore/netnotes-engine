package io.netnotes.engine.core.system.control;

import io.netnotes.engine.core.system.control.ui.UIProtocol;
import io.netnotes.engine.core.system.control.ui.UIRenderer;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.daemon.ClaimedDevice;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.io.process.StreamChannel;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * PasswordCreationSession - First-run password creation
 * 
 * SECURITY CRITICAL:
 * - Passwords are NEVER stored as fields
 * - First password is held temporarily ONLY for comparison
 * - Both passwords are closed immediately after comparison
 * - Handler receives password that is valid ONLY during handler execution
 * 
 * Workflow:
 * 1. Prompt user for password
 * 2. Prompt user to confirm password
 * 3. Verify passwords match (byte-by-byte)
 * 4. If match: call onPasswordCreated handler
 * 5. If no match: clear both, retry
 * 
 * This is ONLY used during first-run setup when no SettingsData exists.
 */
public class PasswordCreationSession extends FlowProcess {
    
    private final ClaimedDevice inputDevice;
    private final UIRenderer uiRenderer;
    private final String prompt1;
    private final String prompt2;
    
    // SECURITY: No password fields! We handle passwords inline.
    private Function<NoteBytesEphemeral, CompletableFuture<Void>> onPasswordCreatedHandler;
    private Runnable onCancelledHandler;
    
    private volatile boolean active = true;
    private volatile PasswordCreationState state = PasswordCreationState.FIRST_PASSWORD;
    
    // Store first password TEMPORARILY for comparison only
    // This is the ONLY exception to "no password fields" rule
    // Cleared immediately after comparison
    private NoteBytesEphemeral temporaryFirstPassword = null;
    
    public PasswordCreationSession(
            ClaimedDevice inputDevice,
            UIRenderer uiRenderer,
            String prompt1,
            String prompt2) {
        
        super(ProcessType.SINK);
        this.inputDevice = inputDevice;
        this.uiRenderer = uiRenderer;
        this.prompt1 = prompt1;
        this.prompt2 = prompt2;
    }
    
    @Override
    public CompletableFuture<Void> run() {
        // Start first password reader
        startPasswordReader(prompt1);
        return getCompletionFuture();
    }
    
    /**
     * Start a PasswordReader for input
     */
    private void startPasswordReader(String promptText) {
        if (!active) return;
        
        // Show prompt
        uiRenderer.render(UIProtocol.showPasswordPrompt(promptText));
        
        // Create password reader
 
        PasswordReader reader = new PasswordReader();
        
        // Register reader with device
        String readerId = "password-creation-" + state.name();
        inputDevice.addEventConsumer(readerId, reader.getEventConsumer());
        
        // Handle password when ready
        reader.setOnPassword(password -> {
            // SECURITY: password valid ONLY in this block
            
            // Unregister and close reader
            inputDevice.removeEventConsumer(readerId);
            reader.close();
            
            handlePasswordReceived(password);
        });
        /*
        exceptionally(ex -> {
            // Unregister and close reader on error
            inputDevice.removeEventConsumer(readerId);
            reader.close();
            
            if (ex.getMessage().contains("cancelled")) {
                handleCancellation();
            } else {
                System.err.println("Password reader error: " + ex.getMessage());
                active = false;
                complete();
            }
            return null;
        });*/
    }
    
    /**
     * SECURITY CRITICAL: Handle received password
     * 
     * Password lifetime:
     * - FIRST_PASSWORD: Copy and store temporarily
     * - CONFIRM_PASSWORD: Compare, then close both
     */
    private void handlePasswordReceived(NoteBytesEphemeral password) {
        switch (state) {
            case FIRST_PASSWORD:
                // Store copy for comparison
                temporaryFirstPassword = password.copy();
                
                // Close original (we have copy)
                password.close();
                
                // Move to confirmation
                state = PasswordCreationState.CONFIRM_PASSWORD;
                startPasswordReader(prompt2);
                break;
                
            case CONFIRM_PASSWORD:

                //use simple password compare, re-entry is to prevent user typos, not security
                if (temporaryFirstPassword.equals(password)) {
                    // SUCCESS! Use first password (has not been closed yet)
                    handlePasswordCreated(temporaryFirstPassword);
                    
                    // Close both passwords
                    temporaryFirstPassword = null; // Handler takes ownership
                    password.close();
                    
                } else {
                    // NO MATCH - clear and retry
                    uiRenderer.render(UIProtocol.showError(
                        "Passwords do not match. Please try again."));
                    
                    // SECURITY: Close both passwords
                    temporaryFirstPassword.close();
                    temporaryFirstPassword = null;
                    password.close();
                    
                    // Restart
                    state = PasswordCreationState.FIRST_PASSWORD;
                    startPasswordReader(prompt1);
                }
                break;
            default:
                  uiRenderer.render(UIProtocol.showError(
                        "Session is unavailable"));
                break;
        }
    }
    
    
    
    /**
     * SECURITY CRITICAL: Handler receives password
     * 
     * The password passed to handler is valid ONLY during handler execution.
     * Handler MUST copy if it needs password beyond its scope.
     * 
     * After handler completes, this method closes the password.
     */
    private void handlePasswordCreated(NoteBytesEphemeral password) {
        state = PasswordCreationState.COMPLETE;
        active = false;
        
        if (onPasswordCreatedHandler == null) {
            // No handler, just close and complete
            password.close();
            complete();
            return;
        }
        
        // Call handler (handler takes ownership of password lifecycle)
        onPasswordCreatedHandler.apply(password)
            .whenComplete((v, ex) -> {
                // SECURITY: Always close password after handler
                password.close();
                
                if (ex != null) {
                    uiRenderer.render(UIProtocol.showError(
                        "Failed to create settings: " + ex.getMessage()));
                }
                complete();
            });
    }
    
    private void handleCancellation() {
        active = false;
        
        // SECURITY: Clear any stored password
        if (temporaryFirstPassword != null) {
            temporaryFirstPassword.close();
            temporaryFirstPassword = null;
        }
        
        if (onCancelledHandler != null) {
            onCancelledHandler.run();
        }
        
        complete();
    }
    
    public void cancel() {
        handleCancellation();
    }
    
    @Override
    public void kill() {
        // SECURITY: Always clear password on kill
        if (temporaryFirstPassword != null) {
            temporaryFirstPassword.close();
            temporaryFirstPassword = null;
        }
        super.kill();
    }
    
    // ===== CALLBACKS =====
    
    /**
     * Set handler for successful password creation
     * 
     * SECURITY CRITICAL:
     * Handler receives password that is valid ONLY during handler execution.
     * The password will be closed automatically when handler completes.
     * 
     * If handler needs password beyond its execution:
     * 1. Copy it: NoteBytesEphemeral copy = password.copy()
     * 2. Use the copy
     * 3. Close copy when done: copy.close()
     * 
     * Example (correct usage):
     *   session.onPasswordCreated(password -> {
     *       // password valid within this lambda
     *       return SettingsData.createSettings(password)
     *           .thenApply(settings -> {
     *               // password still valid here
     *               return null;
     *           });
     *       // password closed when this returns
     *   });
     */
    public void onPasswordCreated(
            Function<NoteBytesEphemeral, CompletableFuture<Void>> handler) {
        this.onPasswordCreatedHandler = handler;
    }
    
    /**
     * Set handler for cancellation
     */
    public void onCancelled(Runnable handler) {
        this.onCancelledHandler = handler;
    }
    
    @Override
    public CompletableFuture<Void> handleMessage(RoutedPacket packet) {
        // Not used - we use PasswordReader directly
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public void handleStreamChannel(StreamChannel channel, ContextPath fromPath) {
        throw new UnsupportedOperationException();
    }
    
    private enum PasswordCreationState {
        FIRST_PASSWORD,
        CONFIRM_PASSWORD,
        COMPLETE
    }
}