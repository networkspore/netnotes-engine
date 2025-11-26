package io.netnotes.engine.core.system.control.ui;

import io.netnotes.engine.noteBytes.NoteBytesReadOnly;

/**
 * UICommands - Command types for UI protocol
 * 
 * Similar to ProtocolMessages for IODaemon, but for UI layer
 */
public class UICommands {
    
    // ===== DISPLAY COMMANDS =====
    public static final NoteBytesReadOnly SHOW_MENU = new NoteBytesReadOnly("show_menu");
    public static final NoteBytesReadOnly SHOW_PASSWORD_PROMPT = new NoteBytesReadOnly("show_password_prompt");
    public static final NoteBytesReadOnly SHOW_VERIFYING = new NoteBytesReadOnly("show_verifying");
    public static final NoteBytesReadOnly SHOW_MESSAGE = new NoteBytesReadOnly("show_message");
    public static final NoteBytesReadOnly SHOW_ERROR = new NoteBytesReadOnly("show_error");
    public static final NoteBytesReadOnly SHOW_PROGRESS = new NoteBytesReadOnly("show_progress");
    
    // ===== MENU COMMANDS =====
    public static final NoteBytesReadOnly MENU_ADD_ITEM = new NoteBytesReadOnly("menu_add_item");
    public static final NoteBytesReadOnly MENU_NAVIGATE = new NoteBytesReadOnly("menu_navigate");
    public static final NoteBytesReadOnly MENU_BACK = new NoteBytesReadOnly("menu_back");
    public static final NoteBytesReadOnly MENU_UPDATE = new NoteBytesReadOnly("menu_update");
    
    // ===== PASSWORD COMMANDS =====
    public static final NoteBytesReadOnly PASSWORD_CLEAR = new NoteBytesReadOnly("password_clear");
    public static final NoteBytesReadOnly PASSWORD_RETRY = new NoteBytesReadOnly("password_retry");
    public static final NoteBytesReadOnly PASSWORD_SUCCESS = new NoteBytesReadOnly("password_success");
    public static final NoteBytesReadOnly PASSWORD_MAX_ATTEMPTS = new NoteBytesReadOnly("password_max_attempts");
    public static final NoteBytesReadOnly CHANGE_PASSWORD = new NoteBytesReadOnly("change_password");
    
    // ===== RESPONSE COMMANDS (from UI to system) =====
    public static final NoteBytesReadOnly UI_MENU_SELECTED = new NoteBytesReadOnly("ui_menu_selected");
    public static final NoteBytesReadOnly UI_PASSWORD_ENTERED = new NoteBytesReadOnly("ui_password_entered");
    public static final NoteBytesReadOnly UI_CANCELLED = new NoteBytesReadOnly("ui_cancelled");
    public static final NoteBytesReadOnly UI_BACK = new NoteBytesReadOnly("ui_back");

    public static final NoteBytesReadOnly LOCK_SYSTEM = new NoteBytesReadOnly("lock_system");
    public static final NoteBytesReadOnly UNLOCK_SYSTEM = new NoteBytesReadOnly("unlock_system");
    
}