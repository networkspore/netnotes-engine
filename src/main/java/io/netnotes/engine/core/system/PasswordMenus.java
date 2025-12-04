package io.netnotes.engine.core.system;

import io.netnotes.engine.core.system.control.PasswordSessionProcess;
import io.netnotes.engine.core.system.control.ui.UIProtocol;
import io.netnotes.engine.core.system.control.ui.UIRenderer;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;

import java.util.concurrent.CompletableFuture;

/**
 * PasswordMenus - Static methods for password-related operations
 * 
 * Handles password prompts for:
 * - Installation confirmation
 * - System unlock
 * - Recovery operations
 */
public class PasswordMenus {
    
    /**
     * Request password for installation
     * 
     * Returns a COPY of the password for the installation to use.
     * The original password is auto-closed by PasswordSessionProcess.
     */
    public static CompletableFuture<NoteBytesEphemeral> requestPasswordForInstallation(
        SystemSessionProcess systemSessionProcess,
        RuntimeAccess systemAccess,
        UIRenderer uiRenderer
    ) {
        CompletableFuture<NoteBytesEphemeral> passwordFuture = new CompletableFuture<>();
        
        systemSessionProcess.getSecureInputDevice()
            .thenAccept(device -> {
                PasswordSessionProcess pwdSession = new PasswordSessionProcess(
                    "installation-password-confirm",
                    device,
                    uiRenderer,
                    "Enter system password to confirm installation",
                    3
                );
                
                // Verify password
                pwdSession.onPasswordEntered(password -> {
                    return systemAccess.verifyPassword(password)
                        .thenApply(valid -> {
                            if (valid) {
                                // Password valid! Copy for installation use
                                // (original will be closed after this handler)
                                NoteBytesEphemeral passwordCopy = password.copy();
                                passwordFuture.complete(passwordCopy);
                            } else {
                                uiRenderer.render(UIProtocol.showError("Invalid password"));
                            }
                            return valid;
                        });
                });
                
                pwdSession.onMaxAttemptsReached(() -> {
                    uiRenderer.render(UIProtocol.showError(
                        "Maximum password attempts reached"));
                    passwordFuture.complete(null);
                });
                
                pwdSession.onCancelled(() -> {
                    uiRenderer.render(UIProtocol.showMessage(
                        "Installation cancelled"));
                    passwordFuture.complete(null);
                });
                
                // Need registry access to spawn - this should be injected
                // For now, this is a placeholder that shows the pattern
                // Real implementation would need ProcessRegistryInterface
                
            })
            .exceptionally(ex -> {
                passwordFuture.completeExceptionally(ex);
                return null;
            });
        
        return passwordFuture;
    }
    
    /**
     * Request password for recovery operations
     */
    public static CompletableFuture<NoteBytesEphemeral> requestPasswordForRecovery(
        SystemSessionProcess systemSessionProcess,
        RuntimeAccess systemAccess,
        UIRenderer uiRenderer,
        String prompt
    ) {
        CompletableFuture<NoteBytesEphemeral> passwordFuture = new CompletableFuture<>();
        
        systemSessionProcess.getSecureInputDevice()
            .thenAccept(device -> {
                PasswordSessionProcess pwdSession = new PasswordSessionProcess(
                    "recovery-password-verify",
                    device,
                    uiRenderer,
                    prompt,
                    3
                );
                
                pwdSession.onPasswordEntered(password -> {
                    return systemAccess.verifyOldPassword(password)
                        .thenApply(valid -> {
                            if (valid) {
                                NoteBytesEphemeral passwordCopy = password.copy();
                                passwordFuture.complete(passwordCopy);
                            } else {
                                uiRenderer.render(UIProtocol.showError(
                                    "Invalid password"));
                            }
                            return valid;
                        });
                });
                
                pwdSession.onMaxAttemptsReached(() -> {
                    passwordFuture.complete(null);
                });
                
                pwdSession.onCancelled(() -> {
                    passwordFuture.complete(null);
                });
                
            })
            .exceptionally(ex -> {
                passwordFuture.completeExceptionally(ex);
                return null;
            });
        
        return passwordFuture;
    }
}