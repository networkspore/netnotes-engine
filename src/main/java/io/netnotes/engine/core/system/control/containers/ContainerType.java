package io.netnotes.engine.core.system.control.containers;


/**
 * ContainerType - What kind of container this is
 * 
 * Abstract types that map to UI-specific implementations:
 * - WINDOW → Desktop window, mobile screen, web browser tab
 * - PANEL → Split pane, sidebar, dashboard widget
 * - DIALOG → Modal dialog, popup, overlay
 * - TRAY → System tray icon, notification area
 * - BACKGROUND → No UI, logical grouping only
 */
public enum ContainerType {
    /**
     * Full window container
     * - Has title bar, close button, resize handles
     * - Can be minimized, maximized, moved
     * - Independent from other containers
     */
    WINDOW,
    
    /**
     * Panel within a larger container
     * - Embedded in parent window/container
     * - Can be resized within bounds
     * - Part of a layout (split, tabs, grid)
     */
    PANEL,
    
    /**
     * Modal dialog
     * - Blocks interaction with parent
     * - Usually centered, no maximize
     * - Temporary, focused interaction
     */
    DIALOG,
    
    /**
     * System tray icon
     * - Minimized presence in system tray
     * - Shows popup menu or window on click
     * - Persistent, low-profile
     */
    TRAY,
    
    /**
     * Background container (no UI)
     * - Logical grouping only
     * - No visual representation
     * - For services, daemons, background tasks
     */
    BACKGROUND,
    
    /**
     * Terminal/console panel
     * - Text-based interface
     * - Can be in window or embedded
     * - Character-cell rendering
     */
    TERMINAL
}
