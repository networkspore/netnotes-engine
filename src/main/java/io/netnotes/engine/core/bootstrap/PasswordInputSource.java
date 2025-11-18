package io.netnotes.engine.core.bootstrap;

/**
 * Available input sources for password entry
 */
public enum PasswordInputSource {
    /**
     * GUI native input (OS keyboard to Java)
     */
    GUI_NATIVE("system/gui/native"),
    
    /**
     * IODaemon secure input (USB keyboard)
     */
    IODAEMON("system/base/secure-input"),
    
    /**
     * Network input (remote shell)
     */
    NETWORK("system/network/remote");
    
    private final String path;
    
    PasswordInputSource(String path) {
        this.path = path;
    }
    
    public String getPath() {
        return path;
    }
}