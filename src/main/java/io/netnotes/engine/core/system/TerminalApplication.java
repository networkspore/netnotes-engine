package io.netnotes.engine.core.system;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import io.netnotes.engine.core.CoreConstants;
import io.netnotes.engine.core.system.control.terminal.TerminalContainerHandle;
import io.netnotes.engine.core.system.control.terminal.TerminalRenderable;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.daemon.ClientSession;
import io.netnotes.engine.io.daemon.ClaimedDevice;
import io.netnotes.engine.io.daemon.IODaemon;
import io.netnotes.engine.io.daemon.IODaemon.SESSION_CMDS;
import io.netnotes.engine.io.daemon.IODaemonProtocol.USBDeviceDescriptor;
import io.netnotes.engine.io.input.Keyboard.KeyCodeBytes;
import io.netnotes.engine.io.input.ephemeralEvents.EphemeralKeyDownEvent;
import io.netnotes.engine.io.input.ephemeralEvents.EphemeralRoutedEvent;
import io.netnotes.engine.io.input.events.EventBytes;
import io.netnotes.engine.io.input.events.EventHandlerRegistry;
import io.netnotes.engine.io.input.events.EventHandlerRegistry.RoutedEventHandler;
import io.netnotes.engine.io.input.events.EventsFactory;
import io.netnotes.engine.io.input.events.KeyDownEvent;
import io.netnotes.engine.io.input.events.RoutedEvent;
import io.netnotes.engine.io.input.events.containers.ContainerMoveEvent;
import io.netnotes.engine.io.input.events.containers.ContainerResizeEvent;
import io.netnotes.engine.io.process.ProcessRegistryInterface;
import io.netnotes.engine.messaging.NoteMessaging.ItemTypes;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.messaging.NoteMessaging.ProtocolMesssages;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.NoteUUID;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.state.BitFlagStateMachine;
import io.netnotes.engine.utils.LoggingHelpers.Log;

/**
 * Base class for terminal-based applications
 * 
 * Provides:
 * - Terminal lifecycle (attach/detach/open/close)
 * - Event handling and routing
 * - Screen management
 * - IODaemon session management (connects to existing daemon)
 * - Device claiming and event routing
 * - Default device registry routing
 * 
 */
public abstract class TerminalApplication<T extends TerminalApplication<T>> {
    
    
    // ===== ATTACHMENT STATES =====
    public static final int DETACHED = 65;
    public static final int ATTACHED_LOCAL = 66;
    public static final int ATTACHED_REMOTE = 67;
    
    // ===== RENDERING STATES =====
    public static final int RENDER_REQUESTED = 68;
    public static final int ACTIVE = 69;
    public static final int SHOWING_SCREEN = 70;
    public static final int OPENING = 72;
    
    // ===== CORE IDENTITY =====
    protected final String name;
    protected final String id;
    protected final BitFlagStateMachine stateMachine;
    protected final ProcessRegistryInterface registry;
    
    // ===== TERMINAL CONNECTION =====
    protected TerminalContainerHandle terminalHandle;
    protected volatile boolean isVisible = false;
    
    // ===== EVENT HANDLING =====
    protected final EventHandlerRegistry eventHandlerRegistry = new EventHandlerRegistry();
    
    // ===== IODaemon SESSION =====
    protected ClientSession ioDaemonSession = null;
    protected final Map<NoteBytesReadOnly, String> defaultEventHandlers = new ConcurrentHashMap<>();
    
    // ===== SCREEN MANAGEMENT =====
    protected TerminalScreen currentScreen;
    protected String lastScreenName;
    private final Map<String, ScreenFactory<T>> screenFactories = new HashMap<>();

    protected volatile int width;
    protected volatile int height;
    
     @FunctionalInterface
    public interface ScreenFactory<A extends TerminalApplication<A>>  {
        TerminalScreen create(String id, A app);
    }
    
    // ===== CONSTRUCTION =====
    
    protected TerminalApplication(
        String name,
        TerminalContainerHandle terminalHandle,
        ProcessRegistryInterface registry
    ) {
        this.name = name;
        this.id = NoteUUID.createSafeUUID128();
        this.stateMachine = new BitFlagStateMachine(name + ":" + id);
        this.registry = registry;
        
        setupEventHandlers();
        
        if (terminalHandle != null) {
            setTerminalHandle(terminalHandle);
            stateMachine.addState(ATTACHED_LOCAL);
        } else {
            stateMachine.addState(DETACHED);
        }
        
        registerScreens();
    }
    
    /**
     * Subclasses override this to register their screens
     */
    protected abstract void registerScreens();
    
    // ===== TERMINAL LIFECYCLE =====
    
    public void setTerminalHandle(TerminalContainerHandle handle) {
        this.terminalHandle = handle;
        if(terminalHandle != null){
            width = terminalHandle.getInitialWidth();
            height = terminalHandle.getInitialHeight();
            setupTerminalEventHandling();
        }
    }
    
    private void setupTerminalEventHandling() {
        if (terminalHandle == null) return;
        terminalHandle.setOnContainerEvent(this::onContainerEvent);
    }
    
    protected void onContainerEvent(NoteBytes eventBytes) {
        try {
            if (eventBytes != null) {
                RoutedEvent event = EventsFactory.from(
                    terminalHandle.getContextPath(), 
                    eventBytes
                );
                eventHandlerRegistry.dispatch(event);
            }
        } catch (Exception ex) {
            Log.logError("[" + name + "] Event processing failed: " + ex.getMessage());
        }
    }
    
    public CompletableFuture<Void> attachTerminal(TerminalContainerHandle handle, boolean isRemote) {
        if (isTerminalAttached()) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Terminal already attached"));
        }
        
        Log.logMsg("[" + name + "] Attaching terminal (remote: " + isRemote + ")");
        
        this.terminalHandle = handle;
        setupTerminalEventHandling();
        
        stateMachine.removeState(DETACHED);
        stateMachine.addState(isRemote ? ATTACHED_REMOTE : ATTACHED_LOCAL);
        
        return handle.waitUntilReady()
            .thenCompose(v -> open())
            .thenRun(() -> Log.logMsg("[" + name + "] Terminal attached"));
    }
    
    public CompletableFuture<Void> detachTerminal() {
        if (!isTerminalAttached()) {
            return CompletableFuture.completedFuture(null);
        }
        
        Log.logMsg("[" + name + "] Detaching terminal");
        
        if (currentScreen != null) {
            lastScreenName = currentScreen.getName();
            currentScreen.onHide();
        }
        
        return onBeforeDetach()
            .thenCompose(v -> disconnectFromIODaemon())
            .thenCompose(v -> terminalHandle != null ? terminalHandle.hide() : 
                CompletableFuture.completedFuture(null))
            .thenRun(() -> {
                isVisible = false;
                stateMachine.removeState(OPENING);
                stateMachine.removeState(SHOWING_SCREEN);
                stateMachine.removeState(ATTACHED_LOCAL);
                stateMachine.removeState(ATTACHED_REMOTE);
                stateMachine.addState(DETACHED);
                
                eventHandlerRegistry.clear();
                defaultEventHandlers.clear();
                terminalHandle = null;
                
                Log.logMsg("[" + name + "] Terminal detached");
            });
    }
    
    /**
     * Hook for subclasses to cleanup before detach
     */
    protected CompletableFuture<Void> onBeforeDetach() {
        return CompletableFuture.completedFuture(null);
    }
    
    public CompletableFuture<Void> open() {
        if (!isTerminalAttached()) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Cannot open - no terminal attached"));
        }
        
        if (isVisible) {
            return CompletableFuture.completedFuture(null);
        }
        
        Log.logMsg("[" + name + "] Opening application");
        
        stateMachine.addState(OPENING);
        
        return terminalHandle.show()
            .thenRun(() -> {
                stateMachine.removeState(OPENING);
                isVisible = true;
                onOpened();
            })
            .exceptionally(ex -> {
                Log.logError("[" + name + "] Open failed: " + ex.getMessage());
                stateMachine.removeState(OPENING);
                return null;
            });
    }
    
    /**
     * Hook for subclasses to handle post-open logic (e.g., show appropriate screen)
     */
    protected abstract void onOpened();
    
    public CompletableFuture<Void> close() {
        if (!isTerminalAttached() || !isVisible) {
            return CompletableFuture.completedFuture(null);
        }
        
        if (currentScreen != null) {
            lastScreenName = currentScreen.getName();
            currentScreen.onHide();
        }
        
        return onBeforeClose()
            .thenCompose(v -> terminalHandle.hide())
            .thenRun(() -> {
                isVisible = false;
                stateMachine.removeState(OPENING);
                stateMachine.removeState(SHOWING_SCREEN);
            })
            .exceptionally(ex -> {
                Log.logError("[" + name + "] Close error: " + ex.getMessage());
                return null;
            });
    }
    
    /**
     * Hook for subclasses to cleanup before close
     */
    protected CompletableFuture<Void> onBeforeClose() {
        return CompletableFuture.completedFuture(null);
    }
    
    // ===== EVENT HANDLING =====
    
    private void setupEventHandlers() {
        eventHandlerRegistry.register(EventBytes.EVENT_CONTAINER_RESIZED, 
            this::handleContainerResized);
        eventHandlerRegistry.register(EventBytes.EVENT_CONTAINER_CLOSED, 
            this::handleContainerClosed);
        eventHandlerRegistry.register(EventBytes.EVENT_CONTAINER_SHOWN, 
            this::handleContainerShown);
        eventHandlerRegistry.register(EventBytes.EVENT_CONTAINER_HIDDEN, 
            this::handleContainerHidden);
        eventHandlerRegistry.register(EventBytes.EVENT_CONTAINER_FOCUS_GAINED, 
            this::handleContainerFocusGained);
        eventHandlerRegistry.register(EventBytes.EVENT_CONTAINER_FOCUS_LOST, 
            this::handleContainerFocusLost);
        eventHandlerRegistry.register(EventBytes.EVENT_CONTAINER_MOVE, 
            this::handleContainerMove);
    }
    
    private void handleContainerResized(RoutedEvent event) {
        if (event instanceof ContainerResizeEvent resizeEvent) {
            onContainerResized(resizeEvent);
        }
    }
    
    private void handleContainerClosed(RoutedEvent event) {
        onContainerClosed();
    }
    
    private void handleContainerShown(RoutedEvent event) {
        onContainerShown();
    }
    
    private void handleContainerHidden(RoutedEvent event) {
        onContainerHidden();
    }
    
    private void handleContainerFocusGained(RoutedEvent event) {
        onContainerFocusGained();
    }
    
    private void handleContainerFocusLost(RoutedEvent event) {
        onContainerFocusLost();
    }
    
    private void handleContainerMove(RoutedEvent event) {
        if (event instanceof ContainerMoveEvent moveEvent) {
            onContainerMove(moveEvent);
        }
    }

    public int getWidth(){ return width; }
    public int getHeight(){ return height; }

    public void setDimensions(int width, int height){
        this.width = width;
        this.height = height;
    }
    
    protected void onContainerResized(ContainerResizeEvent event) {
        setDimensions(width, height);
    }
    
    protected void onContainerClosed() {
        stateMachine.removeState(ACTIVE);
        stateMachine.removeState(SHOWING_SCREEN);
        close();
    }
    
    protected void onContainerShown() {
        stateMachine.addState(ACTIVE);
        isVisible = true;
        invalidate();
        Log.logMsg("[" + name + "] Container shown");
    }
    
    protected void onContainerHidden() {
        stateMachine.removeState(ACTIVE);
        isVisible = false;
        Log.logMsg("[" + name + "] Container hidden");
    }
    
    protected void onContainerFocusGained() {
        stateMachine.addState(ACTIVE);
        Log.logMsg("[" + name + "] Container focused");
    }
    
    protected void onContainerFocusLost() {
        stateMachine.removeState(ACTIVE);
        Log.logMsg("[" + name + "] Container focus lost");
    }
    
    protected void onContainerMove(ContainerMoveEvent moveEvent) {
        // Override if needed
    }
    
    // ===== SCREEN MANAGEMENT =====
    
    public void registerScreen(String id, ScreenFactory<T> factory) {
        screenFactories.put(id, factory);
    }
    
    public CompletableFuture<Void> showScreen(String screenName) {
        ScreenFactory<T> factory = screenFactories.get(screenName);
        if (factory == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Screen not found: " + screenName));
        }
        
        TerminalScreen screen = factory.create(screenName, self());
        return showScreen(screen);
    }

    @SuppressWarnings("unchecked")
	protected T self(){
        return (T) this;
    }
    
    public CompletableFuture<Void> showScreen(TerminalScreen screen) {
        if (screen.isShowing()) {
            return CompletableFuture.completedFuture(null);
        }
        
        if (currentScreen != null && currentScreen != screen) {
            currentScreen.onHide();
        }
        
        currentScreen = screen;
        stateMachine.addState(SHOWING_SCREEN);
        
        return screen.onShow()
            .thenRun(() -> {
                if (isTerminalAttached() && terminalHandle.getRenderable() != screen) {
                    terminalHandle.setRenderable(screen);
                }
            });
    }
    
    public TerminalScreen getCurrentScreen() {
        return currentScreen;
    }
    
    // ===== IODaemon SESSION MANAGEMENT =====
    
    /**
     * Connect to IODaemon and create a client session
     * IODaemon must already be registered in the process registry
     */
    public CompletableFuture<ClientSession> connectToIODaemon() {
        return connectToIODaemon(CoreConstants.IO_DAEMON_PATH);
    }
    
    public CompletableFuture<ClientSession> connectToIODaemon(ContextPath ioDaemonPath) {
        if (ioDaemonSession != null) {
            Log.logMsg("[" + name + "] Already connected to IODaemon");
            return CompletableFuture.completedFuture(ioDaemonSession);
        }
        
        if (!isTerminalAttached()) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("No terminal attached"));
        }
        
        // Look for IODaemon in registry
        if (!registry.exists(ioDaemonPath)) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("IODaemon not found in registry at " + ioDaemonPath + 
                    ". It must be set up before applications can connect."));
        }
        
        IODaemon ioDaemon = (IODaemon) registry.getProcess(ioDaemonPath);
        if (ioDaemon == null || !ioDaemon.isAlive()) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("IODaemon is not running"));
        }
        
        Log.logMsg("[" + name + "] Connecting to IODaemon at: " + ioDaemonPath);
        
        // Generate unique session ID
        String sessionId = terminalHandle.getId() + "-session";
        int pid = (int) ProcessHandle.current().pid();
        
        // Send create_session request to IODaemon
        return terminalHandle.request(ioDaemonPath, Duration.ofSeconds(2),
            new NoteBytesPair(Keys.CMD, SESSION_CMDS.CREATE_SESSION),
            new NoteBytesPair(Keys.SESSION_ID, sessionId),
            new NoteBytesPair(Keys.PID, pid)
        )
        .thenCompose(reply -> {
            NoteBytesReadOnly status = reply.getPayload().getAsNoteBytesMap()
                .getReadOnly(Keys.STATUS);
            
            if (status == null || !status.equals(ProtocolMesssages.SUCCESS)) {
                String errorMsg = reply.getPayload().getAsNoteBytesMap()
                    .get(Keys.MSG).getAsString();
                throw new RuntimeException("Failed to create session: " + errorMsg);
            }
            
            // Get session path
            NoteBytes pathBytes = reply.getPayload().getAsNoteBytesMap().get(Keys.PATH);
            if (pathBytes == null) {
                throw new RuntimeException("No session path in response");
            }
            
            ContextPath sessionPath = ContextPath.fromNoteBytes(pathBytes);
            
            // Get session from registry
            ClientSession session = (ClientSession) registry.getProcess(sessionPath);
            if (session == null) {
                throw new RuntimeException("Session not found in registry: " + sessionPath);
            }
            
            this.ioDaemonSession = session;
            
            Log.logMsg("[" + name + "] Connected to IODaemon session: " + sessionPath);
            
            return CompletableFuture.completedFuture(session);
        });
    }
    
    public CompletableFuture<Void> disconnectFromIODaemon() {
        if (ioDaemonSession == null) {
            return CompletableFuture.completedFuture(null);
        }
        
        Log.logMsg("[" + name + "] Disconnecting from IODaemon session");
        
        ClientSession session = ioDaemonSession;
        this.ioDaemonSession = null;
        
        // Clear default devices
        defaultEventHandlers.clear();
        
        if (!isTerminalAttached()) {
            // No terminal, can't send destroy message
            return CompletableFuture.completedFuture(null);
        }
        
        ContextPath ioDaemonPath = session.getParentPath();
        
        return terminalHandle.request(ioDaemonPath, Duration.ofSeconds(1),
            new NoteBytesPair(Keys.CMD, "destroy_session"),
            new NoteBytesPair(Keys.SESSION_ID, session.sessionId)
        )
        .thenAccept(packet -> {
            NoteBytesReadOnly status = packet.getPayload().getAsNoteBytesMap()
                .getReadOnly(Keys.STATUS);
            if (status != null && status.equals(ProtocolMesssages.SUCCESS)) {
                Log.logMsg("[" + name + "] Disconnected from IODaemon");
            } else {
                String msg = packet.getPayload().getAsNoteBytesMap()
                    .get(Keys.MSG).getAsString();
                Log.logError("[" + name + "] Disconnection failed: " + msg);
            }
        })
        .exceptionally(ex -> {
            Log.logError("[" + name + "] Error during disconnect: " + ex.getMessage());
            return null;
        });
    }
    
    public boolean hasIODaemonSession() {
        return ioDaemonSession != null && ioDaemonSession.isAlive();
    }
    
    public ClientSession getIODaemonSession() {
        if (!hasIODaemonSession()) {
            throw new IllegalStateException("No active IODaemon session");
        }
        return ioDaemonSession;
    }
    
    // ===== DEVICE MANAGEMENT =====
    
    public CompletableFuture<List<USBDeviceDescriptor>> discoverUSBDevices() {
        if (!hasIODaemonSession()) {
            return connectToIODaemon()
                .thenCompose(session -> session.discoverDevices())
                .thenApply(devices -> devices.stream()
                    .map(d -> d.usbDevice())
                    .toList());
        }
        
        return ioDaemonSession.discoverDevices()
            .thenApply(devices -> devices.stream()
                .map(d -> d.usbDevice())
                .toList());
    }
    
    public CompletableFuture<ContextPath> claimDevice(String deviceId, String mode) {
        if (!hasIODaemonSession()) {
            return connectToIODaemon()
                .thenCompose(session -> session.claimDevice(deviceId, mode));
        }
        
        return ioDaemonSession.claimDevice(deviceId, mode);
    }
    
    public CompletableFuture<Void> releaseDevice(String deviceId) {
        if (!hasIODaemonSession()) {
            return CompletableFuture.completedFuture(null);
        }
        
        // Remove from defaults if it was set
        defaultEventHandlers.values().remove(deviceId);
        
        return ioDaemonSession.releaseDevice(deviceId);
    }
    
    public boolean isDeviceClaimed(String deviceId) {
        if (!hasIODaemonSession()) return false;
        
        ClaimedDevice device = ioDaemonSession.getClaimedDevice(deviceId);
        return device != null && device.isActive();
    }
    
    public ClaimedDevice getClaimedDevice(String deviceId) {
        if (!hasIODaemonSession()) return null;
        return ioDaemonSession.getClaimedDevice(deviceId);
    }
    
    public EventHandlerRegistry getClaimedDeviceRegistry(String deviceId){
        ClaimedDevice device = ioDaemonSession.getClaimedDevice(deviceId);
        if(device != null){
            return device.getEventHandlerRegistry();
        }else{
            return null;
        }
    }
    // ===== EVENT REGISTRY ROUTING =====
    
    /**
     * Get event registry for a specific claimed device
     */
    public EventHandlerRegistry getDeviceRegistry(String deviceId) {
        if (!hasIODaemonSession()) {
            throw new IllegalStateException("No IODaemon session");
        }
        
        ClaimedDevice device = ioDaemonSession.getClaimedDevice(deviceId);
        if (device == null) {
            throw new IllegalArgumentException("Device not claimed: " + deviceId);
        }
        
        return device.getEventHandlerRegistry();
    }
    
    /**
     * Get the appropriate event registry for a device type
     * Returns claimed device registry if a default is set, otherwise native registry
     */
    public EventHandlerRegistry getEventRegistryForType(NoteBytesReadOnly deviceType) {
        String defaultDeviceId = defaultEventHandlers.get(deviceType);
        
        if (defaultDeviceId == null) {
            // No default set, use native registry
            return eventHandlerRegistry;
        }
        
        // Get device from session
        if (!hasIODaemonSession()) {
            Log.logError("[" + name + "] Session lost, clearing default " + deviceType);
            defaultEventHandlers.remove(deviceType);
            return eventHandlerRegistry;
        }
        
        ClaimedDevice device = ioDaemonSession.getClaimedDevice(defaultDeviceId);
        if (device != null && device.isActive()) {
            return device.getEventHandlerRegistry();
        } else {
            Log.logError("[" + name + "] Device " + defaultDeviceId + 
                " not available, removing from defaults");
            defaultEventHandlers.remove(deviceType);
            return eventHandlerRegistry;
        }
    }
    
    /**
     * Get keyboard event registry (claimed device if set, otherwise native)
     */
    public EventHandlerRegistry getKeyboardRegistry() {
        return getEventRegistryForType(ItemTypes.KEYBOARD);
    }
    
    /**
     * Get mouse event registry (claimed device if set, otherwise native)
     */
    public EventHandlerRegistry getMouseRegistry() {
        return getEventRegistryForType(ItemTypes.MOUSE);
    }

    // ===== KEY PRESS ====
    private CompletableFuture<RoutedEvent> keyWaitFuture = null;
    private CompletableFuture<Void> anyKeyFuture = null;
    private NoteBytesReadOnly handlerId = null;

    public CompletableFuture<Void> waitForKeyPress() {
        if (anyKeyFuture != null) {
            // Already waiting, return existing future
            return anyKeyFuture;
        }
        Log.logMsg("[TerminalContainerHandle] waitForKeyPress");

        anyKeyFuture = new CompletableFuture<>();
          
        
        // Create temporary handler that captures ANY key press
        Consumer<RoutedEvent> consumer = event -> {
            if (event instanceof KeyDownEvent) {
                handleKeyWaitComplete();
            }else if( event instanceof EphemeralKeyDownEvent){
                handleKeyWaitComplete();
            }
        };
        
        // Register temporary handler
        handlerId = addKeyDownHandler(consumer);
        
        return anyKeyFuture;
    }
 
    public CompletableFuture<Void> waitForKeyPress(Runnable action) {
        return waitForKeyPress()
            .thenRun(action);
    }



    public CompletableFuture<RoutedEvent> waitForKey(NoteBytes keyCodeBytes) {
        if (keyWaitFuture != null) {
            // Already waiting, return existing future
            return keyWaitFuture;
        }
        
        keyWaitFuture = new CompletableFuture<>();
        
   
        
        // Create temporary handler that captures key presses
        Consumer<RoutedEvent> consumer = event -> {
            if (event instanceof KeyDownEvent keyDown) {
                if(keyDown.getKeyCodeBytes().equals(keyCodeBytes)){
                    handleKeyWaitComplete(event);
                }
            }else if(event instanceof EphemeralKeyDownEvent keyDown){
                if(keyDown.getKeyCodeBytes().equals(keyCodeBytes)){
                    handleKeyWaitComplete(event);
                }
            }
        };
        
        // Register temporary handler
        handlerId = addKeyDownHandler(consumer);
        
        return keyWaitFuture;
    }
    
  
    public CompletableFuture<Void> waitForEnter() {
        return waitForKey(KeyCodeBytes.ENTER)
            .thenAccept(k -> {
                if(k instanceof EphemeralRoutedEvent ephemeralRoutedEvent){
                    ephemeralRoutedEvent.close();
                }
            }); // Convert to Void
    }
    
   
    public CompletableFuture<Void> waitForEscape() {
        return waitForKey(KeyCodeBytes.ESCAPE)
            .thenAccept(k -> {
                 if(k instanceof EphemeralRoutedEvent ephemeralRoutedEvent){
                    ephemeralRoutedEvent.close();
                }
            });
    }
    
  
    private void handleKeyWaitComplete(RoutedEvent event) {
        if (keyWaitFuture == null) {
            return;
        }
        
        // Remove temporary handler
        if (handlerId != null) {
            removeKeyDownHandler(handlerId);
            handlerId = null;
        }
        
        // Complete the future
        if (keyWaitFuture != null && !keyWaitFuture.isDone()) {
            keyWaitFuture.complete(event);
        }
        
        keyWaitFuture = null;
    }

    private void handleKeyWaitComplete() {
        if (anyKeyFuture == null) {
            return;
        }
         Log.logMsg("[TerminalContainerHandle] waitComplete");
        // Remove temporary handler
        if (handlerId != null) {
            removeKeyDownHandler(handlerId);
            handlerId = null;
        }
        
        // Complete the future
        if (anyKeyFuture != null && !anyKeyFuture.isDone()) {
            anyKeyFuture.complete(null);
        }
        
        anyKeyFuture = null;
    }
    

    public void cancelKeyWait() {
        if (anyKeyFuture == null) {
            return;
        }
        
        // Remove temporary handler
        if (handlerId != null) {
            removeKeyDownHandler(handlerId);
            handlerId = null;
        }
        
        // Cancel the future
        if (keyWaitFuture != null && !keyWaitFuture.isDone()) {
            keyWaitFuture.cancel(false);
        }
        
        keyWaitFuture = null;
    }


    public boolean isWaitingForKeyPress() {
        return keyWaitFuture != null;
    }


     public EventHandlerRegistry getDefaultEventRegistry(NoteBytesReadOnly itemType) {
        String defaultDeviceId = defaultEventHandlers.get(itemType);
        
        if (defaultDeviceId == null) {
            // No default set, use native registry
            return eventHandlerRegistry;
        }
        
        // Get device from session
        if (ioDaemonSession == null) {
            Log.logError("[ContainerHandle] Session lost, clearing default " + itemType);
            defaultEventHandlers.remove(itemType);
            return eventHandlerRegistry;
        }
        
        ClaimedDevice device = ioDaemonSession.getClaimedDevice(defaultDeviceId);
        if (device != null) {
            return device.getEventHandlerRegistry();
        } else {
            Log.logError("[ContainerHandle] Device " + defaultDeviceId + " not available, removing from defaults");
            defaultEventHandlers.remove(itemType);
            return eventHandlerRegistry;
        }
    }

    public EventHandlerRegistry getDefaultKeyboardEventRegistry() {
        return getDefaultEventRegistry(ItemTypes.KEYBOARD);
    }


    public  NoteBytesReadOnly addKeyCharHandler( Consumer<RoutedEvent> handler){
       
        return  getDefaultKeyboardEventRegistry().register(EventBytes.EVENT_KEY_CHAR, handler);
    }

    public  List<RoutedEventHandler> removeKeyCharHandler(Consumer<RoutedEvent> handler){
       return  getDefaultKeyboardEventRegistry().unregister(EventBytes.EVENT_KEY_CHAR, handler);
    }

    public  List<RoutedEventHandler> removeKeyCharHandler(NoteBytesReadOnly handlerId){
       return  getDefaultKeyboardEventRegistry().unregister(EventBytes.EVENT_KEY_CHAR, handlerId);
    }

    public  NoteBytesReadOnly addKeyDownHandler( Consumer<RoutedEvent> handler){
        return  getDefaultKeyboardEventRegistry().register(EventBytes.EVENT_KEY_DOWN, handler);
    }

    public  NoteBytesReadOnly addKeyDownHandler( RoutedEventHandler handler){
        return getDefaultKeyboardEventRegistry().register(EventBytes.EVENT_KEY_DOWN, handler);
    }
    
    public  List<RoutedEventHandler> removeKeyDownHandler(Consumer<RoutedEvent> consumer){
        return getDefaultKeyboardEventRegistry().unregister(EventBytes.EVENT_KEY_DOWN, consumer);
    }

    public  List<RoutedEventHandler> removeKeyDownHandler(NoteBytesReadOnly id){
        return getDefaultKeyboardEventRegistry().unregister(EventBytes.EVENT_KEY_DOWN, id);
    }

    public  NoteBytesReadOnly  addKeyUpHandler( Consumer<RoutedEvent> handler){
        return getEventHandlerRegistry().register(EventBytes.EVENT_KEY_UP, handler);
    }

    public List<RoutedEventHandler> removeKeyUpHandler(Consumer<RoutedEvent> handler){
        return getEventHandlerRegistry().unregister(EventBytes.EVENT_KEY_UP, handler);
    }

    public List<RoutedEventHandler> removeKeyUpHandler(NoteBytesReadOnly handlerId){
        return getEventHandlerRegistry().unregister(EventBytes.EVENT_KEY_UP, handlerId);
    }

    public  NoteBytesReadOnly addResizeHandler( Consumer<RoutedEvent> handler){
        return getEventHandlerRegistry().register(EventBytes.EVENT_CONTAINER_RESIZED, handler);
    }

    public  List<RoutedEventHandler> removeResizeHandler(Consumer<RoutedEvent> handler){
        return getEventHandlerRegistry().unregister(EventBytes.EVENT_CONTAINER_RESIZED, handler);
    }
    
    public  List<RoutedEventHandler> removeResizeHandler(NoteBytesReadOnly handlerId){
        return getEventHandlerRegistry().unregister(EventBytes.EVENT_CONTAINER_RESIZED, handlerId);
    }
    
    // ===== DEFAULT DEVICE MANAGEMENT =====
    
    public void setDefaultDevice(NoteBytesReadOnly deviceType, String deviceId) {
        if (!hasIODaemonSession()) {
            throw new IllegalStateException("No IODaemon session");
        }
        
        ClaimedDevice device = ioDaemonSession.getClaimedDevice(deviceId);
        if (device == null) {
            throw new IllegalArgumentException("Device not claimed: " + deviceId);
        }
        
        defaultEventHandlers.put(deviceType, deviceId);
        Log.logMsg("[" + name + "] Set default " + deviceType + " to: " + deviceId);
    }
    
    public void clearDefaultDevice(NoteBytesReadOnly deviceType) {
        String removed = defaultEventHandlers.remove(deviceType);
        if (removed != null) {
            Log.logMsg("[" + name + "] Cleared default " + deviceType + ": " + removed);
        }
    }
    
    public String getDefaultDevice(NoteBytesReadOnly deviceType) {
        return defaultEventHandlers.get(deviceType);
    }
    
    public void setDefaultKeyboard(String deviceId) {
        setDefaultDevice(ItemTypes.KEYBOARD, deviceId);
    }
    
    public void clearDefaultKeyboard() {
        clearDefaultDevice(ItemTypes.KEYBOARD);
    }
    
    public String getDefaultKeyboardId() {
        return getDefaultDevice(ItemTypes.KEYBOARD);
    }
    
    public void setDefaultMouse(String deviceId) {
        setDefaultDevice(ItemTypes.MOUSE, deviceId);
    }
    
    public void clearDefaultMouse() {
        clearDefaultDevice(ItemTypes.MOUSE);
    }
    
    public String getDefaultMouseId() {
        return getDefaultDevice(ItemTypes.MOUSE);
    }
    
    // ===== RENDERING =====
    
    public void invalidate() {
        if (isTerminalAttached()) {
            stateMachine.addState(RENDER_REQUESTED);
            terminalHandle.invalidate();
        }
    }
    
    public void setRenderable(TerminalRenderable renderable) {
        if (isTerminalAttached()) {
            terminalHandle.setRenderable(renderable);
        }
    }

    public void clearRenderable(){
        if(isTerminalAttached()){
            terminalHandle.clearRenderable();
        }
    }
    
    // ===== QUERIES =====
    
    public boolean isTerminalAttached() {
        return terminalHandle != null && !stateMachine.hasState(DETACHED);
    }
    
    public boolean isLocalAttachment() {
        return stateMachine.hasState(ATTACHED_LOCAL);
    }
    
    public boolean isRemoteAttachment() {
        return stateMachine.hasState(ATTACHED_REMOTE);
    }
    
    public boolean isVisible() {
        return isVisible;
    }
    
    public TerminalContainerHandle getTerminal() {
        if (terminalHandle == null) {
            throw new IllegalStateException("No terminal attached");
        }
        return terminalHandle;
    }
    
    public BitFlagStateMachine getStateMachine() {
        return stateMachine;
    }
    
    public ProcessRegistryInterface getRegistry() {
        return registry;
    }
    
    public EventHandlerRegistry getEventHandlerRegistry() {
        return eventHandlerRegistry;
    }
    
    public String getName() {
        return name;
    }
    
    public String getId() {
        return id;
    }
}