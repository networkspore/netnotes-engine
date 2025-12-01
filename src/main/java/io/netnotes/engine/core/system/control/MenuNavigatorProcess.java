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
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.state.BitFlagStateMachine;

/**
 * MenuNavigatorProcess - Manages menu navigation state and flow
 * 
 * Responsibilities:
 * - Maintain navigation stack (for back button)
 * - Route menu selections to appropriate handlers
 * - Track current menu context
 * - Coordinate with UIRenderer for display
 * - Handle password-protected menu unlocking
 * 
 * States:
 * - IDLE: No menu displayed
 * - DISPLAYING_MENU: Menu visible to user
 * - NAVIGATING: Transitioning between menus
 * - WAITING_PASSWORD: Password required for protected menu
 * - EXECUTING_ACTION: Running menu item action
 * 
 * Path: /system/base/system-session/{session-id}/menu-navigator
 */
public class MenuNavigatorProcess extends FlowProcess {

    private final BitFlagStateMachine state;
    private final UIRenderer uiRenderer;
    
    // Navigation state
    private final Stack<MenuContext> navigationStack = new Stack<>();
    private MenuContext currentMenu;
    private String pendingMenuItem; // Item waiting for password unlock
    
    // Message dispatch
    private final Map<NoteBytesReadOnly, RoutedMessageExecutor> m_routedMsgMap = 
        new ConcurrentHashMap<>();
    
    // States
    public static final long IDLE = 1L << 0;
    public static final long DISPLAYING_MENU = 1L << 1;
    public static final long NAVIGATING = 1L << 2;
    public static final long WAITING_PASSWORD = 1L << 3;
    public static final long EXECUTING_ACTION = 1L << 4;
    
    public MenuNavigatorProcess(String name, UIRenderer uiRenderer) {
        super(name, ProcessType.BIDIRECTIONAL);
        this.uiRenderer = uiRenderer;
        this.state = new BitFlagStateMachine("menu-navigator");
        
        setupMessageMapping();
        setupStateTransitions();
    }
    
    private void setupMessageMapping() {
        m_routedMsgMap.put(UICommands.UI_MENU_SELECTED, this::handleMenuSelection);
        m_routedMsgMap.put(UICommands.UI_BACK, this::handleBack);
        m_routedMsgMap.put(UICommands.UI_PASSWORD_ENTERED, this::handlePasswordEntered);
        m_routedMsgMap.put(UICommands.UI_CANCELLED, this::handleCancelled);
    }
    
    private void setupStateTransitions() {
        state.onStateAdded(IDLE, (old, now, bit) -> {
            System.out.println("[MenuNavigator] IDLE - No menu displayed");
        });
        
        state.onStateAdded(DISPLAYING_MENU, (old, now, bit) -> {
            System.out.println("[MenuNavigator] DISPLAYING_MENU - Menu visible");
            if (currentMenu != null) {
                displayCurrentMenu();
            }
        });
        
        state.onStateAdded(NAVIGATING, (old, now, bit) -> {
            System.out.println("[MenuNavigator] NAVIGATING - Transitioning between menus");
        });
        
        state.onStateAdded(WAITING_PASSWORD, (old, now, bit) -> {
            System.out.println("[MenuNavigator] WAITING_PASSWORD - Password required");
            // Parent will handle password session
        });
        
        state.onStateAdded(EXECUTING_ACTION, (old, now, bit) -> {
            System.out.println("[MenuNavigator] EXECUTING_ACTION - Running menu action");
        });
        
        // State exit handlers
        state.onStateRemoved(DISPLAYING_MENU, (old, now, bit) -> {
            System.out.println("[MenuNavigator] Left DISPLAYING_MENU state");
        });
        
        state.onStateRemoved(WAITING_PASSWORD, (old, now, bit) -> {
            System.out.println("[MenuNavigator] Password session ended");
            pendingMenuItem = null; // Clear pending item
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
                System.err.println("[MenuNavigator] Received message without 'cmd' field");
                return CompletableFuture.completedFuture(null);
            }
            
            RoutedMessageExecutor executor = m_routedMsgMap.get(cmd);
            if (executor != null) {
                return executor.execute(msg, packet);
            } else {
                System.err.println("[MenuNavigator] Unknown command: " + cmd);
            }
            
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            System.err.println("[MenuNavigator] Error handling message: " + e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }
    
    @Override
    public void handleStreamChannel(StreamChannel channel, ContextPath fromPath) {
        throw new UnsupportedOperationException("MenuNavigator does not handle streams");
    }
    
    // ===== MENU DISPLAY =====
    
    /**
     * Show a menu (called by parent process)
     */
    public void showMenu(MenuContext menu) {
        if (menu == null) {
            System.err.println("[MenuNavigator] Cannot show null menu");
            return;
        }
        
        // Save current menu to stack if we have one
        if (currentMenu != null && currentMenu != menu) {
            navigationStack.push(currentMenu);
            System.out.println("[MenuNavigator] Pushed menu to stack: " + currentMenu.getTitle());
        }
        
        currentMenu = menu;
        
        // Transition to displaying state
        state.removeState(IDLE);
        state.removeState(NAVIGATING);
        state.removeState(EXECUTING_ACTION);
        state.addState(DISPLAYING_MENU);
    }
    
    /**
     * Display current menu via UIRenderer
     */
    private void displayCurrentMenu() {
        if (currentMenu == null) {
            System.err.println("[MenuNavigator] No current menu to display");
            return;
        }
        
        currentMenu.display()
            .exceptionally(ex -> {
                System.err.println("[MenuNavigator] Failed to display menu: " + ex.getMessage());
                
                // Show error message via renderer
                uiRenderer.render(UIProtocol.showError(
                    "Failed to display menu: " + ex.getMessage()
                ));
                
                // Return to previous menu if available
                if (!navigationStack.isEmpty()) {
                    currentMenu = navigationStack.pop();
                    state.addState(DISPLAYING_MENU);
                } else {
                    state.removeState(DISPLAYING_MENU);
                    state.addState(IDLE);
                }
                
                return null;
            });
    }
    
    /**
     * Refresh current menu (re-display)
     */
    public void refreshMenu() {
        if (state.hasState(DISPLAYING_MENU) && currentMenu != null) {
            displayCurrentMenu();
        }
    }
    
    // ===== MESSAGE HANDLERS =====
    
    /**
     * Handle menu item selection
     */
    private CompletableFuture<Void> handleMenuSelection(
            NoteBytesMap msg, RoutedPacket packet) {
        
        NoteBytes itemNameBytes = msg.get(Keys.ITEM_NAME);
        if (itemNameBytes == null) {
            System.err.println("[MenuNavigator] Menu selection without item_name");
            return CompletableFuture.completedFuture(null);
        }
        
        String itemName = itemNameBytes.getAsString();
        System.out.println("[MenuNavigator] Item selected: " + itemName);
        
        // Transition to navigating state
        state.removeState(DISPLAYING_MENU);
        state.addState(NAVIGATING);
        
        return currentMenu.navigate(itemName)
            .thenAccept(targetMenu -> {
                state.removeState(NAVIGATING);
                
                if (targetMenu == null) {
                    // Password required - notify parent
                    System.out.println("[MenuNavigator] Password required for: " + itemName);
                    state.addState(WAITING_PASSWORD);
                    pendingMenuItem = itemName;
                    
                    notifyParentPasswordRequired(itemName);
                    
                } else if (targetMenu == currentMenu) {
                    // Action executed, stay on same menu
                    System.out.println("[MenuNavigator] Action executed, staying on current menu");
                    state.addState(DISPLAYING_MENU);
                    
                } else {
                    // Navigate to new menu
                    System.out.println("[MenuNavigator] Navigating to: " + targetMenu.getTitle());
                    showMenu(targetMenu);
                }
            })
            .exceptionally(ex -> {
                System.err.println("[MenuNavigator] Navigation error: " + ex.getMessage());
                
                // Show error via renderer
                uiRenderer.render(UIProtocol.showError(
                    "Navigation failed: " + ex.getMessage()
                ));
                
                // Return to displaying current menu
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
        
        System.out.println("[MenuNavigator] Back requested");
        
        if (navigationStack.isEmpty()) {
            // Already at root
            System.out.println("[MenuNavigator] At root menu, notifying parent");
            notifyParentAtRoot();
            
        } else {
            // Pop back to previous menu
            MenuContext previousMenu = navigationStack.pop();
            System.out.println("[MenuNavigator] Returning to: " + previousMenu.getTitle());
            
            currentMenu = previousMenu;
            
            state.removeState(WAITING_PASSWORD);
            state.removeState(EXECUTING_ACTION);
            state.addState(DISPLAYING_MENU);
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Handle password entered (for protected menus)
     */
    private CompletableFuture<Void> handlePasswordEntered(
            NoteBytesMap msg, RoutedPacket packet) {
        
        // This should be handled by parent's password session
        // We just forward the message
        if (parentPath != null) {
            emitTo(parentPath, msg.getNoteBytesObject());
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Handle cancellation
     */
    private CompletableFuture<Void> handleCancelled(
            NoteBytesMap msg, RoutedPacket packet) {
        
        System.out.println("[MenuNavigator] Cancellation received");
        
        // If waiting for password, return to menu
        if (state.hasState(WAITING_PASSWORD)) {
            state.removeState(WAITING_PASSWORD);
            state.addState(DISPLAYING_MENU);
            pendingMenuItem = null;
        }
        
        // Notify parent
        if (parentPath != null) {
            NoteBytesMap notify = new NoteBytesMap();
            notify.put(Keys.CMD, new NoteBytes("menu_cancelled"));
            emitTo(parentPath, notify.getNoteBytesObject());
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    // ===== PARENT COMMUNICATION =====
    
    /**
     * Called by parent when password session succeeds
     */
    public void onPasswordSuccess(String menuItemName) {
        if (!state.hasState(WAITING_PASSWORD)) {
            System.err.println("[MenuNavigator] Password success but not waiting for password");
            return;
        }
        
        System.out.println("[MenuNavigator] Password verified, navigating to: " + menuItemName);
        
        state.removeState(WAITING_PASSWORD);
        state.addState(NAVIGATING);
        
        // Try navigation again (should succeed now)
        currentMenu.navigate(menuItemName)
            .thenAccept(targetMenu -> {
                state.removeState(NAVIGATING);
                if (targetMenu != null) {
                    showMenu(targetMenu);
                } else {
                    // Still can't access? Return to menu
                    System.err.println("[MenuNavigator] Still can't access menu after password");
                    state.addState(DISPLAYING_MENU);
                }
            })
            .exceptionally(ex -> {
                System.err.println("[MenuNavigator] Post-password navigation failed: " + ex.getMessage());
                state.removeState(NAVIGATING);
                state.addState(DISPLAYING_MENU);
                return null;
            });
    }
    
    /**
     * Called by parent when password session fails/cancelled
     */
    public void onPasswordCancelled() {
        System.out.println("[MenuNavigator] Password cancelled, returning to menu");
        
        state.removeState(WAITING_PASSWORD);
        state.addState(DISPLAYING_MENU);
        pendingMenuItem = null;
    }
    
    /**
     * Notify parent that password is required
     */
    private void notifyParentPasswordRequired(String itemName) {
        if (parentPath == null) {
            System.err.println("[MenuNavigator] No parent to notify about password requirement");
            return;
        }
        
        NoteBytesMap request = new NoteBytesMap();
        request.put(Keys.CMD, new NoteBytes("request_unlock"));
        request.put(Keys.ITEM_NAME, new NoteBytes(itemName));
        
        emitTo(parentPath, request.getNoteBytesObject());
    }
    
    /**
     * Notify parent that we're at root menu (back pressed at top)
     */
    private void notifyParentAtRoot() {
        if (parentPath == null) {
            return;
        }
        
        NoteBytesMap notify = new NoteBytesMap();
        notify.put(Keys.CMD, new NoteBytes("at_root_menu"));
        
        emitTo(parentPath, notify.getNoteBytesObject());
    }
    
    // ===== NAVIGATION CONTROL =====
    
    /**
     * Clear navigation stack
     */
    public void clearNavigationStack() {
        navigationStack.clear();
        System.out.println("[MenuNavigator] Navigation stack cleared");
    }
    
    /**
     * Get navigation depth (how many menus deep)
     */
    public int getNavigationDepth() {
        return navigationStack.size();
    }
    
    /**
     * Navigate to root menu
     */
    public void navigateToRoot() {
        if (navigationStack.isEmpty()) {
            return;
        }
        
        // Pop to root
        while (navigationStack.size() > 1) {
            navigationStack.pop();
        }
        
        if (!navigationStack.isEmpty()) {
            currentMenu = navigationStack.pop();
            state.removeState(WAITING_PASSWORD);
            state.removeState(EXECUTING_ACTION);
            state.addState(DISPLAYING_MENU);
        }
    }
    
    // ===== GETTERS =====
    
    public BitFlagStateMachine getState() {
        return state;
    }
    
    public MenuContext getCurrentMenu() {
        return currentMenu;
    }
    
    public boolean hasMenu() {
        return currentMenu != null;
    }
    
    public boolean isDisplayingMenu() {
        return state.hasState(DISPLAYING_MENU);
    }
    
    public boolean isWaitingForPassword() {
        return state.hasState(WAITING_PASSWORD);
    }
    
    public String getPendingMenuItem() {
        return pendingMenuItem;
    }
    
    public UIRenderer getUIRenderer() {
        return uiRenderer;
    }
}