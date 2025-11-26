package io.netnotes.engine.core.system.control;

import io.netnotes.engine.core.system.control.ui.UIProtocol;
import io.netnotes.engine.core.system.control.ui.UIRenderer;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.input.InputDevice;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.io.process.StreamChannel;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * PasswordSessionProcess - Secure password verification
 * 
 * Works with any InputDevice (ClaimedDevice or BaseKeyboardInput)
 */
public class PasswordSessionProcess extends FlowProcess {
    
    private final InputDevice inputDevice;
    private final UIRenderer uiRenderer;
    private final String prompt;
    private final int maxAttempts;
    
    private int attemptCount = 0;
    private volatile boolean active = true;
    
    // Handler receives password, must return verification result
    // Password is ONLY valid during handler execution
    private Function<NoteBytesEphemeral, CompletableFuture<Boolean>> verificationHandler;
    private Runnable onMaxAttemptsHandler;
    private Runnable onCancelledHandler;
    
    // Result tracks SUCCESS/FAILURE, NOT the password
    private final CompletableFuture<Boolean> result = new CompletableFuture<>();
    
    public PasswordSessionProcess(
            InputDevice inputDevice,
            UIRenderer uiRenderer,
            String prompt,
            int maxAttempts) {
        
        super(ProcessType.SINK);
        this.inputDevice = inputDevice;
        this.uiRenderer = uiRenderer;
        this.prompt = prompt;
        this.maxAttempts = maxAttempts;
    }
    
    @Override
    public CompletableFuture<Void> run() {

        // Show prompt
        uiRenderer.render(UIProtocol.showPasswordPrompt(prompt));
        handlePasswordReady();
        return getCompletionFuture();
    }
    


    /**
     * SECURITY CRITICAL: Password handling
     * 
     * The password comes from PasswordReader via CompletableFuture.
     * We must:
     * 1. Get the password
     * 2. Immediately pass to verification handler
     * 3. Close it when handler completes
     * 4. NEVER store it anywhere
     * 5. ALWAYS close PasswordReader
     */
    private void handlePasswordReady() {
        // Create PasswordReader
        
        PasswordReader reader = new PasswordReader();
        
        // Register reader with device
        String readerId = "password-reader-" + System.currentTimeMillis();
        inputDevice.addEventConsumer(readerId, reader.getEventConsumer());
        
        // Handle password when ready
        reader.setOnPassword(password -> {
            // SECURITY: password is valid ONLY within this block
            
            // Unregister and close reader
            inputDevice.removeEventConsumer(readerId);
            reader.close();
            
            if (verificationHandler == null) {
                password.close();
            }
            
            // Call verification handler
            // Handler MUST NOT store password, only use it for verification
            verificationHandler.apply(password)
                .whenComplete((valid, ex) -> {
                    // SECURITY: Always close password when done
                    password.close();
                    
                    if (ex != null) {
                        System.err.println("Verification error: " + ex.getMessage());
                        active = false;
                        result.completeExceptionally(ex);
                        complete();
                    } else if (valid) {
                        // Success!
                        active = false;
                        result.complete(true);
                        complete();
                    } else {
                        // Failed attempt
                        attemptCount++;
                        
                        if (maxAttempts > 0 && attemptCount >= maxAttempts) {
                            // Max attempts reached
                            active = false;
                            result.complete(false);
                            
                            if (onMaxAttemptsHandler != null) {
                                onMaxAttemptsHandler.run();
                            }
                            
                            uiRenderer.render(UIProtocol.showError("Maximum attempts exceeded"));
                            complete();
                        } else {
                            // Retry
                            String msg = maxAttempts > 0 
                                ? String.format("Invalid password (%d/%d attempts)", attemptCount, maxAttempts) 
                                : "Invalid password";

                            uiRenderer.render(UIProtocol.showError(msg));
                            uiRenderer.render(UIProtocol.showPasswordPrompt(prompt));
                            
                            // Start another reader
                            handlePasswordReady();
                        }
                    }
                });
        });

        /*
          // Unregister and close reader on error
            inputDevice.removeEventConsumer(readerId);
            reader.close();
            
            System.err.println("Password reader error: " + ex.getMessage());
            active = false;
            result.completeExceptionally(ex);
            complete();
            return null;
        */
    }
    
    private void handleCancellation() {
        active = false;
        result.complete(false);
        
        if (onCancelledHandler != null) {
            onCancelledHandler.run();
        }
        
        complete();
    }
    
    public void cancel() {
        handleCancellation();
    }
    
    // ===== CALLBACKS =====
    
    /**
     * Set password verification handler
     * 
     * SECURITY CRITICAL:
     * The handler receives a NoteBytesEphemeral password that is ONLY valid
     * during handler execution. The password will be closed automatically
     * when the returned CompletableFuture completes.
     * 
     * If you need the password beyond handler execution, you MUST:
     * 1. Copy it: NoteBytesEphemeral copy = password.copy()
     * 2. Use the copy
     * 3. Close the copy when done: copy.close()
     * 
     * Example (incorrect - password invalid after return):
     *   session.onPasswordEntered(password -> {
     *       CompletableFuture<Boolean> future = new CompletableFuture<>();
     *       executor.submit(() -> {
     *           verify(password); // ERROR! password is closed
     *           future.complete(true);
     *       });
     *       return future;
     *   });
     * 
     * Example (correct - copy and close):
     *   session.onPasswordEntered(password -> {
     *       NoteBytesEphemeral copy = password.copy(); // Copy for async use
     *       CompletableFuture<Boolean> future = new CompletableFuture<>();
     *       executor.submit(() -> {
     *           try {
     *               verify(copy);
     *               future.complete(true);
     *           } finally {
     *               copy.close(); // Always close
     *           }
     *       });
     *       return future;
     *   });
     */
    public void onPasswordEntered(
            Function<NoteBytesEphemeral, CompletableFuture<Boolean>> handler) {
        this.verificationHandler = handler;
    }
    
    /**
     * Set handler for max attempts reached
     */
    public void onMaxAttemptsReached(Runnable handler) {
        this.onMaxAttemptsHandler = handler;
    }
    
    /**
     * Set handler for cancellation
     */
    public void onCancelled(Runnable handler) {
        this.onCancelledHandler = handler;
    }
    
    /**
     * Get result future (completes with true/false, NOT password)
     */
    public CompletableFuture<Boolean> getResult() {
        return result;
    }
    
    @Override
    public void handleStreamChannel(StreamChannel channel, ContextPath fromPath) {
        throw new UnsupportedOperationException();
    }


    public boolean isActive(){
        return active;
    }
}