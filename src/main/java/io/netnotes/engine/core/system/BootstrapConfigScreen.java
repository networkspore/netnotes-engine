package io.netnotes.engine.core.system;

import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.io.input.InputDevice;

/**
 * BootstrapConfigScreen - Bootstrap configuration
 */
class BootstrapConfigScreen extends TerminalScreen {
    
    public BootstrapConfigScreen(String name, SystemTerminalContainer terminal, InputDevice keyboard) {
        super(name, terminal, keyboard);
    }
    
    @Override
    public CompletableFuture<Void> render() {
        return terminal.clear()
            .thenCompose(v -> terminal.printTitle("Bootstrap Configuration"))
            .thenCompose(v -> terminal.printAt(5, 10, "Secure Input: [Status]"))
            .thenCompose(v -> terminal.printAt(6, 10, "Socket Path: [Path]"))
            .thenCompose(v -> terminal.printAt(10, 10, "Press any key to go back..."))
            .thenCompose(v -> terminal.printStatusLine("Bootstrap Info"));
    }
}
