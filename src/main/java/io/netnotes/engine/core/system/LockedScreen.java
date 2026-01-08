package io.netnotes.engine.core.system;

import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.core.system.control.terminal.TextStyle;
import io.netnotes.engine.core.system.control.terminal.TerminalCommands;
import io.netnotes.engine.core.system.control.terminal.TerminalRenderState;

/**
 * LockedScreen - Shows when system is locked
 * Press any key → transition to AUTHENTICATING
 */
class LockedScreen extends TerminalScreen {
    
    public LockedScreen(String name, SystemApplication systemApplication) {
        super(name, systemApplication);
    }
    
    // ===== RENDERABLE INTERFACE =====
    
    @Override
    public TerminalRenderState getRenderState() {
        return TerminalRenderState.builder()
            .add((term) -> {
                term.clear();
                term.printAt(1, (LockedScreen.this.systemApplication.getTerminal().getCols() - 13) / 2, "System Locked", TextStyle.BOLD);
                term.printAt(5, 10, TerminalCommands.PRESS_ANY_KEY, TextStyle.NORMAL);
                term.moveCursor(5, 35);
            })
            .build();
    }
    
    // ===== LIFECYCLE =====
    
    @Override
    public CompletableFuture<Void> onShow() {
        // Set up key press handler
        return systemApplication.getTerminal().waitForKeyPress(() -> {
            // Transition to AUTHENTICATING → claims password keyboard, shows login
            systemApplication.getStateMachine().removeState(SystemApplication.LOCKED);
            systemApplication.getStateMachine().addState(SystemApplication.AUTHENTICATING);
        });
    }
    
    @Override
    public void onHide() {
        // Cancel any pending key wait
        systemApplication.getTerminal().cancelKeyWait();
    }
}