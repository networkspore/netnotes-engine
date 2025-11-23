package io.netnotes.engine.core.system.control;

import io.netnotes.engine.core.system.control.ui.UIProtocol;
import io.netnotes.engine.core.system.control.ui.UIRenderer;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.daemon.ClaimedDevice;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.io.process.StreamChannel;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * PasswordSessionProcess - Secure password verification
 * 
 * SECURITY CRITICAL:
 * - NoteBytesEphemeral passwords are NEVER stored as fields
 * - Passwords are immediately verified and closed
 * - CompletableFutures complete with Boolean, NOT passwords
 * - All password copies are explicitly closed
 * 
 * Usage:
 *   session.onPasswordEntered(password -> {
 *       // password is valid ONLY within this lambda
 *       // must copy if needed beyond this scope
 *       return verify(password).thenApply(valid -> {
 *           // password still accessible here
 *           return valid;
 *       });
 *       // password is CLOSED when this returns
 *   });
 */
public class PasswordSessionProcess extends FlowProcess {
    
    private final ClaimedDevice inputDevice;
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
            ClaimedDevice inputDevice,
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
        // Subscribe to input device
        inputDevice.subscribe(getSubscriber());
        
        // Show prompt
        uiRenderer.render(UIProtocol.showPasswordPrompt(prompt));
        
        return getCompletionFuture();
    }
    
    @Override
    public CompletableFuture<Void> handleMessage(RoutedPacket packet) {
        if (!active) {
            return CompletableFuture.completedFuture(null);
        }
        
        try {
            NoteBytesMap msg = packet.getPayload().getAsNoteBytesMap();
            String eventType = msg.get("event").getAsString();
            
            if ("password_ready".equals(eventType)) {
                // PasswordReader has completed
                handlePasswordReady();
            } else if ("cancelled".equals(eventType)) {
                handleCancellation();
            }
            
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
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
     */
    private void handlePasswordReady() {
        // Create PasswordReader
        CompletableFuture<NoteBytesEphemeral> passwordFuture = new CompletableFuture<>();
        PasswordReader reader = new PasswordReader(passwordFuture);
        
        // Register reader with device
        String readerId = "password-reader-" + System.currentTimeMillis();
        inputDevice.addEventConsumer(readerId, reader.getEventConsumer());
        
        // Handle password when ready
        passwordFuture.thenCompose(password -> {
            // SECURITY: password is valid ONLY within this block
            
            // Unregister reader
            inputDevice.removeEventConsumer(readerId);
            
            if (verificationHandler == null) {
                password.close();
                return CompletableFuture.completedFuture(false);
            }
            
            // Call verification handler
            // Handler MUST NOT store password, only use it for verification
            return verificationHandler.apply(password)
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
                        
                        if (attemptCount >= maxAttempts) {
                            // Max attempts reached
                            active = false;
                            result.complete(false);
                            
                            if (onMaxAttemptsHandler != null) {
                                onMaxAttemptsHandler.run();
                            }
                            
                            uiRenderer.render(UIProtocol.showError(
                                "Maximum attempts exceeded"));
                            complete();
                        } else {
                            // Retry
                            uiRenderer.render(UIProtocol.showError(
                                String.format("Invalid password (%d/%d attempts)", 
                                    attemptCount, maxAttempts)));
                            uiRenderer.render(UIProtocol.showPasswordPrompt(prompt));
                            
                            // Start another reader
                            handlePasswordReady();
                        }
                    }
                });
        }).exceptionally(ex -> {
            // Unregister reader on error
            inputDevice.removeEventConsumer(readerId);
            
            System.err.println("Password reader error: " + ex.getMessage());
            active = false;
            result.completeExceptionally(ex);
            complete();
            return null;
        });
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
}