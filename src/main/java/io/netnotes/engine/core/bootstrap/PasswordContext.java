package io.netnotes.engine.core.bootstrap;

/**
 * Context for password prompts
 */
public enum PasswordContext {
    /**
     * First-time setup - creating new password
     */
    FIRST_TIME_SETUP("Create a password"),
    
    /**
     * Confirming password during creation
     */
    CONFIRM("Confirm your password"),
    
    /**
     * Unlocking existing system
     */
    UNLOCK("Enter your password"),
    
    /**
     * Changing password (requires old password first)
     */
    CHANGE_OLD("Enter your current password"),
    
    /**
     * Changing password (entering new password)
     */
    CHANGE_NEW("Enter your new password");
    
    private final String prompt;
    
    PasswordContext(String prompt) {
        this.prompt = prompt;
    }
    
    public String getPrompt() {
        return prompt;
    }
}