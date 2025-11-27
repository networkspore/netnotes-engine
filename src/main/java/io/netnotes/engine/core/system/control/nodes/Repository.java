package io.netnotes.engine.core.system.control.nodes;


/**
 * Repository - A source of packages (like apt repository)
 * 
 * Example:
 * - Official Netnotes repo
 * - User's GitHub repo
 * - Local file repository
 * - Third-party community repo
 */
public class Repository {
    private final String id;
    private final String name;
    private final String url;
    private final String keyUrl;  // Optional: GPG key URL for verification
    private boolean enabled;
    
    public Repository(String id, String name, String url, String keyUrl, boolean enabled) {
        this.id = id;
        this.name = name;
        this.url = url;
        this.keyUrl = keyUrl;
        this.enabled = enabled;
    }
    
    public String getId() { return id; }
    public String getName() { return name; }
    public String getUrl() { return url; }
    public String getKeyUrl() { return keyUrl; }
    public boolean isEnabled() { return enabled; }
    public boolean hasKey() { return keyUrl != null && !keyUrl.isEmpty(); }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}