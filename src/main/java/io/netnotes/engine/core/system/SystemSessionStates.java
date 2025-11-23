package io.netnotes.engine.core.system;

import io.netnotes.engine.state.BitFlagStateMachine;

/**
 * SystemSessionStates - State flags for SystemSessionProcess
 * 
 * Initialization Flow States:
 * INITIALIZING → BOOTSTRAP_LOADED → [FIRST_RUN_SETUP | SETTINGS_EXIST] → READY
 * 
 * Lock/Unlock States:
 * READY → LOCKED → UNLOCKING → UNLOCKED
 */
public class SystemSessionStates {
    
    // ===== INITIALIZATION STATES =====
    
    /**
     * System is starting up, loading bootstrap config
     */
    public static final long INITIALIZING        = 1L << 0;
    
    /**
     * Bootstrap config loaded successfully
     */
    public static final long BOOTSTRAP_LOADED    = 1L << 1;
    
    /**
     * First run detected - no settings file exists
     * Need to create new password and SettingsData
     */
    public static final long FIRST_RUN_SETUP     = 1L << 2;
    
    /**
     * Settings file exists, need to load and verify
     */
    public static final long SETTINGS_EXIST      = 1L << 3;
    
    /**
     * Settings map loaded (contains BCrypt hash)
     * Ready to verify password
     */
    public static final long SETTINGS_LOADED     = 1L << 4;
    
    /**
     * SettingsData fully initialized
     * System ready to use
     */
    public static final long READY               = 1L << 5;
    
    // ===== LOCK STATES =====
    
    /**
     * System is locked, UI access restricted
     * SettingsData still exists in memory
     */
    public static final long LOCKED              = 1L << 6;
    
    /**
     * Password verification in progress for unlock
     */
    public static final long UNLOCKING           = 1L << 7;
    
    /**
     * System unlocked, full UI access
     */
    public static final long UNLOCKED            = 1L << 8;
    
    // ===== UI STATES =====
    
    /**
     * UI renderer connected and ready
     */
    public static final long UI_CONNECTED        = 1L << 9;
    
    /**
     * Currently showing a menu
     */
    public static final long SHOWING_MENU        = 1L << 10;
    
    /**
     * Currently in password prompt
     */
    public static final long PASSWORD_PROMPT     = 1L << 11;
    
    // ===== SERVICE STATES =====
    
    /**
     * Secure input device is available
     */
    public static final long SECURE_INPUT_ACTIVE = 1L << 12;
    
    // ===== HELPER METHODS =====
    
    /**
     * Check if system can be unlocked
     */
    public static boolean canUnlock(BitFlagStateMachine state) {
        return state.hasState(LOCKED) && 
               state.hasState(READY) &&
               !state.hasState(UNLOCKING);
    }
    
    /**
     * Check if system can navigate menus
     */
    public static boolean canNavigateMenu(BitFlagStateMachine state) {
        return state.hasState(READY) && 
               state.hasState(UI_CONNECTED) &&
               !state.hasState(PASSWORD_PROMPT);
    }
    
    /**
     * Check if initialization is complete
     */
    public static boolean isInitialized(BitFlagStateMachine state) {
        return state.hasState(READY);
    }
    
    /**
     * Check if in first-run setup
     */
    public static boolean isFirstRun(BitFlagStateMachine state) {
        return state.hasState(FIRST_RUN_SETUP);
    }
    
    /**
     * Get human-readable state description
     */
    public static String describe(BitFlagStateMachine state) {
        StringBuilder sb = new StringBuilder();
        
        if (state.hasState(INITIALIZING)) sb.append("INITIALIZING ");
        if (state.hasState(BOOTSTRAP_LOADED)) sb.append("BOOTSTRAP_LOADED ");
        if (state.hasState(FIRST_RUN_SETUP)) sb.append("FIRST_RUN_SETUP ");
        if (state.hasState(SETTINGS_EXIST)) sb.append("SETTINGS_EXIST ");
        if (state.hasState(SETTINGS_LOADED)) sb.append("SETTINGS_LOADED ");
        if (state.hasState(READY)) sb.append("READY ");
        if (state.hasState(LOCKED)) sb.append("LOCKED ");
        if (state.hasState(UNLOCKING)) sb.append("UNLOCKING ");
        if (state.hasState(UNLOCKED)) sb.append("UNLOCKED ");
        if (state.hasState(UI_CONNECTED)) sb.append("UI_CONNECTED ");
        if (state.hasState(SHOWING_MENU)) sb.append("SHOWING_MENU ");
        if (state.hasState(PASSWORD_PROMPT)) sb.append("PASSWORD_PROMPT ");
        if (state.hasState(SECURE_INPUT_ACTIVE)) sb.append("SECURE_INPUT_ACTIVE ");
        
        return sb.toString().trim();
    }
}