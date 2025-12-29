package io.netnotes.engine.core.system;

import java.util.concurrent.CompletableFuture;
import io.netnotes.engine.core.system.control.terminal.TerminalCommands;

/**
 * LockedScreen - Shows when system is locked
 * Press any key → transition to AUTHENTICATING
 */
class LockedScreen extends TerminalScreen {
    
    public LockedScreen(String name, SystemTerminalContainer terminal) {
        super(name, terminal);
    }
    
    @Override
    public CompletableFuture<Void> onShow() {
        return render();
    }
    
    @Override
    public void onHide() {
    }
    
    @Override
    public CompletableFuture<Void> render() {
        return terminal.clear()
            .thenCompose(v -> terminal.printTitle("System Locked"))
            .thenCompose(v -> terminal.printAt(5, 10, TerminalCommands.PRESS_ANY_KEY))
            .thenCompose(v -> terminal.moveCursor(5, 35))
            .thenCompose(v -> terminal.waitForKeyPress(() -> {
                // Transition to AUTHENTICATING → claims password keyboard, shows login
                terminal.getState().removeState(SystemTerminalContainer.LOCKED);
                terminal.getState().addState(SystemTerminalContainer.AUTHENTICATING);
            }));
    }
}