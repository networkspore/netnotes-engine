package io.netnotes.engine.core.bootstrap.impl;

import io.netnotes.engine.core.bootstrap.*;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;

import java.util.concurrent.CompletableFuture;

/**
 * Headless BootstrapUI implementation
 * Uses pre-configured responses for automated setup
 * Useful for testing and scripted deployments
 */
public class HeadlessBootstrapUI implements BootstrapUI {
    
    private final boolean installSecureInput;
    private final boolean useSecureInputForShell;
    private final String defaultPassword;
    
    public HeadlessBootstrapUI(
            boolean installSecureInput,
            boolean useSecureInputForShell,
            String defaultPassword) {
        this.installSecureInput = installSecureInput;
        this.useSecureInputForShell = useSecureInputForShell;
        this.defaultPassword = defaultPassword;
    }
    
    /**
     * Convenience constructor for testing (no secure input, simple password)
     */
    public HeadlessBootstrapUI(String password) {
        this(false, false, password);
    }
    
    @Override
    public CompletableFuture<Void> showWelcome() {
        System.out.println("[HEADLESS] Starting automated setup");
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public void showBootProgress(String stage, int percent) {
        System.out.println("[HEADLESS] " + percent + "% - " + stage);
    }
    
    @Override
    public void showMessage(String message) {
        System.out.println("[HEADLESS] INFO: " + message);
    }
    
    @Override
    public void showError(String message) {
        System.err.println("[HEADLESS] ERROR: " + message);
    }
    
    @Override
    public CompletableFuture<Boolean> promptInstallSecureInput() {
        System.out.println("[HEADLESS] Install secure input: " + installSecureInput);
        return CompletableFuture.completedFuture(installSecureInput);
    }
    
    @Override
    public CompletableFuture<Boolean> promptUseSecureInputForShell() {
        System.out.println("[HEADLESS] Use secure input for shell: " + useSecureInputForShell);
        return CompletableFuture.completedFuture(useSecureInputForShell);
    }
    
    @Override
    public CompletableFuture<NoteBytesEphemeral> promptPassword(
            PasswordContext context,
            PasswordInputSource source) {
        
        System.out.println("[HEADLESS] Password prompt (" + context.getPrompt() + ")");
        
        byte[] passwordBytes = defaultPassword.getBytes();
        return CompletableFuture.completedFuture(new NoteBytesEphemeral(passwordBytes));
    }
    
    @Override
    public CompletableFuture<String> promptCommand() {
        return CompletableFuture.completedFuture("exit");
    }
}