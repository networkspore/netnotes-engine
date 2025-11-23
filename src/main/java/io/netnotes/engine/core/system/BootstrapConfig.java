package io.netnotes.engine.core.system;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;

import java.util.ArrayList;
import java.util.List;

/**
 * BootstrapConfig - System bootstrap configuration management
 * 
 * Structure mirrors ContextPath hierarchy for intuitive navigation:
 * 
 * bootstrap/
 *   system/
 *     base/
 *       secure-input/           # IODaemon (USB security layer)
 *         installed: boolean
 *         socket_path: string
 *         auto_start: boolean
 *       command-shell/          # Command interface
 *         input-source: string  # Path to active input source
 *         history_size: integer
 *       input/                  # Input source registry
 *         sources/              # Available input sources
 *           gui-native/
 *             enabled: boolean
 *             priority: integer
 *           secure-input/
 *             enabled: boolean
 *             priority: integer
 *     network/                  # Network services
 *       enabled: boolean
 *       listen_port: integer
 *     gui/                      # GUI environment
 *       native/
 *         enabled: boolean
 *         
 * Usage:
 *   NoteBytesMap config = BootstrapConfig.createDefault();
 *   NoteBytes value = BootstrapConfig.get(config, "system", "base", "secure-input", "installed");
 *   BootstrapConfig.set(config, "system/base/secure-input/installed", new NoteBytes(true));
 */
public class BootstrapConfig {
    
    // ===== ROOT KEYS =====
    public static final String SYSTEM = "system";
    
    // ===== SYSTEM.BASE KEYS =====
    public static final String BASE = "base";
    public static final String SECURE_INPUT = "secure-input";
    public static final String COMMAND_SHELL = "command-shell";
    public static final String INPUT = "input";
    
    // ===== SYSTEM.BASE.SECURE-INPUT KEYS =====
    public static final String INSTALLED = "installed";
    public static final String SOCKET_PATH = "socket_path";
    public static final String AUTO_START = "auto_start";
    
    // ===== SYSTEM.BASE.COMMAND-SHELL KEYS =====
    public static final String INPUT_SOURCE = "input-source";
    public static final String HISTORY_SIZE = "history_size";
    public static final String ECHO_ENABLED = "echo_enabled";
    
    // ===== SYSTEM.BASE.INPUT KEYS =====
    public static final String SOURCES = "sources";
    public static final String GUI_NATIVE = "gui-native";
    public static final String ENABLED = "enabled";
    public static final String PRIORITY = "priority";
    
    // ===== SYSTEM.NETWORK KEYS =====
    public static final String NETWORK = "network";
    public static final String LISTEN_PORT = "listen_port";
    
    // ===== SYSTEM.GUI KEYS =====
    public static final String GUI = "gui";
    public static final String NATIVE = "native";
    
    // ===== DEFAULT VALUES =====
    public static final String DEFAULT_SOCKET_PATH = "/var/run/io-daemon.sock";
    public static final String DEFAULT_INPUT_SOURCE = "system/gui/native";
    public static final int DEFAULT_HISTORY_SIZE = 1000;
    public static final int DEFAULT_NETWORK_PORT = 8080;
    
    // ===== FACTORY METHODS =====
    
    /**
     * Create default bootstrap configuration
     */
    public static NoteBytesMap createDefault() {
        NoteBytesMap root = new NoteBytesMap();
        
        // Build system branch
        NoteBytesMap system = createSystemConfig();
        root.put(SYSTEM, system);
        
        return root;
    }
    
    /**
     * Create system-level configuration
     */
    private static NoteBytesMap createSystemConfig() {
        NoteBytesMap system = new NoteBytesMap();
        
        // system/base
        NoteBytesMap base = createBaseConfig();
        system.put(BASE, base);
        
        // system/network
        NoteBytesMap network = createNetworkConfig();
        system.put(NETWORK, network);
        
        // system/gui
        NoteBytesMap gui = createGUIConfig();
        system.put(GUI, gui);
        
        return system;
    }
    
    /**
     * Create base services configuration
     */
    private static NoteBytesMap createBaseConfig() {
        NoteBytesMap base = new NoteBytesMap();
        
        // system/base/secure-input
        NoteBytesMap secureInput = createSecureInputConfig();
        base.put(SECURE_INPUT, secureInput);
        
        // system/base/command-shell
        NoteBytesMap commandShell = createCommandShellConfig();
        base.put(COMMAND_SHELL, commandShell);
        
        // system/base/input
        NoteBytesMap input = createInputConfig();
        base.put(INPUT, input);
        
        return base;
    }
    
    /**
     * Create secure input (IODaemon) configuration
     */
    private static NoteBytesMap createSecureInputConfig() {
        NoteBytesMap secureInput = new NoteBytesMap();
        secureInput.put(INSTALLED, new NoteBytes(false));
        secureInput.put(SOCKET_PATH, new NoteBytes(DEFAULT_SOCKET_PATH));
        secureInput.put(AUTO_START, new NoteBytes(false));
        return secureInput;
    }
    
    /**
     * Create command shell configuration
     */
    private static NoteBytesMap createCommandShellConfig() {
        NoteBytesMap commandShell = new NoteBytesMap();
        commandShell.put(INPUT_SOURCE, new NoteBytes(DEFAULT_INPUT_SOURCE));
        commandShell.put(HISTORY_SIZE, new NoteBytes(DEFAULT_HISTORY_SIZE));
        commandShell.put(ECHO_ENABLED, new NoteBytes(true));
        return commandShell;
    }
    
    /**
     * Create input sources configuration
     */
    private static NoteBytesMap createInputConfig() {
        NoteBytesMap input = new NoteBytesMap();
        
        // sources map
        NoteBytesMap sources = new NoteBytesMap();
        
        // sources/gui-native
        NoteBytesMap guiNative = new NoteBytesMap();
        guiNative.put(ENABLED, new NoteBytes(true));
        guiNative.put(PRIORITY, new NoteBytes(10));
        sources.put(GUI_NATIVE, guiNative);
        
        // sources/secure-input
        NoteBytesMap secureInputSource = new NoteBytesMap();
        secureInputSource.put(ENABLED, new NoteBytes(false));
        secureInputSource.put(PRIORITY, new NoteBytes(5));
        sources.put(SECURE_INPUT, secureInputSource);
        
        input.put(SOURCES, sources);
        
        return input;
    }
    
    /**
     * Create network configuration
     */
    private static NoteBytesMap createNetworkConfig() {
        NoteBytesMap network = new NoteBytesMap();
        network.put(ENABLED, new NoteBytes(false));
        network.put(LISTEN_PORT, new NoteBytes(DEFAULT_NETWORK_PORT));
        return network;
    }
    
    /**
     * Create GUI configuration
     */
    private static NoteBytesMap createGUIConfig() {
        NoteBytesMap gui = new NoteBytesMap();
        
        NoteBytesMap nativeGui = new NoteBytesMap();
        nativeGui.put(ENABLED, new NoteBytes(true));
        gui.put(NATIVE, nativeGui);
        
        return gui;
    }
    
    // ===== NAVIGATION METHODS =====
    
    /**
     * Get value at path (varargs)
     * 
     * Example:
     *   get(config, "system", "base", "secure-input", "installed")
     * 
     * @param config Root configuration map
     * @param path Path segments
     * @return Value at path, or null if not found
     */
    public static NoteBytes get(NoteBytesMap config, String... path) {
        if (config == null || path == null || path.length == 0) {
            return null;
        }
        
        NoteBytesMap current = config;
        
        // Navigate to parent
        for (int i = 0; i < path.length - 1; i++) {
            NoteBytes segment = current.get(path[i]);
            
            if (segment == null) {
                return null;
            }
            
            byte type = segment.getType();
            if (type != NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
                // Not a map, can't traverse further
                return null;
            }
            
            current = segment.getAsNoteBytesMap();
        }
        
        // Get final value
        return current.get(path[path.length - 1]);
    }
    
    /**
     * Get value at path (string with separators)
     * 
     * Example:
     *   get(config, "system/base/secure-input/installed")
     * 
     * @param config Root configuration map
     * @param path Path string with '/' separators
     * @return Value at path, or null if not found
     */
    public static NoteBytes get(NoteBytesMap config, String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        
        String[] segments = path.split("/");
        return get(config, segments);
    }
    
    /**
     * Set value at path (string with separators)
     * Creates intermediate maps if they don't exist
     * 
     * Example:
     *   set(config, "system/base/secure-input/installed", new NoteBytes(true))
     * 
     * @param config Root configuration map
     * @param path Path string with '/' separators
     * @param value Value to set
     */
    public static void set(NoteBytesMap config, String path, NoteBytes value) {
        if (config == null || path == null || path.isEmpty() || value == null) {
            throw new IllegalArgumentException("Config, path, and value must not be null");
        }
        
        String[] segments = path.split("/");
        set(config, segments, value);
    }
    
    /**
     * Set value at path (varargs)
     * Creates intermediate maps if they don't exist
     * 
     * @param config Root configuration map
     * @param segments Path segments
     * @param value Value to set (last element in segments array is the key)
     */
    public static void set(NoteBytesMap config, String[] segments, NoteBytes value) {
        if (config == null || segments == null || segments.length == 0 || value == null) {
            throw new IllegalArgumentException("Invalid arguments for set");
        }
        
        NoteBytesMap current = config;
        
        // Navigate/create intermediate maps
        for (int i = 0; i < segments.length - 1; i++) {
            String key = segments[i];
            NoteBytes segment = current.get(key);
            
            if (segment == null || segment.getType() != NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
                // Create missing intermediate map
                NoteBytesMap newMap = new NoteBytesMap();
                current.put(key, newMap);
                current = newMap;
            } else {
                current = segment.getAsNoteBytesMap();
            }
        }
        
        // Set final value
        String finalKey = segments[segments.length - 1];
        current.put(finalKey, value);
    }
    
    /**
     * Check if path exists in config
     * 
     * @param config Root configuration map
     * @param path Path string with '/' separators
     * @return true if path exists, false otherwise
     */
    public static boolean exists(NoteBytesMap config, String path) {
        return get(config, path) != null;
    }
    
    /**
     * Remove value at path
     * 
     * @param config Root configuration map
     * @param path Path string with '/' separators
     * @return true if value was removed, false if path didn't exist
     */
    public static boolean remove(NoteBytesMap config, String path) {
        if (config == null || path == null || path.isEmpty()) {
            return false;
        }
        
        String[] segments = path.split("/");
        if (segments.length == 0) {
            return false;
        }
        
        NoteBytesMap current = config;
        
        // Navigate to parent
        for (int i = 0; i < segments.length - 1; i++) {
            NoteBytes segment = current.get(segments[i]);
            
            if (segment == null || segment.getType() != NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
                return false;
            }
            
            current = segment.getAsNoteBytesMap();
        }
        
        // Remove final key
        String finalKey = segments[segments.length - 1];
        return current.remove(finalKey) != null;
    }
    
    // ===== TYPED GETTERS =====
    
    /**
     * Get boolean value at path
     * 
     * @param config Root configuration map
     * @param path Path string
     * @param defaultValue Default if not found or wrong type
     * @return Boolean value
     */
    public static boolean getBoolean(NoteBytesMap config, String path, boolean defaultValue) {
        NoteBytes value = get(config, path);
        if (value == null || value.getType() != NoteBytesMetaData.BOOLEAN_TYPE) {
            return defaultValue;
        }
        return value.getAsBoolean();
    }
    
    /**
     * Get integer value at path
     * 
     * @param config Root configuration map
     * @param path Path string
     * @param defaultValue Default if not found or wrong type
     * @return Integer value
     */
    public static int getInt(NoteBytesMap config, String path, int defaultValue) {
        NoteBytes value = get(config, path);
        if (value == null || value.getType() != NoteBytesMetaData.INTEGER_TYPE) {
            return defaultValue;
        }
        return value.getAsInt();
    }
    
    /**
     * Get string value at path
     * 
     * @param config Root configuration map
     * @param path Path string
     * @param defaultValue Default if not found or wrong type
     * @return String value
     */
    public static String getString(NoteBytesMap config, String path, String defaultValue) {
        NoteBytes value = get(config, path);
        if (value == null || value.getType() != NoteBytesMetaData.STRING_TYPE) {
            return defaultValue;
        }
        return value.getAsString();
    }
    
    /**
     * Get map value at path
     * 
     * @param config Root configuration map
     * @param path Path string
     * @return NoteBytesMap at path, or null if not found
     */
    public static NoteBytesMap getMap(NoteBytesMap config, String path) {
        NoteBytes value = get(config, path);
        if (value == null || value.getType() != NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
            return null;
        }
        return value.getAsNoteBytesMap();
    }
    
    // ===== CONVENIENCE METHODS =====
    
    /**
     * Get secure input installation status
     */
    public static boolean isSecureInputInstalled(NoteBytesMap config) {
        return getBoolean(config, SYSTEM + "/" + BASE + "/" + SECURE_INPUT + "/" + INSTALLED, false);
    }
    
    /**
     * Set secure input installation status
     */
    public static void setSecureInputInstalled(NoteBytesMap config, boolean installed) {
        set(config, SYSTEM + "/" + BASE + "/" + SECURE_INPUT + "/" + INSTALLED, 
            new NoteBytes(installed));
    }
    
    /**
     * Get secure input socket path
     */
    public static String getSecureInputSocketPath(NoteBytesMap config) {
        return getString(config, SYSTEM + "/" + BASE + "/" + SECURE_INPUT + "/" + SOCKET_PATH, 
            DEFAULT_SOCKET_PATH);
    }
    
    /**
     * Get shell input source path
     */
    public static String getShellInputSource(NoteBytesMap config) {
        return getString(config, SYSTEM + "/" + BASE + "/" + COMMAND_SHELL + "/" + INPUT_SOURCE,
            DEFAULT_INPUT_SOURCE);
    }
    
    /**
     * Set shell input source path
     */
    public static void setShellInputSource(NoteBytesMap config, String sourcePath) {
        set(config, SYSTEM + "/" + BASE + "/" + COMMAND_SHELL + "/" + INPUT_SOURCE,
            new NoteBytes(sourcePath));
    }
    
    /**
     * Get all configured input sources
     * 
     * @return List of input source names
     */
    public static List<String> getInputSources(NoteBytesMap config) {
        NoteBytesMap sources = getMap(config, SYSTEM + "/" + BASE + "/" + INPUT + "/" + SOURCES);
        
        if (sources == null) {
            return new ArrayList<>();
        }
        
        List<String> sourceNames = new ArrayList<>();
        sources.forEach((key, value) -> {
            if (value.getType() == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
                NoteBytesMap sourceConfig = value.getAsNoteBytesMap();
                NoteBytes enabled = sourceConfig.get(ENABLED);
                if (enabled != null && enabled.getAsBoolean()) {
                    sourceNames.add(key.getAsString());
                }
            }
        });
        
        return sourceNames;
    }
    
    /**
     * Enable/disable an input source
     */
    public static void setInputSourceEnabled(NoteBytesMap config, String sourceName, boolean enabled) {
        String path = SYSTEM + "/" + BASE + "/" + INPUT + "/" + SOURCES + "/" + sourceName + "/" + ENABLED;
        set(config, path, new NoteBytes(enabled));
    }
    
    /**
     * Check if network is enabled
     */
    public static boolean isNetworkEnabled(NoteBytesMap config) {
        return getBoolean(config, SYSTEM + "/" + NETWORK + "/" + ENABLED, false);
    }
    
    /**
     * Get network listen port
     */
    public static int getNetworkPort(NoteBytesMap config) {
        return getInt(config, SYSTEM + "/" + NETWORK + "/" + LISTEN_PORT, DEFAULT_NETWORK_PORT);
    }
    
    // ===== VALIDATION =====
    
    /**
     * Validate configuration structure
     * Ensures all required keys exist with correct types
     * 
     * @param config Configuration to validate
     * @return true if valid, false otherwise
     */
    public static boolean validate(NoteBytesMap config) {
        if (config == null) {
            return false;
        }
        
        try {
            // Check system branch exists
            NoteBytesMap system = getMap(config, SYSTEM);
            if (system == null) {
                return false;
            }
            
            // Check base branch exists
            NoteBytesMap base = getMap(config, SYSTEM + "/" + BASE);
            if (base == null) {
                return false;
            }
            
            // Check secure-input config
            NoteBytesMap secureInput = getMap(config, SYSTEM + "/" + BASE + "/" + SECURE_INPUT);
            if (secureInput == null) {
                return false;
            }
            
            // Check command-shell config
            NoteBytesMap commandShell = getMap(config, SYSTEM + "/" + BASE + "/" + COMMAND_SHELL);
            if (commandShell == null) {
                return false;
            }
            
            // All required branches exist
            return true;
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Merge updates into existing config
     * Updates existing keys, adds new keys, preserves unmentioned keys
     * 
     * @param base Base configuration
     * @param updates Updates to apply
     */
    public static void merge(NoteBytesMap base, NoteBytesMap updates) {
        if (base == null || updates == null) {
            return;
        }
        
        updates.forEach((key, value) -> {
            if (value.getType() == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
                // Recursive merge for maps
                NoteBytes existing = base.get(key);
                if (existing != null && existing.getType() == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
                    merge(existing.getAsNoteBytesMap(), value.getAsNoteBytesMap());
                } else {
                    base.put(key, value);
                }
            } else {
                // Direct replacement for values
                base.put(key, value);
            }
        });
    }
    
    // ===== DEBUG / DISPLAY =====
    
    /**
     * Print configuration structure for debugging
     */
    public static void print(NoteBytesMap config) {
        System.out.println("Bootstrap Configuration:");
        printMap(config, "", 0);
    }
    
    private static void printMap(NoteBytesMap map, String prefix, int indent) {
        String indentStr = "  ".repeat(indent);
        
        map.forEach((key, value) -> {
            String fullPath = prefix.isEmpty() ? key.getAsString() : prefix + "/" + key;
            
            if (value.getType() == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
                System.out.println(indentStr + key + "/");
                printMap(value.getAsNoteBytesMap(), fullPath, indent + 1);
            } else {
                String valueStr = formatValue(value);
                System.out.println(indentStr + key + ": " + valueStr);
            }
        });
    }
    
    private static String formatValue(NoteBytes value) {
        byte type = value.getType();
        
        switch (type) {
            case NoteBytesMetaData.BOOLEAN_TYPE:
                return String.valueOf(value.getAsBoolean());
            case NoteBytesMetaData.INTEGER_TYPE:
                return String.valueOf(value.getAsInt());
            case NoteBytesMetaData.LONG_TYPE:
                return String.valueOf(value.getAsLong());
            case NoteBytesMetaData.STRING_TYPE:
                return "\"" + value.getAsString() + "\"";
            default:
                return "<" + value.getType() + ">";
        }
    }
}