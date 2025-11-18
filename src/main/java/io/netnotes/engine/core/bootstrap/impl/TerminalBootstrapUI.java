package io.netnotes.engine.core.bootstrap.impl;

import io.netnotes.engine.core.bootstrap.*;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;

import java.io.Console;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;

/**
 * Terminal-based BootstrapUI implementation
 * Uses System.console() for hidden password input when available
 */
public class TerminalBootstrapUI implements BootstrapUI {
    
    private final Scanner scanner;
    private final Console console;
    
    public TerminalBootstrapUI() {
        this.scanner = new Scanner(System.in);
        this.console = System.console();
    }
    
    @Override
    public CompletableFuture<Void> showWelcome() {
        return CompletableFuture.runAsync(() -> {
            System.out.println("\n" + "=".repeat(50));
            System.out.println("  NetNotes Engine - First Time Setup");
            System.out.println("=".repeat(50));
            System.out.println("\nWelcome! Let's configure your system.\n");
        });
    }
    
    @Override
    public void showBootProgress(String stage, int percent) {
        // Create progress bar
        int barLength = 30;
        int filled = (percent * barLength) / 100;
        String bar = "█".repeat(filled) + "░".repeat(barLength - filled);
        
        System.out.printf("\r[%s] %3d%% - %s", bar, percent, stage);
        
        if (percent >= 100) {
            System.out.println(); // New line when complete
        }
    }
    
    @Override
    public void showMessage(String message) {
        System.out.println("ℹ " + message);
    }
    
    @Override
    public void showError(String message) {
        System.err.println("✗ ERROR: " + message);
    }
    
    @Override
    public CompletableFuture<Boolean> promptInstallSecureInput() {
        return CompletableFuture.supplyAsync(() -> {
            System.out.println("\n━━━ Secure Input Configuration ━━━");
            System.out.println("Secure input allows you to use USB devices (keyboards, mice)");
            System.out.println("for authentication and command input.");
            System.out.println();
            System.out.print("Install secure input (IODaemon)? [y/N]: ");
            
            String response = scanner.nextLine().trim().toLowerCase();
            boolean install = response.equals("y") || response.equals("yes");
            
            if (install) {
                System.out.println("✓ Secure input will be installed");
            } else {
                System.out.println("✓ Using GUI native input only");
            }
            
            return install;
        });
    }
    
    @Override
    public CompletableFuture<Boolean> promptUseSecureInputForShell() {
        return CompletableFuture.supplyAsync(() -> {
            System.out.println("\n━━━ Shell Input Source ━━━");
            System.out.println("You can use secure input (USB keyboard) or GUI native input");
            System.out.println("for entering passwords and shell commands.");
            System.out.println();
            System.out.print("Use secure input for shell? [Y/n]: ");
            
            String response = scanner.nextLine().trim().toLowerCase();
            boolean useSecure = !response.equals("n") && !response.equals("no");
            
            if (useSecure) {
                System.out.println("✓ Shell will use secure input (USB keyboard)");
            } else {
                System.out.println("✓ Shell will use GUI native input");
            }
            
            return useSecure;
        });
    }
    
    @Override
    public CompletableFuture<NoteBytesEphemeral> promptPassword(
            PasswordContext context,
            PasswordInputSource source) {
        
        return CompletableFuture.supplyAsync(() -> {
            System.out.println("\n━━━ " + context.getPrompt() + " ━━━");
            
            // Show which input source we're using
            if (source == PasswordInputSource.IODAEMON) {
                System.out.println("Using secure input (USB keyboard)");
            } else {
                System.out.println("Using GUI native input");
            }
            
            System.out.print("Password: ");
            
            // Use Console for hidden input if available
            char[] passwordChars;
            if (console != null) {
                passwordChars = console.readPassword();
            } else {
                // Fallback to Scanner (visible - warn user)
                System.out.println("⚠ Warning: Console not available, password will be visible");
                passwordChars = scanner.nextLine().toCharArray();
            }
            
            // Convert to bytes
            byte[] passwordBytes = new byte[passwordChars.length];
            for (int i = 0; i < passwordChars.length; i++) {
                passwordBytes[i] = (byte) passwordChars[i];
                passwordChars[i] = 0; // Clear char array
            }
            
            return new NoteBytesEphemeral(passwordBytes);
        });
    }
    
    @Override
    public CompletableFuture<String> promptCommand() {
        return CompletableFuture.supplyAsync(() -> {
            System.out.print("\n> ");
            return scanner.nextLine();
        });
    }
    
    /**
     * Cleanup resources
     */
    public void close() {
        scanner.close();
    }
}