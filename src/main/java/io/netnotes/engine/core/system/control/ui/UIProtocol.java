package io.netnotes.engine.core.system.control.ui;


import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesArray;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;

/**
 * UIProtocol - Builder for UI command messages
 */
public class UIProtocol {
    
    /**
     * Show menu
     */
    public static NoteBytesMap showMenu(
            String title,
            String currentPath,
            java.util.List<MenuItem> items,
            boolean hasBack) {
        
        NoteBytesMap cmd = new NoteBytesMap();
        cmd.put(Keys.CMD, UICommands.SHOW_MENU);
        cmd.put(Keys.TITLE, new NoteBytes(title));
        cmd.put(Keys.PATH, new NoteBytes(currentPath));
        cmd.put(Keys.HAS_BACK, new NoteBytes(hasBack));
        
        // Serialize menu items
        NoteBytesArray itemsArray = new NoteBytesArray();
        for (MenuItem item : items) {
            itemsArray.add(new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair(Keys.ITEM_NAME, item.name()),
               new NoteBytesPair(Keys.ITEM_DESCRIPTION, item.description()),
               new NoteBytesPair(Keys.ITEM_TYPE, item.type().name())
            }));
        }
        cmd.put(Keys.MENU_ITEMS, itemsArray);
        
        return cmd;
    }
    
    /**
     * Show password prompt
     */
    public static NoteBytesMap showPasswordPrompt(
            String prompt,
            int attemptsRemaining) {
        
        NoteBytesMap cmd = new NoteBytesMap();
        cmd.put(Keys.CMD, UICommands.SHOW_PASSWORD_PROMPT);
        cmd.put(Keys.PROMPT, new NoteBytes(prompt));
        cmd.put(Keys.ATTEMPTS_REMAINING, new NoteBytes(attemptsRemaining));
        
        return cmd;
    }

    public static NoteBytesMap showPasswordPrompt(String prompt) {
        NoteBytesMap cmd = new NoteBytesMap();
        cmd.put(Keys.CMD, UICommands.SHOW_PASSWORD_PROMPT);
        cmd.put(Keys.PROMPT, new NoteBytes(prompt));
        return cmd;
    }
    
    /**
     * Show verifying state
     */
    public static NoteBytesMap showVerifying() {
        NoteBytesMap cmd = new NoteBytesMap();
        cmd.put(Keys.CMD, UICommands.SHOW_VERIFYING);
        cmd.put(Keys.MSG, new NoteBytes("Verifying..."));
        return cmd;
    }
    
    /**
     * Clear password field (for retry)
     */
    public static NoteBytesMap passwordRetry(String errorMessage, int attemptsRemaining) {
        NoteBytesMap cmd = new NoteBytesMap();
        cmd.put(Keys.CMD, UICommands.PASSWORD_RETRY);
        cmd.put(Keys.ERROR_MESSAGE, new NoteBytes(errorMessage));
        cmd.put(Keys.ATTEMPTS_REMAINING, new NoteBytes(attemptsRemaining));
        return cmd;
    }
    
    /**
     * Password success
     */
    public static NoteBytesMap passwordSuccess() {
        NoteBytesMap cmd = new NoteBytesMap();
        cmd.put(Keys.CMD, UICommands.PASSWORD_SUCCESS);
        return cmd;
    }
    
    /**
     * Max attempts reached
     */
    public static NoteBytesMap passwordMaxAttempts() {
        NoteBytesMap cmd = new NoteBytesMap();
        cmd.put(Keys.CMD, UICommands.PASSWORD_MAX_ATTEMPTS);
        cmd.put(Keys.MSG, new NoteBytes("Maximum password attempts reached"));
        return cmd;
    }
    
    /**
     * Show message
     */
    public static NoteBytesMap showMessage(String message) {
        NoteBytesMap cmd = new NoteBytesMap();
        cmd.put(Keys.CMD, UICommands.SHOW_MESSAGE);
        cmd.put(Keys.MSG, new NoteBytes(message));
        return cmd;
    }
    
    /**
     * Show error
     */
    public static NoteBytesMap showError(String errorMessage) {
        NoteBytesMap cmd = new NoteBytesMap();
        cmd.put(Keys.CMD, UICommands.SHOW_ERROR);
        cmd.put(Keys.MSG, new NoteBytes(errorMessage));
        return cmd;
    }
    
    /**
     * Show progress
     */
    public static NoteBytesMap showProgress(String message, int percent) {
        NoteBytesMap cmd = new NoteBytesMap();
        cmd.put(Keys.CMD, UICommands.SHOW_PROGRESS);
        cmd.put(Keys.PROGRESS_MESSAGE, new NoteBytes(message));
        cmd.put(Keys.PROGRESS_PERCENT, new NoteBytes(percent));
        return cmd;
    }


    
    // ===== HELPER RECORD =====
    public record MenuItem(
        String name,
        String description,
        MenuItemType type
    ) {}
    
    public enum MenuItemType {
        ACTION,
        SUBMENU,
        PROTECTED_SUBMENU
    }
}