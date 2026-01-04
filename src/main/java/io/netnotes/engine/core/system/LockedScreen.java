package io.netnotes.engine.core.system;

import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.core.system.control.terminal.RenderManager.RenderState;
import io.netnotes.engine.core.system.control.terminal.TextStyle;
import io.netnotes.engine.core.system.control.terminal.TerminalCommands;

/**
 * LockedScreen - Shows when system is locked
 * Press any key → transition to AUTHENTICATING
 */
class LockedScreen extends TerminalScreen {
    
    public LockedScreen(String name, SystemTerminalContainer terminal) {
        super(name, terminal);
    }
    
    // ===== RENDERABLE INTERFACE =====
    
    @Override
    public RenderState getRenderState() {
        return RenderState.builder()
            .add((term) -> {
                term.clear();
                term.printAt(1, (LockedScreen.this.terminal.getCols() - 13) / 2, "System Locked", TextStyle.BOLD);
                term.printAt(5, 10, TerminalCommands.PRESS_ANY_KEY, TextStyle.NORMAL);
                term.moveCursor(5, 35);
            })
            .build();
    }
    
    // ===== LIFECYCLE =====
    
    @Override
    public CompletableFuture<Void> onShow() {
        // Set up key press handler
        return terminal.waitForKeyPress(() -> {
            // Transition to AUTHENTICATING → claims password keyboard, shows login
            terminal.getState().removeState(SystemTerminalContainer.LOCKED);
            terminal.getState().addState(SystemTerminalContainer.AUTHENTICATING);
        });
    }
    
    @Override
    public void onHide() {
        // Cancel any pending key wait
        terminal.cancelKeyWait();
    }
}