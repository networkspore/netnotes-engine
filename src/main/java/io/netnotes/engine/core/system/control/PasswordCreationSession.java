package io.netnotes.engine.core.system.control;

import io.netnotes.engine.core.system.control.containers.TerminalContainerHandle;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.input.InputDevice;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.io.process.StreamChannel;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * PasswordCreationSession - FIXED to use TerminalContainerHandle
 * 
 * CHANGES:
 * 1. Replace UIRenderer with TerminalContainerHandle
 * 2. Use terminal.print methods for prompts
 * 3. Cleaner integration with terminal system
 */
public class PasswordCreationSession extends FlowProcess {
    
    private final InputDevice inputDevice;
    private final TerminalContainerHandle terminal;
    private final String prompt1;
    private final String prompt2;
    
    private Function<NoteBytesEphemeral, CompletableFuture<Void>> onPasswordCreatedHandler;
    private Runnable onCancelledHandler;
    
    private volatile boolean active = true;
    private volatile PasswordCreationState state = PasswordCreationState.FIRST_PASSWORD;
    
    private NoteBytesEphemeral temporaryFirstPassword = null;
    
    public PasswordCreationSession(
        String name,
        InputDevice inputDevice,
        TerminalContainerHandle terminal,
        String prompt1,
        String prompt2
    ) {
        super(name, ProcessType.SINK);
        this.inputDevice = inputDevice;
        this.terminal = terminal;
        this.prompt1 = prompt1;
        this.prompt2 = prompt2;
    }
    
    @Override
    public CompletableFuture<Void> run() {
        startPasswordReader(prompt1);
        return getCompletionFuture();
    }
    
    /**
     * Start a PasswordReader for input
     */
    private void startPasswordReader(String promptText) {
        if (!active) return;
        
        // Show prompt via terminal
        terminal.clear()
            .thenCompose(v -> terminal.println(promptText))
            .thenCompose(v -> terminal.print("Password: "));
        
        PasswordReader reader = new PasswordReader();

        inputDevice.setEventConsumer(reader.getEventConsumer());
        
        reader.setOnPassword(password -> {
            inputDevice.setEventConsumer(null);
            reader.close();
            
            // Echo newline
            terminal.println("");
            
            handlePasswordReceived(password);
        });
    }
    
    /**
     * SECURITY CRITICAL: Handle received password
     */
    private void handlePasswordReceived(NoteBytesEphemeral password) {
        switch (state) {
            case FIRST_PASSWORD:
                temporaryFirstPassword = password.copy();
                password.close();
                
                state = PasswordCreationState.CONFIRM_PASSWORD;
                startPasswordReader(prompt2);
                break;
                
            case CONFIRM_PASSWORD:
                if (temporaryFirstPassword.equals(password)) {
                    handlePasswordCreated(temporaryFirstPassword);
                    temporaryFirstPassword = null;
                    password.close();
                } else {
                    terminal.printError("Passwords do not match. Please try again.\n");
                    
                    temporaryFirstPassword.close();
                    temporaryFirstPassword = null;
                    password.close();
                    
                    state = PasswordCreationState.FIRST_PASSWORD;
                    startPasswordReader(prompt1);
                }
                break;
                
            default:
                terminal.printError("Session is unavailable");
                password.close();
                break;
        }
    }
    
    /**
     * SECURITY CRITICAL: Handler receives password
     */
    private void handlePasswordCreated(NoteBytesEphemeral password) {
        state = PasswordCreationState.COMPLETE;
        active = false;
        
        if (onPasswordCreatedHandler == null) {
            password.close();
            complete();
            return;
        }
        
        onPasswordCreatedHandler.apply(password)
            .whenComplete((v, ex) -> {
                password.close();
                
                if (ex != null) {
                    terminal.printError("Failed to create settings: " + ex.getMessage());
                }
                complete();
            });
    }
    
    private void handleCancellation() {
        active = false;
        
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
        if (temporaryFirstPassword != null) {
            temporaryFirstPassword.close();
            temporaryFirstPassword = null;
        }
        super.kill();
    }
    
    public void onPasswordCreated(
            Function<NoteBytesEphemeral, CompletableFuture<Void>> handler) {
        this.onPasswordCreatedHandler = handler;
    }
    
    public void onCancelled(Runnable handler) {
        this.onCancelledHandler = handler;
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

// ============================================================================

/**
 * PasswordSessionProcess - FIXED to use TerminalContainerHandle
 */
class PasswordSessionProcess extends FlowProcess {
    
    private final InputDevice inputDevice;
    private final TerminalContainerHandle terminal;
    private final String prompt;
    private final int maxAttempts;
    
    private int attemptCount = 0;
    private volatile boolean active = true;
    
    private Function<NoteBytesEphemeral, CompletableFuture<Boolean>> verificationHandler;
    private Runnable onMaxAttemptsHandler;
    private Runnable onCancelledHandler;
    
    private final CompletableFuture<Boolean> result = new CompletableFuture<>();
    
    public PasswordSessionProcess(
        String name,
        InputDevice inputDevice,
        TerminalContainerHandle terminal,
        String prompt,
        int maxAttempts
    ) {
        super(name, ProcessType.SINK);
        this.inputDevice = inputDevice;
        this.terminal = terminal;
        this.prompt = prompt;
        this.maxAttempts = maxAttempts;
    }
    
    @Override
    public CompletableFuture<Void> run() {
        terminal.clear()
            .thenCompose(v -> terminal.println(prompt))
            .thenCompose(v -> terminal.print("Password: "))
            .thenRun(this::handlePasswordReady);
        
        return getCompletionFuture();
    }
    
    /**
     * SECURITY CRITICAL: Password handling
     */
    private void handlePasswordReady() {
        PasswordReader reader = new PasswordReader();
        
        inputDevice.setEventConsumer(reader.getEventConsumer());
        
        reader.setOnPassword(password -> {
            inputDevice.setEventConsumer(null);
            reader.close();
            
            // Echo newline
            terminal.println("");
            
            if (verificationHandler == null) {
                password.close();
                return;
            }
            
            verificationHandler.apply(password)
                .whenComplete((valid, ex) -> {
                    password.close();
                    
                    if (ex != null) {
                        System.err.println("Verification error: " + ex.getMessage());
                        active = false;
                        result.completeExceptionally(ex);
                        complete();
                    } else if (valid) {
                        active = false;
                        result.complete(true);
                        complete();
                    } else {
                        attemptCount++;
                        
                        if (maxAttempts > 0 && attemptCount >= maxAttempts) {
                            active = false;
                            result.complete(false);
                            
                            if (onMaxAttemptsHandler != null) {
                                onMaxAttemptsHandler.run();
                            }
                            
                            terminal.printError("Maximum attempts exceeded");
                            complete();
                        } else {
                            String msg = maxAttempts > 0 
                                ? String.format("Invalid password (%d/%d attempts)", 
                                    attemptCount, maxAttempts) 
                                : "Invalid password";
                            
                            terminal.printError(msg + "\n")
                                .thenCompose(v -> terminal.print("Password: "))
                                .thenRun(this::handlePasswordReady);
                        }
                    }
                });
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
    
    public void onPasswordEntered(
            Function<NoteBytesEphemeral, CompletableFuture<Boolean>> handler) {
        this.verificationHandler = handler;
    }
    
    public void onMaxAttemptsReached(Runnable handler) {
        this.onMaxAttemptsHandler = handler;
    }
    
    public void onCancelled(Runnable handler) {
        this.onCancelledHandler = handler;
    }
    
    public CompletableFuture<Boolean> getResult() {
        return result;
    }
    
    @Override
    public void handleStreamChannel(StreamChannel channel, ContextPath fromPath) {
        throw new UnsupportedOperationException();
    }
    
    public boolean isActive() {
        return active;
    }
}