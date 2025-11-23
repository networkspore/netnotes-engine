package io.netnotes.engine.core.system.control;


import java.util.Map;
import java.util.Stack;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import io.netnotes.engine.core.system.control.ui.*;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.io.process.StreamChannel;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.messaging.NoteMessaging.RoutedMessageExecutor;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.state.BitFlagStateMachine;

/**
 * MenuNavigatorProcess - Manages menu navigation state
 * 
 * States:
 * - IDLE (no menu shown)
 * - DISPLAYING_MENU (menu visible)
 * - NAVIGATING (transition between menus)
 * - WAITING_PASSWORD (password required for protected menu)
 * 
 * Maintains navigation stack for back button support
 * Path: /system/base/system-session/{session-id}/menu-navigator
 */
public class MenuNavigatorProcess extends FlowProcess {
    
    private final BitFlagStateMachine state;
    private final UIRenderer uiRenderer;
    
    // Navigation state
    private final Stack<MenuContext> navigationStack = new Stack<>();
    private MenuContext currentMenu;
    
    // Message dispatch
    private final Map<NoteBytesReadOnly, RoutedMessageExecutor> m_routedMsgMap = 
        new ConcurrentHashMap<>();
    
    // States
    public static final long IDLE = 1L << 0;
    public static final long DISPLAYING_MENU = 1L << 1;
    public static final long NAVIGATING = 1L << 2;
    public static final long WAITING_PASSWORD = 1L << 3;
    
    public MenuNavigatorProcess(UIRenderer uiRenderer) {
        super(ProcessType.BIDIRECTIONAL);
        this.uiRenderer = uiRenderer;
        this.state = new BitFlagStateMachine("menu-navigator");
        
        setupMessageMapping();
        setupStateTransitions();
    }
    
    private void setupMessageMapping() {
        m_routedMsgMap.put(UICommands.UI_MENU_SELECTED, this::handleMenuSelection);
        m_routedMsgMap.put(UICommands.UI_BACK, this::handleBack);
    }
    
    private void setupStateTransitions() {
        state.onStateAdded(DISPLAYING_MENU, (old, now, bit) -> {
            if (currentMenu != null) {
                displayCurrentMenu();
            }
        });
        
        state.onStateAdded(WAITING_PASSWORD, (old, now, bit) -> {
            // Parent will handle password session
            // We just wait in this state
        });
    }
    
    @Override
    public CompletableFuture<Void> run() {
        state.addState(IDLE);
        return getCompletionFuture();
    }
    
    @Override
    public CompletableFuture<Void> handleMessage(RoutedPacket packet) {
        try {
            NoteBytesMap msg = packet.getPayload().getAsNoteBytesMap();
            NoteBytesReadOnly cmd = msg.getReadOnly(Keys.CMD);
            
            if (cmd == null) {
                return CompletableFuture.completedFuture(null);
            }
            
            RoutedMessageExecutor executor = m_routedMsgMap.get(cmd);
            if (executor != null) {
                return executor.execute(msg, packet);
            }
            
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
    
    @Override
    public void handleStreamChannel(StreamChannel channel, ContextPath fromPath) {
        throw new UnsupportedOperationException();
    }
    
    /**
     * Show a menu (called by parent)
     */
    public void showMenu(MenuContext menu) {
        if (currentMenu != null) {
            navigationStack.push(currentMenu);
        }
        
        currentMenu = menu;
        
        state.removeState(IDLE);
        state.removeState(NAVIGATING);
        state.addState(DISPLAYING_MENU);
    }
    
    /**
     * Display current menu to UI
     */
    private void displayCurrentMenu() {
        currentMenu.display()
            .exceptionally(ex -> {
                System.err.println("Failed to display menu: " + ex.getMessage());
                return null;
            });
    }
    
    /**
     * Handle menu item selection
     */
    private CompletableFuture<Void> handleMenuSelection(
            NoteBytesMap msg, RoutedPacket packet) {
        
        String itemName = msg.get(Keys.ITEM_NAME).getAsString();
        
        state.removeState(DISPLAYING_MENU);
        state.addState(NAVIGATING);
        
        return currentMenu.navigate(itemName)
            .thenAccept(targetMenu -> {
                state.removeState(NAVIGATING);
                
                if (targetMenu == null) {
                    // Password required - notify parent
                    state.addState(WAITING_PASSWORD);
                    
                    NoteBytesMap request = new NoteBytesMap();
                    request.put("cmd", "request_unlock");
                    request.put("menu_item", itemName);
                    
                    emitTo(parentPath, request.getNoteBytesObject());
                    
                } else if (targetMenu == currentMenu) {
                    // Action executed, redisplay same menu
                    state.addState(DISPLAYING_MENU);
                    
                } else {
                    // Navigate to new menu
                    showMenu(targetMenu);
                }
            })
            .exceptionally(ex -> {
                System.err.println("Navigation error: " + ex.getMessage());
                state.removeState(NAVIGATING);
                state.addState(DISPLAYING_MENU);
                return null;
            });
    }
    
    /**
     * Handle back button
     */
    private CompletableFuture<Void> handleBack(
            NoteBytesMap msg, RoutedPacket packet) {
        
        if (navigationStack.isEmpty()) {
            // Already at root, notify parent
            NoteBytesMap notify = new NoteBytesMap();
            notify.put("cmd", "at_root_menu");
            emitTo(parentPath, notify.getNoteBytesObject());
            
        } else {
            // Pop back to previous menu
            MenuContext previousMenu = navigationStack.pop();
            currentMenu = previousMenu;
            
            state.removeState(WAITING_PASSWORD);
            state.addState(DISPLAYING_MENU);
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Called by parent when password session succeeds
     */
    public void onPasswordSuccess(String menuItemName) {
        state.removeState(WAITING_PASSWORD);
        state.addState(NAVIGATING);
        
        // Try navigation again (should succeed now)
        currentMenu.navigate(menuItemName)
            .thenAccept(targetMenu -> {
                state.removeState(NAVIGATING);
                if (targetMenu != null) {
                    showMenu(targetMenu);
                } else {
                    state.addState(DISPLAYING_MENU);
                }
            });
    }
    
    /**
     * Called by parent when password session fails/cancelled
     */
    public void onPasswordCancelled() {
        state.removeState(WAITING_PASSWORD);
        state.addState(DISPLAYING_MENU);
    }
    
    public BitFlagStateMachine getState() {
        return state;
    }
    
    public MenuContext getCurrentMenu() {
        return currentMenu;
    }
}