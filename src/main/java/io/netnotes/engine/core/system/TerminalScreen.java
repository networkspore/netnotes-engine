package io.netnotes.engine.core.system;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.input.InputDevice;
import io.netnotes.engine.io.input.ephemeralEvents.EphemeralKeyDownEvent;
import io.netnotes.engine.io.input.events.KeyDownEvent;
import io.netnotes.engine.io.input.events.RoutedEvent;


/**
 * TerminalScreen - Base class for all screens
 */
abstract class TerminalScreen {
    protected final String name;
    protected SystemTerminalContainer terminal;
    protected final InputDevice keyboard;
    protected TerminalScreen parent;
    
    public TerminalScreen(String name, SystemTerminalContainer terminal, InputDevice keyboard) {
        this.name = name;
        this.terminal = terminal;
        this.keyboard = keyboard;
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
    
    public void onHide() {
        // Override if needed
    }
    
    public abstract CompletableFuture<Void> render();
    
    public CompletableFuture<Void> onResize(int rows, int cols) {
        return render();
    }
    
    public CompletableFuture<Void> handleMessage(RoutedPacket packet) {
        return CompletableFuture.completedFuture(null);
    }

     /**
     * Wait for any key press, then execute action
     */
    protected static void waitForKeyPress(InputDevice inputDevice, Runnable action) {
        Consumer<RoutedEvent> originalConsumer = inputDevice.getEventConsumer();
        inputDevice.setEventConsumer((event)->{
            if (event instanceof KeyDownEvent || event instanceof EphemeralKeyDownEvent) {
                action.run();
                // Any key pressed - cleanup and execute action
                inputDevice.setEventConsumer(originalConsumer);
            }
        });
    }
}

