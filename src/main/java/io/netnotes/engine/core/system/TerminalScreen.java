package io.netnotes.engine.core.system;

import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.io.RoutedPacket;

/**
 * TerminalScreen - Base class for all screens
 */
abstract class TerminalScreen {
    protected final String name;
    protected SystemTerminalContainer terminal;
    protected TerminalScreen parent;
    
    public TerminalScreen(String name, SystemTerminalContainer terminal) {
        this.name = name;
        this.terminal = terminal;
    }
    
    public String getName() {
        return name;
    }
    
    public void setTerminal(SystemTerminalContainer terminal) {
        this.terminal = terminal;
    }
    
    public void setParent(TerminalScreen parent) {
        this.parent = parent;
    }
    
    public TerminalScreen getParent() {
        return parent;
    }
    
    public CompletableFuture<Void> onShow() {
        return CompletableFuture.completedFuture(null);
    }
    public abstract void onHide();
    public abstract CompletableFuture<Void> render();
    

    public CompletableFuture<Void> handleMessage(RoutedPacket packet) {
        return CompletableFuture.completedFuture(null);
    }

    
}

