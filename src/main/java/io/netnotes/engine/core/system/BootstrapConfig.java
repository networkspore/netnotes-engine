package io.netnotes.engine.core.system;

import io.netnotes.engine.core.CoreConstants;
import io.netnotes.engine.core.SettingsData;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.io.process.StreamChannel;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.messaging.NoteMessaging.ProtocolMesssages;
import io.netnotes.engine.messaging.NoteMessaging.RoutedMessageExecutor;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;
import io.netnotes.engine.state.BitFlagStateMachine;
import io.netnotes.engine.utils.VirtualExecutors;
import io.netnotes.engine.utils.LoggingHelpers.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * BootstrapConfig - Singleton process managing system bootstrap configuration
 * 
 * NEW ARCHITECTURE:
 * - Lives at: /system/bootstrap-config
 * - Manages bootstrap config as FlowProcess
 * - Handles concurrent reads/writes with ReadWriteLock
 * - Auto-saves changes to disk
 * - Notifies subscribers of changes
 * - Thread-safe access from multiple sessions
 * 
 * Structure remains the same:
 * bootstrap/
 *   system/
 *     base/
 *       secure-input/
 *       command-shell/
 *       input/
 *     network/
 *     gui/
 * 
 * Commands:
 * - GET: Get value at path
 * - SET: Set value at path (auto-saves)
 * - MERGE: Merge updates (auto-saves)
 * - VALIDATE: Check structure
 * - SUBSCRIBE: Get notified of changes
 * - UNSUBSCRIBE: Stop notifications
 * 
 * Usage:
 * <pre>
 * // Request value
 * NoteBytesMap request = BootstrapConfig.CMD.get("system/base/secure-input/installed");
 * processInterface.request(BOOTSTRAP_CONFIG_PATH, request)
 *     .thenAccept(response -> {
 *         boolean installed = response.get(Keys.DATA).getAsBoolean();
 *     });
 * 
 * // Set value (auto-saves)
 * NoteBytesMap setReq = BootstrapConfig.CMD.set(
 *     "system/base/secure-input/installed", 
 *     new NoteBytes(true)
 * );
 * processInterface.sendMessage(BOOTSTRAP_CONFIG_PATH, setReq);
 * </pre>
 */
public class BootstrapConfig extends FlowProcess {
    
    public static final String NAME = "bootstrap-config";
    public static final ContextPath BOOTSTRAP_CONFIG_PATH = CoreConstants.SYSTEM_PATH.append(NAME);
    
    // ===== SINGLETON INSTANCE =====
    private static volatile BootstrapConfig instance = null;
    private static final Object INSTANCE_LOCK = new Object();
    
    // ===== STATE =====
    private final BitFlagStateMachine state;
    private NoteBytesMap data;
    private final ReadWriteLock dataLock = new ReentrantReadWriteLock();
    
    // ===== SUBSCRIBERS =====
    private final Map<ContextPath, ConfigChangeListener> subscribers = new ConcurrentHashMap<>();
    
    // ===== MESSAGE HANDLERS =====
    private final Map<NoteBytesReadOnly, RoutedMessageExecutor> msgHandlers = new ConcurrentHashMap<>();
    
    // ===== CONSTANTS (same as before) =====
    public static final String SYSTEM = "system";
    public static final String BASE = "base";
    public static final String SECURE_INPUT = "secure-input";
    public static final String COMMAND_SHELL = "command-shell";
    public static final String INPUT = "input";
    public static final String INSTALLED = "installed";
    public static final String SOCKET_PATH = "socket_path";
    public static final String AUTO_START = "auto_start";
    public static final String INPUT_SOURCE = "input-source";
    public static final String HISTORY_SIZE = "history_size";
    public static final String ECHO_ENABLED = "echo_enabled";
    public static final String SOURCES = "sources";
    public static final String GUI_NATIVE = "gui-native";
    public static final String ENABLED = "enabled";
    public static final String PRIORITY = "priority";
    public static final String NETWORK = "network";
    public static final String LISTEN_PORT = "listen_port";
    public static final String GUI = "gui";
    public static final String NATIVE = "native";
    public static final String DEFAULT_SOCKET_PATH = "/var/run/io-daemon.sock";
    public static final String DEFAULT_INPUT_SOURCE = "system/gui/native";
    public static final int DEFAULT_HISTORY_SIZE = 1000;
    public static final int DEFAULT_NETWORK_PORT = 8080;
    
    // ===== COMMANDS =====
    public static final class CMD {
        public static final NoteBytesReadOnly GET = new NoteBytesReadOnly("get");
        public static final NoteBytesReadOnly SET = new NoteBytesReadOnly("set");
        public static final NoteBytesReadOnly MERGE = new NoteBytesReadOnly("merge");
        public static final NoteBytesReadOnly VALIDATE = new NoteBytesReadOnly("validate");
        public static final NoteBytesReadOnly SUBSCRIBE = new NoteBytesReadOnly("subscribe");
        public static final NoteBytesReadOnly UNSUBSCRIBE = new NoteBytesReadOnly("unsubscribe");
        public static final NoteBytesReadOnly GET_ALL = new NoteBytesReadOnly("get_all");
        
        // Helper methods to build command messages
        public static NoteBytesMap get(String path) {
            NoteBytesMap msg = new NoteBytesMap();
            msg.put(Keys.CMD, GET);
            msg.put(Keys.PATH, new NoteBytes(path));
            return msg;
        }
        
        public static NoteBytesMap set(String path, NoteBytes value) {
            NoteBytesMap msg = new NoteBytesMap();
            msg.put(Keys.CMD, SET);
            msg.put(Keys.PATH, new NoteBytes(path));
            msg.put(Keys.DATA, value);
            return msg;
        }
        
        public static NoteBytesMap merge(NoteBytesMap updates) {
            NoteBytesMap msg = new NoteBytesMap();
            msg.put(Keys.CMD, MERGE);
            msg.put(Keys.DATA, updates);
            return msg;
        }
        
        public static NoteBytesMap getAll() {
            NoteBytesMap msg = new NoteBytesMap();
            msg.put(Keys.CMD, GET_ALL);
            return msg;
        }
        
        public static NoteBytesMap subscribe() {
            NoteBytesMap msg = new NoteBytesMap();
            msg.put(Keys.CMD, SUBSCRIBE);
            return msg;
        }
    }
    
    // ===== STATES =====
    public static final long INITIALIZING = 1L << 0;
    public static final long LOADING = 1L << 1;
    public static final long READY = 1L << 2;
    public static final long SAVING = 1L << 3;
    public static final long ERROR = 1L << 4;
    
    // ===== CONSTRUCTOR =====
    
    private BootstrapConfig() {
        super(NAME, ProcessType.BIDIRECTIONAL);
        this.state = new BitFlagStateMachine("bootstrap-config");
        
        setupMessageHandlers();
        setupStateTransitions();
    }
    
    /**
     * Get singleton instance (must be initialized first)
     */
    public static BootstrapConfig getInstance() {
        if (instance == null) {
            throw new IllegalStateException("BootstrapConfig not initialized - call initialize() first");
        }
        return instance;
    }
    
    /**
     * Check if initialized
     */
    public static boolean isInitialized() {
        return instance != null;
    }
    
    /**
     * Initialize singleton instance
     * Called by SystemProcess during startup
     */
    public static CompletableFuture<BootstrapConfig> initialize() {
        synchronized (INSTANCE_LOCK) {
            if (instance != null) {
                Log.logMsg("[BootstrapConfig] Already initialized");
                return CompletableFuture.completedFuture(instance);
            }
            
            instance = new BootstrapConfig();
            
            return instance.loadConfig()
                .thenApply(v -> {
                    Log.logMsg("[BootstrapConfig] Singleton initialized at: " + 
                        BOOTSTRAP_CONFIG_PATH);
                    return instance;
                })
                .exceptionally(ex -> {
                    Log.logError("[BootstrapConfig] Initialization failed: " + ex.getMessage());
                    instance = null;
                    throw new RuntimeException("Failed to initialize BootstrapConfig", ex);
                });
        }
    }
    
    /**
     * Shutdown singleton
     */
    public static CompletableFuture<Void> shutdown() {
        synchronized (INSTANCE_LOCK) {
            if (instance == null) {
                return CompletableFuture.completedFuture(null);
            }
            
            BootstrapConfig toShutdown = instance;
            instance = null;
            
            return toShutdown.saveConfig()
                .thenRun(() -> {
                    toShutdown.kill();
                    Log.logMsg("[BootstrapConfig] Singleton shutdown");
                });
        }
    }
    
    // ===== LIFECYCLE =====
    
    private void setupMessageHandlers() {
        msgHandlers.put(CMD.GET, this::handleGet);
        msgHandlers.put(CMD.SET, this::handleSet);
        msgHandlers.put(CMD.MERGE, this::handleMerge);
        msgHandlers.put(CMD.VALIDATE, this::handleValidate);
        msgHandlers.put(CMD.SUBSCRIBE, this::handleSubscribe);
        msgHandlers.put(CMD.UNSUBSCRIBE, this::handleUnsubscribe);
        msgHandlers.put(CMD.GET_ALL, this::handleGetAll);
    }
    
    private void setupStateTransitions() {
        state.onStateAdded(INITIALIZING, (old, now, bit) -> {
            Log.logMsg("[BootstrapConfig] Initializing...");
        });
        
        state.onStateAdded(LOADING, (old, now, bit) -> {
            Log.logMsg("[BootstrapConfig] Loading from disk...");
        });
        
        state.onStateAdded(READY, (old, now, bit) -> {
            Log.logMsg("[BootstrapConfig] Ready - accepting requests");
        });
        
        state.onStateAdded(SAVING, (old, now, bit) -> {
            Log.logMsg("[BootstrapConfig] Saving to disk...");
        });
        
        state.onStateAdded(ERROR, (old, now, bit) -> {
            Log.logError("[BootstrapConfig] ERROR state");
        });
    }
    
    @Override
    public CompletableFuture<Void> run() {
        state.addState(INITIALIZING);
        state.addState(READY);
        state.removeState(INITIALIZING);
        
        return getCompletionFuture();
    }
    
    /**
     * Load config from disk or create default
     */
    private CompletableFuture<Void> loadConfig() {
        state.addState(LOADING);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                boolean exists = SettingsData.isBootstrapData();
                
                if (exists) {
                    Log.logMsg("[BootstrapConfig] Loading existing config");
                    return SettingsData.loadBootStrapConfig().join();
                } else {
                    Log.logMsg("[BootstrapConfig] Creating default config");
                    return createDefault();
                }
                
            } catch (Exception e) {
                Log.logError("[BootstrapConfig] Load error: " + e.getMessage());
                return createDefault();
            }
        }, VirtualExecutors.getVirtualExecutor())
        .thenAccept(config -> {
            dataLock.writeLock().lock();
            try {
                this.data = config;
            } finally {
                dataLock.writeLock().unlock();
            }
            
            state.removeState(LOADING);
            Log.logMsg("[BootstrapConfig] Config loaded");
        });
    }
    
    /**
     * Save config to disk
     */
    private CompletableFuture<Void> saveConfig() {
        if (!state.hasState(READY)) {
            return CompletableFuture.completedFuture(null);
        }
        
        state.addState(SAVING);
        
        NoteBytesMap toSave;
        dataLock.readLock().lock();
        try {
            toSave = data;
        } finally {
            dataLock.readLock().unlock();
        }
        
        return SettingsData.saveBootstrapConfig(toSave)
            .thenRun(() -> {
                state.removeState(SAVING);
                Log.logMsg("[BootstrapConfig] Config saved to disk");
            })
            .exceptionally(ex -> {
                state.removeState(SAVING);
                Log.logError("[BootstrapConfig] Save failed: " + ex.getMessage());
                return null;
            });
    }
    
    // ===== MESSAGE HANDLERS =====
    
    @Override
    public CompletableFuture<Void> handleMessage(RoutedPacket packet) {
        if (!state.hasState(READY)) {
            return replyError(packet, "Not ready");
        }
        
        try {
            NoteBytesMap msg = packet.getPayload().getAsNoteBytesMap();
            NoteBytes cmdBytes = msg.get(Keys.CMD);
            
            if (cmdBytes == null) {
                return replyError(packet, "'cmd' required");
            }
            
            RoutedMessageExecutor handler = msgHandlers.get(cmdBytes);
            if (handler != null) {
                return handler.execute(msg, packet);
            } else {
                return replyError(packet, "Unknown command: " + cmdBytes);
            }
            
        } catch (Exception e) {
            return replyError(packet, "Error: " + e.getMessage());
        }
    }
    
    private CompletableFuture<Void> handleGet(NoteBytesMap msg, RoutedPacket packet) {
        String path = msg.get(Keys.PATH).getAsString();
        
        NoteBytes value;
        dataLock.readLock().lock();
        try {
            value = get(data, path);
        } finally {
            dataLock.readLock().unlock();
        }
        
        NoteBytesMap response = new NoteBytesMap();
        response.put(Keys.STATUS, ProtocolMesssages.SUCCESS);
        
        if (value != null) {
            response.put(Keys.DATA, value);
        } else {
            response.put(Keys.STATUS, ProtocolMesssages.ERROR);
            response.put(Keys.ERROR_MESSAGE, new NoteBytes("Path not found: " + path));
        }
        
        reply(packet, response.toNoteBytes());
        return CompletableFuture.completedFuture(null);
    }
    
    private CompletableFuture<Void> handleSet(NoteBytesMap msg, RoutedPacket packet) {
        String path = msg.get(Keys.PATH).getAsString();
        NoteBytes value = msg.get(Keys.DATA);
        
        if (value == null) {
            return replyError(packet, "No value provided");
        }
        
        dataLock.writeLock().lock();
        try {
            set(data, path, value);
        } finally {
            dataLock.writeLock().unlock();
        }
        
        // Auto-save and notify
        return saveConfig()
            .thenRun(() -> {
                notifySubscribers(path, value);
                replySuccess(packet);
            });
    }
    
    private CompletableFuture<Void> handleMerge(NoteBytesMap msg, RoutedPacket packet) {
        NoteBytes updatesBytes = msg.get(Keys.DATA);
        
        if (updatesBytes == null) {
            return replyError(packet, "No updates provided");
        }
        
        NoteBytesMap updates = updatesBytes.getAsNoteBytesMap();
        
        dataLock.writeLock().lock();
        try {
            merge(data, updates);
        } finally {
            dataLock.writeLock().unlock();
        }
        
        // Auto-save and notify
        return saveConfig()
            .thenRun(() -> {
                emit(updates);
                replySuccess(packet);
            });
    }
    
    private CompletableFuture<Void> handleValidate(NoteBytesMap msg, RoutedPacket packet) {
        boolean valid;
        
        dataLock.readLock().lock();
        try {
            valid = validate(data);
        } finally {
            dataLock.readLock().unlock();
        }
        
        NoteBytesMap response = new NoteBytesMap();
        response.put(Keys.STATUS, ProtocolMesssages.SUCCESS);
        response.put("valid", new NoteBytes(valid));
        
        reply(packet, response.toNoteBytes());
        return CompletableFuture.completedFuture(null);
    }
    
    private CompletableFuture<Void> handleGetAll(NoteBytesMap msg, RoutedPacket packet) {
        NoteBytesMap dataCopy;
        
        dataLock.readLock().lock();
        try {
            dataCopy = data;
        } finally {
            dataLock.readLock().unlock();
        }
        
        NoteBytesMap response = new NoteBytesMap();
        response.put(Keys.STATUS, ProtocolMesssages.SUCCESS);
        response.put(Keys.DATA, dataCopy);
        
        reply(packet, response.toNoteBytes());
        return CompletableFuture.completedFuture(null);
    }
    
    private CompletableFuture<Void> handleSubscribe(NoteBytesMap msg, RoutedPacket packet) {
        ContextPath subscriber = packet.getSourcePath();
        
        ConfigChangeListener listener = new ConfigChangeListener(subscriber);
        subscribers.put(subscriber, listener);
        
        Log.logMsg("[BootstrapConfig] Subscriber added: " + subscriber);
        
        replySuccess(packet);
        return CompletableFuture.completedFuture(null);
    }
    
    private CompletableFuture<Void> handleUnsubscribe(NoteBytesMap msg, RoutedPacket packet) {
        ContextPath subscriber = packet.getSourcePath();
        
        subscribers.remove(subscriber);
        
        Log.logMsg("[BootstrapConfig] Subscriber removed: " + subscriber);
        
        replySuccess(packet);
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public void handleStreamChannel(StreamChannel channel, ContextPath fromPath) {
        Log.logError("[BootstrapConfig] Unexpected stream from: " + fromPath);
    }
    
    // ===== NOTIFICATION =====
    
    private void notifySubscribers(String path, NoteBytes value) {
        NoteBytesMap event = new NoteBytesMap();
        event.put("event", new NoteBytes("config_changed"));
        event.put(Keys.PATH, new NoteBytes(path));
        event.put(Keys.DATA, value);
        
        subscribers.values().forEach(listener -> {
            try {
                emitTo(listener.path, event.toNoteBytes());
            } catch (Exception e) {
                Log.logError("[BootstrapConfig] Failed to notify " + 
                    listener.path + ": " + e.getMessage());
            }
        });
    }
    
    // ===== STATIC HELPERS (for backward compatibility) =====
    
    /**
     * Create default configuration structure
     */
    public static NoteBytesMap createDefault() {
        NoteBytesMap root = new NoteBytesMap();
        NoteBytesMap system = createSystemConfig();
        root.put(SYSTEM, system);
        return root;
    }
    
    private static NoteBytesMap createSystemConfig() {
        NoteBytesMap system = new NoteBytesMap();
        system.put(BASE, createBaseConfig());
        system.put(NETWORK, createNetworkConfig());
        system.put(GUI, createGUIConfig());
        return system;
    }
    
    private static NoteBytesMap createBaseConfig() {
        NoteBytesMap base = new NoteBytesMap();
        base.put(SECURE_INPUT, createSecureInputConfig());
        base.put(COMMAND_SHELL, createCommandShellConfig());
        base.put(INPUT, createInputConfig());
        return base;
    }
    
    private static NoteBytesMap createSecureInputConfig() {
        NoteBytesMap secureInput = new NoteBytesMap();
        secureInput.put(INSTALLED, new NoteBytes(false));
        secureInput.put(SOCKET_PATH, new NoteBytes(DEFAULT_SOCKET_PATH));
        secureInput.put(AUTO_START, new NoteBytes(false));
        return secureInput;
    }
    
    private static NoteBytesMap createCommandShellConfig() {
        NoteBytesMap commandShell = new NoteBytesMap();
        commandShell.put(INPUT_SOURCE, new NoteBytes(DEFAULT_INPUT_SOURCE));
        commandShell.put(HISTORY_SIZE, new NoteBytes(DEFAULT_HISTORY_SIZE));
        commandShell.put(ECHO_ENABLED, new NoteBytes(true));
        return commandShell;
    }
    
    private static NoteBytesMap createInputConfig() {
        NoteBytesMap input = new NoteBytesMap();
        NoteBytesMap sources = new NoteBytesMap();
        
        NoteBytesMap guiNative = new NoteBytesMap();
        guiNative.put(ENABLED, new NoteBytes(true));
        guiNative.put(PRIORITY, new NoteBytes(10));
        sources.put(GUI_NATIVE, guiNative);
        
        NoteBytesMap secureInputSource = new NoteBytesMap();
        secureInputSource.put(ENABLED, new NoteBytes(false));
        secureInputSource.put(PRIORITY, new NoteBytes(5));
        sources.put(SECURE_INPUT, secureInputSource);
        
        input.put(SOURCES, sources);
        return input;
    }
    
    private static NoteBytesMap createNetworkConfig() {
        NoteBytesMap network = new NoteBytesMap();
        network.put(ENABLED, new NoteBytes(false));
        network.put(LISTEN_PORT, new NoteBytes(DEFAULT_NETWORK_PORT));
        return network;
    }
    
    private static NoteBytesMap createGUIConfig() {
        NoteBytesMap gui = new NoteBytesMap();
        NoteBytesMap nativeGui = new NoteBytesMap();
        nativeGui.put(ENABLED, new NoteBytes(true));
        gui.put(NATIVE, nativeGui);
        return gui;
    }
    
    // ===== NAVIGATION METHODS =====
    
    public static NoteBytes get(NoteBytesMap config, String... path) {
        if (config == null || path == null || path.length == 0) {
            return null;
        }
        
        NoteBytesMap current = config;
        
        for (int i = 0; i < path.length - 1; i++) {
            NoteBytes segment = current.get(path[i]);
            
            if (segment == null || segment.getType() != NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
                return null;
            }
            
            current = segment.getAsNoteBytesMap();
        }
        
        return current.get(path[path.length - 1]);
    }
    
    public static NoteBytes get(NoteBytesMap config, String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        
        String[] segments = path.split("/");
        return get(config, segments);
    }
    
    public static void set(NoteBytesMap config, String path, NoteBytes value) {
        if (config == null || path == null || path.isEmpty() || value == null) {
            throw new IllegalArgumentException("Config, path, and value must not be null");
        }
        
        String[] segments = path.split("/");
        set(config, segments, value);
    }
    
    public static void set(NoteBytesMap config, String[] segments, NoteBytes value) {
        if (config == null || segments == null || segments.length == 0 || value == null) {
            throw new IllegalArgumentException("Invalid arguments for set");
        }
        
        NoteBytesMap current = config;
        
        for (int i = 0; i < segments.length - 1; i++) {
            String key = segments[i];
            NoteBytes segment = current.get(key);
            
            if (segment == null || segment.getType() != NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
                NoteBytesMap newMap = new NoteBytesMap();
                current.put(key, newMap);
                current = newMap;
            } else {
                current = segment.getAsNoteBytesMap();
            }
        }
        
        String finalKey = segments[segments.length - 1];
        current.put(finalKey, value);
    }
    
    public static void merge(NoteBytesMap base, NoteBytesMap updates) {
        if (base == null || updates == null) {
            return;
        }
        
        updates.forEach((key, value) -> {
            if (value.getType() == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
                NoteBytes existing = base.get(key);
                if (existing != null && existing.getType() == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
                    merge(existing.getAsNoteBytesMap(), value.getAsNoteBytesMap());
                } else {
                    base.put(key, value);
                }
            } else {
                base.put(key, value);
            }
        });
    }
    
    public static boolean validate(NoteBytesMap config) {
        if (config == null) {
            return false;
        }
        
        try {
            NoteBytesMap system = get(config, SYSTEM) != null ? 
                get(config, SYSTEM).getAsNoteBytesMap() : null;
            
            if (system == null) return false;
            
            NoteBytesMap base = get(config, SYSTEM + "/" + BASE) != null ?
                get(config, SYSTEM + "/" + BASE).getAsNoteBytesMap() : null;
            
            return base != null;
            
        } catch (Exception e) {
            return false;
        }
    }
    
    // ===== TYPED GETTERS =====
    
    public static boolean getBoolean(NoteBytesMap config, String path, boolean defaultValue) {
        NoteBytes value = get(config, path);
        if (value == null || value.getType() != NoteBytesMetaData.BOOLEAN_TYPE) {
            return defaultValue;
        }
        return value.getAsBoolean();
    }
    
    public static int getInt(NoteBytesMap config, String path, int defaultValue) {
        NoteBytes value = get(config, path);
        if (value == null || value.getType() != NoteBytesMetaData.INTEGER_TYPE) {
            return defaultValue;
        }
        return value.getAsInt();
    }
    
    public static String getString(NoteBytesMap config, String path, String defaultValue) {
        NoteBytes value = get(config, path);
        if (value == null || value.getType() != NoteBytesMetaData.STRING_TYPE) {
            return defaultValue;
        }
        return value.getAsString();
    }
    
    // ===== CONVENIENCE METHODS (use singleton) =====
    
    public boolean isSecureInputInstalled() {
        dataLock.readLock().lock();
        try {
            return getBoolean(data, SYSTEM + "/" + BASE + "/" + SECURE_INPUT + "/" + INSTALLED, false);
        } finally {
            dataLock.readLock().unlock();
        }
    }
    
    public String getSecureInputSocketPath() {
        dataLock.readLock().lock();
        try {
            return getString(data, SYSTEM + "/" + BASE + "/" + SECURE_INPUT + "/" + SOCKET_PATH, 
                DEFAULT_SOCKET_PATH);
        } finally {
            dataLock.readLock().unlock();
        }
    }
    
    public String getShellInputSource() {
        dataLock.readLock().lock();
        try {
            return getString(data, SYSTEM + "/" + BASE + "/" + COMMAND_SHELL + "/" + INPUT_SOURCE,
                DEFAULT_INPUT_SOURCE);
        } finally {
            dataLock.readLock().unlock();
        }
    }
    
    public List<String> getInputSources() {
        dataLock.readLock().lock();
        try {
            NoteBytes sourcesBytes = get(data, SYSTEM + "/" + BASE + "/" + INPUT + "/" + SOURCES);
            
            if (sourcesBytes == null) {
                return new ArrayList<>();
            }
            
            NoteBytesMap sources = sourcesBytes.getAsNoteBytesMap();
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
        } finally {
            dataLock.readLock().unlock();
        }
    }
    
    public boolean isNetworkEnabled() {
        dataLock.readLock().lock();
        try {
            return getBoolean(data, SYSTEM + "/" + NETWORK + "/" + ENABLED, false);
        } finally {
            dataLock.readLock().unlock();
        }
    }
    
    public int getNetworkPort() {
        dataLock.readLock().lock();
        try {
            return getInt(data, SYSTEM + "/" + NETWORK + "/" + LISTEN_PORT, DEFAULT_NETWORK_PORT);
        } finally {
            dataLock.readLock().unlock();
        }
    }
    
    /**
     * Get read-only copy of entire config
     */
    public NoteBytesMap getData() {
        dataLock.readLock().lock();
        try {
            return data;
        } finally {
            dataLock.readLock().unlock();
        }
    }
    
    // ===== HELPERS =====
    
    private void replySuccess(RoutedPacket packet) {
        NoteBytesMap response = new NoteBytesMap();
        response.put(Keys.STATUS, ProtocolMesssages.SUCCESS);
        reply(packet, response.toNoteBytes());
    }
    
    private CompletableFuture<Void> replyError(RoutedPacket packet, String message) {
        NoteBytesMap response = new NoteBytesMap();
        response.put(Keys.STATUS, ProtocolMesssages.ERROR);
        response.put(Keys.ERROR_MESSAGE, new NoteBytes(message));
        reply(packet, response.toNoteBytes());
        return CompletableFuture.completedFuture(null);
    }
    
    // ===== GETTERS =====
    
    public BitFlagStateMachine getState() {
        return state;
    }
    
    public boolean isReady() {
        return state.hasState(READY);
    }
    
    // ===== INNER CLASSES =====
    
    private static class ConfigChangeListener {
        final ContextPath path;
        
        ConfigChangeListener(ContextPath path) {
            this.path = path;
        }
    }
}