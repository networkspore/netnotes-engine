package io.netnotes.engine.ui.containers;

import java.io.IOException;
import java.io.PipedInputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

import io.netnotes.engine.ui.layout.LayoutCallback;
import io.netnotes.engine.ui.layout.LayoutContext;
import io.netnotes.engine.ui.layout.LayoutData;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.daemon.ClientSession;
import io.netnotes.engine.io.daemon.IODaemon.SESSION_CMDS;
import io.netnotes.engine.io.input.Keyboard.KeyCodeBytes;
import io.netnotes.engine.io.input.events.EventBytes;
import io.netnotes.engine.io.input.events.EventFilter;
import io.netnotes.engine.io.input.events.EventFilterList;
import io.netnotes.engine.io.input.events.EventFilterList.FilterMode;
import io.netnotes.engine.io.input.events.EventHandlerRegistry;
import io.netnotes.engine.io.input.events.RoutedEvent;
import io.netnotes.engine.io.input.events.containerEvents.RoutedContainerEvent;
import io.netnotes.engine.io.input.ephemeralEvents.EphemeralKeyDownEvent;
import io.netnotes.engine.io.input.events.keyboardEvents.KeyDownEvent;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.io.process.StreamChannel;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.messaging.NoteMessaging.ProtocolMesssages;
import io.netnotes.engine.messaging.NoteMessaging.ProtocolObjects;
import io.netnotes.engine.messaging.NoteMessaging.RoutedMessageExecutor;
import io.netnotes.noteBytes.collections.NoteBytesMap;
import io.netnotes.noteBytes.collections.NoteBytesPair;
import io.netnotes.noteBytes.processing.NoteBytesMetaData;
import io.netnotes.noteBytes.processing.NoteBytesReader;
import io.netnotes.noteBytes.processing.NoteBytesWriter;
import io.netnotes.engine.state.BitFlagStateMachine;
import io.netnotes.engine.state.ConcurrentBitFlagStateMachine;
import io.netnotes.engine.ui.BatchBuilder;
import io.netnotes.engine.ui.FloatingLayerManager;
import io.netnotes.engine.ui.Renderable;
import io.netnotes.engine.ui.RenderableLayoutManager;
import io.netnotes.engine.ui.SpatialPoint;
import io.netnotes.engine.ui.SpatialRegion;
import io.netnotes.engine.utils.LoggingHelpers.Log;
import io.netnotes.engine.utils.noteBytes.NoteUUID;
import io.netnotes.engine.utils.streams.StreamUtils;
import io.netnotes.engine.utils.virtualExecutors.SerializedVirtualExecutor;
import io.netnotes.engine.utils.virtualExecutors.VirtualExecutors;
import io.netnotes.noteBytes.NoteBoolean;
import io.netnotes.noteBytes.NoteBytes;
import io.netnotes.noteBytes.NoteBytesObject;
import io.netnotes.noteBytes.NoteBytesReadOnly;

/**
 * ContainerHandle - Client-side container handle with event filtering
 * 
 * EVENT FILTERING:
 * - Filter events before they reach the Renderable chain
 * - System-level security: only accept events from specific sources
 * - Performance: drop unwanted events early
 * - Flexibility: combine multiple filters with AND/OR logic
 * 
 * FILTERING LEVELS:
 * 1. ContainerHandle level (this class) - system-wide filtering
 * 2. EventHandlerRegistry level - per-handler filtering
 * 3. Handler level - manual filtering in handler code
 * 
 * USAGE:
 * <pre>
 * // Accept only password keyboard events
 * EventFilter filter = EventFilter.forSource(passwordKeyboardPath);
 * handle.setEventFilter(filter);
 * 
 * // Accept keyboard events from any source EXCEPT password keyboard
 * handle.setEventFilter(event -> 
 *     !event.getSourcePath().equals(passwordKeyboardPath));
 * 
 * // Combine filters
 * handle.setEventFilter(EventFilter.forSource(keyboardPath)
 *     .and(EventFilter.forType(EventBytes.EVENT_KEY_DOWN)));
 * </pre>
 */
public abstract class ContainerHandle<
    B extends BatchBuilder<S>,
    H extends ContainerHandle<B,H,P,R,S,DM,RM,FLM,LC,LD,LCB,BLD>,
    P extends SpatialPoint<P>,
    R extends Renderable<B,P,S,LC,LD,LCB,?,?,?,?,R>,
    S extends SpatialRegion<P,S>,
    DM extends DeviceManager<H,DM>,
    RM extends RenderableLayoutManager<B,R,P,S,LC,LD,LCB,?,?,?,?,?>,
    FLM extends FloatingLayerManager<B,R,P,S,LC,LD,LCB>,
    LC extends LayoutContext<B,R,P,S,LD,LCB,LC,?>,
    LD extends LayoutData<B,R,S,LD,?>,
    LCB extends LayoutCallback<B,R,P,S,LC,LD,LCB>,
    BLD extends ContainerHandle.Builder<H,BLD>
> extends FlowProcess {
    
    // ===== CREATION PARAMETERS =====
    protected final ContainerId containerId;
    protected final ContainerConfig containerConfig;
    protected final ContextPath renderingServicePath;
    protected final String title;
    protected final BLD builder;
    protected final ContextPath ioDaemonPath;
    protected final RM renderableLayoutManager;
    protected final FLM floatingLayerManager;


    protected Throwable streamError = null;

    private ClientSession ioSession = null;
    private final Map<String, DM> deviceManagers = new HashMap<>();
    
    // ===== RUNTIME STATE =====


    protected R rootRenderable = null;
    protected final NoteBytesReadOnly rendererId;

    // Stream TO Container (for render commands)
    protected StreamChannel renderStream;
    protected NoteBytesWriter renderWriter;
    private boolean renderReadySnapshot = false;

    // Stream FROM Container (for events)
    protected StreamChannel eventChannel = null;
    protected CompletableFuture<Void> eventStreamReadyFuture = new CompletableFuture<>();
    protected Consumer<RoutedEvent> onContainerEvent = null;

    // Message handling
    protected final Map<NoteBytesReadOnly, RoutedMessageExecutor> m_msgMap = new ConcurrentHashMap<>();
    protected final EventHandlerRegistry eventHandlerRegistry = new EventHandlerRegistry();
   
    protected NoteBytesReadOnly traversalKey = KeyCodeBytes.TAB;
    protected FocusTraversalStrategy strategy = FocusTraversalStrategy.TAB_INDEX_THEN_TREE;
    protected R focused = null;
    protected boolean manageFocus = true;

    // Event filtering
    private EventFilterList filterList = new EventFilterList();

    public static class ContainerPredicate implements Predicate<RoutedEvent>{
        @Override
        public boolean test(RoutedEvent event) {
            return !(event instanceof RoutedContainerEvent );
        }
    }

    private Predicate<RoutedEvent> containerPredicate = new ContainerPredicate();

    protected final ConcurrentBitFlagStateMachine stateMachine;

    protected SerializedVirtualExecutor uiExecutor = VirtualExecutors.getUiExecutor();

    // Dimensions
    protected S allocatedRegion = null;

    protected boolean dimensionsInitialized = false;

    
    @FunctionalInterface
    public interface EventDispatcher {
        void dispatchEvent(RoutedEvent event);
    }


    protected Consumer<H> notifyOnStreamReady = null;
    protected BiConsumer<H,Throwable> notifyOnStreamError = null;
    protected Consumer<H> notifyOnVisible = null;
    protected Consumer<H> notifyOnHidden = null;
    protected Consumer<H> notifyOnFocused = null;
    protected Consumer<H> notifyOnFocusLost = null;
    protected Consumer<H> notifyOnResize = null;
    protected Consumer<H> notifyOnMove = null;
    protected Consumer<R> onRenderableRegistered = null;
    protected Consumer<R> onRenderableUnregistered = null;
    
    protected BiConsumer<R,R> onRenderableChanged = null;
    protected Consumer<H> notifyOnClosed = null;
    protected BiConsumer<H,Boolean> notifyOnRenderingChanged = null;
    protected Consumer<H> layoutCallback = null;
    protected Consumer<H> notifyOnInitialized = null; 
    protected volatile boolean applyingLayout = false;
    /**
     * Private constructor - use Builder
     */
    protected ContainerHandle(BLD builder) {
        super(builder.name, ProcessType.BIDIRECTIONAL);
        this.containerId = ContainerId.generate();
        this.rendererId = builder.rendererId;
        this.title = builder.title != null ? builder.title : builder.name;
        this.containerConfig = builder.containerConfig;
        this.renderingServicePath = builder.renderingServicePath;
        this.ioDaemonPath = builder.ioDaemonPath;
        this.builder = builder;
        
        this.stateMachine = new ConcurrentBitFlagStateMachine("ContainerHandle:" + containerId);
        //runs state changes on UI executor, deffers if during layout
        this.stateMachine.setSerialExecutor(uiExecutor);
 
        //this.ioDaemonPath = builder.ioDaemonPath == null ? CoreConstants.IO_DAEMON_PATH : builder.ioDaemonPath;
        this.filterList = builder.filterList;
        this.floatingLayerManager = createFloatingLayerManager();
        this.renderableLayoutManager = createRenderableLayoutManager(floatingLayerManager);
        this.renderableLayoutManager.setFocusRequester(this::requestFocusInternal);
        setupRoutedMessageMap();
        setupEventHandlers();
        setupBaseStateHandler();
        setupStateTransitions();
    }

    public enum FocusTraversalStrategy {
        TAB_INDEX_THEN_TREE,
        TAB_INDEX_THEN_SCREEN
    }

    protected abstract FLM createFloatingLayerManager();

    protected abstract RM createRenderableLayoutManager(FLM manager);


    public void addFloating(R element, LCB callback, R anchor) {
        element.makeFloating(anchor);
        renderableLayoutManager.registerFloating(element, callback, anchor);
    }

    public void removeFloating(R element) {
        renderableLayoutManager.unregisterFloating(element);
        element.makeStatic();
    }

    public FloatingLayerManager<B,R,P,S,LC,LD,LCB> getFloatingLayerManager() {
        return floatingLayerManager;
    }

    /**
     * Setup base state transitions
     */
    private void setupBaseStateHandler() {

        stateMachine.onStateAdded(Container.STATE_INITIALIZED, (old, now, bit) -> {
            updatedInitialzation();
            handleInitialized();
        });

        stateMachine.onStateAdded(Container.STATE_STREAM_READY,  (old, now, bit) -> {
            Log.logMsg("[ContainerHandle:" + containerId + "] Stream now READY");
            stateMachine.removeState(Container.STATE_STREAM_ERROR);
            updateIsRendering();
            handleOnStreamReady();
        
        });
        stateMachine.onStateRemoved(Container.STATE_STREAM_READY,  (old, now, bit) -> {
            updateIsRendering();
        });

        stateMachine.onStateAdded(Container.STATE_STREAM_ERROR,  (old, now, bit) -> {
            Log.logError("[ContainerHandle:" + containerId + "]", "Stream ERROR", streamError);
            stateMachine.removeState(Container.STATE_STREAM_READY);
            updateIsRendering();
            handleOnStreamError();
        });

        stateMachine.onStateAdded(Container.STATE_VISIBLE, (old, now, bit) -> {
            Log.logMsg("[ContainerHandle:" + containerId + "] Now VISIBLE");
            stateMachine.removeState(Container.STATE_HIDDEN);
            updateIsRendering();
            handleOnVisible();
        });
        
        stateMachine.onStateRemoved(Container.STATE_VISIBLE, (old, now, bit) -> {
            Log.logMsg("[ContainerHandle:" + containerId + "] Now HIDDEN");
            stateMachine.removeState(Container.STATE_FOCUSED);
            stateMachine.addState(Container.STATE_HIDDEN);
            updateIsRendering();
            handleOnHidden();
        });
        
        stateMachine.onStateAdded(Container.STATE_FOCUSED, (old, now, bit) -> {
            Log.logMsg("[ContainerHandle:" + containerId + "] Now FOCUSED");
            handleOnFocused();
        });
        
        stateMachine.onStateRemoved(Container.STATE_FOCUSED, (old, now, bit) -> {
            Log.logMsg("[ContainerHandle:" + containerId + "] Focus lost");
            handleOnFocusLost();
        });
        
        stateMachine.onStateAdded(Container.STATE_DESTROYED, (old, now, bit) -> {
            Log.logMsg("[ContainerHandle:" + containerId + "] DESTROYED");
            if (stateMachine.hasState(Container.STATE_DESTROYING)) {
                stateMachine.removeState(Container.STATE_DESTROYING);
            }

            if(closingFuture == null){
                closingFuture = CompletableFuture.completedFuture(null);
            }else if(!closingFuture.isDone()){
                closingFuture.complete(null);
            }
        });
    }

    protected void handleInitialized(){
        if (notifyOnInitialized != null) {
            notifyOnInitialized.accept(self());
        }
    }

    protected void handleOnClosed(){
        if (notifyOnClosed != null) {
            notifyOnClosed.accept(self());
        }
    }

    protected void handleOnStreamError(){
        if (notifyOnStreamError != null) {
            notifyOnStreamError.accept(self(), streamError);
        }
    }

    protected void handleOnStreamReady(){
        if (notifyOnStreamReady != null) {
            notifyOnStreamReady.accept(self());
        }
    }

    protected void handleOnVisible() {
        if (notifyOnVisible != null) {
            notifyOnVisible.accept(self());
        }
    }

    public void setOnVisible(Consumer<H> onVisible) {
        this.notifyOnVisible = onVisible;
    }

    protected void handleOnHidden() {
        if (notifyOnHidden != null) {
            notifyOnHidden.accept(self());
        }
    }

    public void setOnRenderingChanged(BiConsumer<H,Boolean> onRenderingChanged) {
        this.notifyOnRenderingChanged = onRenderingChanged;
    }

    public void setOnHidden(Consumer<H> onHidden) {
        this.notifyOnHidden = onHidden;
    }

    protected void handleOnFocused() {
        if (notifyOnFocused != null) {
            notifyOnFocused.accept(self());
        }
    }

    public void setOnRenderStreamReady(Consumer<H> onStreamReady) {
        this.notifyOnStreamReady = onStreamReady;
    }


    public void setOnRenderStreamError(BiConsumer<H,Throwable> onStreamError) {
        this.notifyOnStreamError = onStreamError;
    }

    public void setOnFocused(Consumer<H> onFocused) {
        this.notifyOnFocused = onFocused;
    }

    protected void handleOnFocusLost() {
        if (notifyOnFocusLost != null) {
            notifyOnFocusLost.accept(self());
        }
    }

    public void setOnFocusLost(Consumer<H> onFocusLost) {
        this.notifyOnFocusLost = onFocusLost;
    }

    public void setOnResize(Consumer<H> listener) {
        this.notifyOnResize = listener;
    }

     public void setOnMove(Consumer<H> onMove) {
        this.notifyOnMove = onMove;
    }

    /**
     * Called by LayoutManager during registration
     * This establishes the handle → layout manager connection
     * 
     * @param callback Callback to trigger layout recalculation
     */
    public void setLayoutCallback(Consumer<H> callback) {
        this.layoutCallback = callback;
    }

   
    /**
     * Request layout recalculation
     * Called by resize handler or when renderable changes
     */
    protected void requestLayout() {
        // Don't request layout if we're currently applying layout
        // (prevents infinite recursion)
        if (applyingLayout) {
            return;
        }
        
        if (layoutCallback != null) {
            layoutCallback.accept(self());
        }
    }

    /**
     * Called by LayoutManager before applying layout
     * Prevents recursive layout requests
     */
    public void beginLayoutApplication() {
        applyingLayout = true;
    }

    /**
     * Called by LayoutManager after applying layout
     * Re-enables layout requests
     */
    public void endLayoutApplication() {
        applyingLayout = false;
    }


  

    public void setRenderableChangeListener(BiConsumer<R,R> listener) {
        this.onRenderableChanged = listener;
    }

    protected void updatedInitialzation(){
        dimensionsInitialized = true;
        if(rootRenderable != null){    
            applyRegionToRenderable(rootRenderable, allocatedRegion);
        }
    }
    
    protected abstract void applyRegionToRenderable(R currentRenderable, S allocatedRegion);

    protected void updateIsRendering() {
        boolean prev = renderReadySnapshot;

        renderReadySnapshot = renderStream != null && 
            stateMachine.hasState(Container.STATE_STREAM_READY) &&
            !stateMachine.hasState(Container.STATE_STREAM_ERROR) &&
            stateMachine.hasState(Container.STATE_VISIBLE) &&
            !stateMachine.hasState(Container.STATE_HIDDEN);

        if(renderReadySnapshot != prev){
            notifyOnRenderingChanged.accept(self(), renderReadySnapshot);
            if(rootRenderable != null && rootRenderable.needsRender()){
                render();
            }
        }
    
    }

    protected abstract void setupStateTransitions();

    public boolean isDimensionsInitialized() {
        return dimensionsInitialized;
    }
    
    public ConcurrentBitFlagStateMachine getStateMachine() {
        return stateMachine;
    }

    public BitFlagStateMachine.StateSnapshot getStateSnapshot() {
        return stateMachine.getSnapshot();
    }

    // ===== EVENT FILTERING =====
    
    
    public CompletableFuture<Boolean> filterListIsEnableOnAdd(){ 
        return uiExecutor.submit(()->{
            return filterList.isEnableOnAdd(); 
        });
   
    }

    public CompletableFuture<Void> filterListSetEnableOnAdd(boolean enableOnAdd) {
        return uiExecutor.submit(()->{
            this.filterList.setEnableOnAdd(enableOnAdd);
            return null;
        });
    }

    public CompletableFuture<Void> filterListSetEnabled(boolean enabled){ 
        return uiExecutor.submit(()->{
            this.filterList.setEnabled(enabled); 
            return null;
        });
    }

    public CompletableFuture<Boolean> filterListRemoveEventFilterById(String id) {
        return uiExecutor.submit(()->{
            return this.filterList.removeEventFilterById(id);
        });
    }

    public CompletableFuture<EventFilter> filterListGetEventFilterById(String id){
        return uiExecutor.submit(()->{
            return this.filterList.getEventFilterById(id);
        });
    }

    public CompletableFuture<Boolean> filterListAddPredicate(Predicate<RoutedEvent> filter) {
        return uiExecutor.submit(()->{
            return this.filterList.addPredicate(filter);
        });
    }

    public CompletableFuture<Boolean> filterListAddPredicateIfNotExists(EventFilter filter) {
        return uiExecutor.submit(()->{
            return this.filterList.addPredicateIfNotExists(filter);
        });
    }

    public CompletableFuture<Boolean> filterListRemovePredicate(Predicate<RoutedEvent>  filter) {
        return uiExecutor.submit(()->{
            return this.filterList.removePredicate(filter);
        });
    }

   
    public CompletableFuture<Boolean> filterListIsEmpty() {
        return uiExecutor.submit(()->{
            return filterList.isEmpty();
        });
    }

    public CompletableFuture<Void> filterListSetMode(FilterMode mode) {
        return uiExecutor.submit(()->{
            filterList.setMode(mode);
            return null;
        });
    }

    public CompletableFuture< FilterMode> filterListGetMode() {
        return uiExecutor.submit(()->{
            return filterList.getMode();
        });
    }

    public CompletableFuture<Void> filterListClear(){
        return uiExecutor.submit(()->{
            filterList.clear();
            return null;
        });
    }

    /**
     * Test if an event would be accepted by current filter
     * 
     * @param event Event to test
     * @return true if event would be accepted, false if it would be dropped
     */
    public boolean testEventFilter(RoutedEvent event) {

        return filterList.test(event);
    }

    // ===== STATE CHECKS =====

    public boolean isContainerVisible() {
        return stateMachine.hasState(Container.STATE_VISIBLE);
    }
    
    public boolean isContainerHidden() {
        return stateMachine.hasState(Container.STATE_HIDDEN);
    }
    
    public boolean isContainerFocused() {
        return stateMachine.hasState(Container.STATE_FOCUSED);
    }
    
    public boolean isDestroyed() {
        return stateMachine.hasState(Container.STATE_DESTROYED);
    }

    public boolean isReadyToRender() {
        return stateMachine.hasState(Container.STATE_STREAM_READY);
    }

    public boolean hasRenderable() {
        return rootRenderable != null;
    }

    // ===== BUILDER =====

    public static abstract class Builder<
        H extends ContainerHandle<?,H,?,?,?,?,?,?,?,?,?,BLD>,
        BLD extends ContainerHandle.Builder<H,BLD>
    > {
        public String name;
        public String title;
        public final NoteBytesReadOnly rendererId;
        public ContainerConfig containerConfig = new ContainerConfig();
        public final ContextPath renderingServicePath;
        public boolean autoFocus = true;
        public EventFilterList filterList = null;
        public ContextPath ioDaemonPath = null;

        protected Builder(String name, ContextPath renderingServicePath, NoteBytesReadOnly rendererId) {
            this.name = name;
            this.title = name;
            this.rendererId = rendererId;
            this.renderingServicePath = renderingServicePath;
        }

        @SuppressWarnings("unchecked")
        protected BLD self() {
            return (BLD) this;
        }

        public BLD ioDaemonPath(ContextPath ioDaemonPath){
            this.ioDaemonPath = ioDaemonPath;
            return self();
        }
        
        public BLD name(String name) {
            this.name = name;
            return self();
        }
        
        public BLD title(String title) {
            this.title = title;
            return self();
        }
        
      
        public BLD config(ContainerConfig config) {
            this.containerConfig = config;
            return self();
        }

        public BLD autoFocus(boolean autoFocus) {
            this.autoFocus = autoFocus;
            return self();
        }
        
        /**
         * Set event filterList for the container
         * 
         * @param filterList list of event filters to enable
         */
        public BLD eventFilterList(EventFilterList filterList) {
            this.filterList = filterList;
            return self();
        }
        
        /**
         * Add an event filter to the list 
         * @param filter filter to add
         */
        public BLD eventFilter(EventFilter filter) {
            if(filterList == null){
                filterList = new EventFilterList();
            }
            filterList.addPredicate(filter);
            return self();
        }
        
        public abstract H build();
        
        protected void validate() {
            if (name == null || name.isEmpty()) {
                throw new IllegalStateException("name is required");
            }
        }
    }

    protected Map<NoteBytesReadOnly, RoutedMessageExecutor> getRoutedMsgMap() {
        return m_msgMap;
    }

    public NoteBytes getRendererId() {
        return rendererId;
    }
  
    @Override
    public CompletableFuture<Void> run() {
        Log.logMsg("[ContainerHandle] Started, auto-creating container: " + containerId);
        
        return create();
    }

    public boolean isCreating(){
        return stateMachine.hasState(Container.STATE_CREATING);
    }

    public boolean isRenderStreamReady(){
        return stateMachine.hasState(Container.STATE_STREAM_READY);
    }

    public boolean isRenderStreamError(){
        return stateMachine.hasState(Container.STATE_STREAM_ERROR);
    }

    public boolean canCreate(){
        return !isCreating() && !isRenderStreamReady() && !isRenderStreamError();
    }

    public CompletableFuture<Void> create(){
        if(!canCreate()){
            if(isRenderStreamError()){
                return CompletableFuture.failedFuture(streamError);
            }
            return CompletableFuture.completedFuture(null);
        }

        stateMachine.addState(Container.STATE_CREATING);
        registry.connect(contextPath, renderingServicePath);
        registry.connect(renderingServicePath, contextPath);

        NoteBytesMap createCmd = new NoteBytesMap();
        createCmd.put(Keys.CMD, ContainerCommands.CREATE_CONTAINER);
        createCmd.put(ContainerCommands.CONTAINER_ID, containerId.toNoteBytes());
        createCmd.put(Keys.TITLE, title);
        createCmd.put(Keys.PATH, getParentPath().toNoteBytes());
        createCmd.put(Keys.CONFIG, containerConfig.toNoteBytes());
        createCmd.put(ContainerCommands.RENDERER_ID, rendererId);
        createCmd.put(ContainerCommands.AUTO_FOCUS, builder.autoFocus ? NoteBoolean.TRUE : NoteBoolean.FALSE);
        

        return request(renderingServicePath, createCmd.toNoteBytesReadOnly(), Duration.ofMillis(500))
            .thenCompose(response -> {
                NoteBytesMap responseMap = response.getPayload().getAsNoteBytesMap();
                NoteBytesReadOnly status = responseMap.getReadOnly(Keys.STATUS);
                if (status == null || !status.equals(ProtocolMesssages.SUCCESS)) {
                    String errorMsg = ProtocolObjects.getErrMsg(responseMap);
                    throw new RuntimeException("Container creation failed: " + errorMsg);
                }
            
                allocatedRegion = extractRegionFromResponse(responseMap);
                dimensionsInitialized = true;
                Log.logMsg("[ContainerHandle] Container created successfully: " + containerId);
                uiExecutor.executeFireAndForget(()->{
              
                    stateMachine.addState(Container.STATE_INITIALIZED);
                });
                return requestStreamChannel(renderingServicePath);
            }).thenAccept(channel -> {
                Log.logMsg("[ContainerHandle] Render stream established");
                this.renderStream = channel;
                this.renderWriter = new NoteBytesWriter(channel.getChannelStream());

                channel.getReadyFuture().whenComplete((v,ex)->{
                    uiExecutor.execute(()->{
                        if(ex == null){
                            stateMachine.addState(Container.STATE_STREAM_READY);
                        }else{
                            stateMachine.addState(Container.STATE_STREAM_ERROR);
                            streamError = ex;
                        }
                        stateMachine.removeState(Container.STATE_CREATING);
                    });
                });
            })
            .whenComplete((v, ex) -> {
                if(ex != null){
                    uiExecutor.executeFireAndForget(()->{
                        stateMachine.removeState(Container.STATE_CREATING);
                        stateMachine.addState(Container.STATE_STREAM_ERROR);
                        Log.logError("[ContainerHandle] Initialization failed: " + ex.getMessage());
                        streamError = ex;
                        unregisterProcess(contextPath);
                    });
                }
            });
    }

    /**
     * Extract spatial region from container creation response
     * Concrete implementations translate renderer-specific format
     * 
     * @param responseMap Response from renderer
     * @return Initial region in renderer's coordinate system
     */
    protected abstract S extractRegionFromResponse(NoteBytesMap responseMap);

 



   
    @Override
    public void onStop() {
        if(!stateMachine.hasState(Container.STATE_DESTROYING)){
            close();
        }else{
             Log.logMsg("[ContainerHandle:" + getId() + "] Stopped for container: " + containerId);
            stateMachine.setState(Container.STATE_DESTROYED);
            super.onStop();
        }
     
    }

    public CompletableFuture<Void> getEventStreamReadyFuture() { 
        return eventStreamReadyFuture; 
    }

    @Override
    public CompletableFuture<Void> handleMessage(RoutedPacket packet) {
        NoteBytesMap message = packet.getPayload().getAsNoteBytesMap();
        NoteBytesReadOnly cmd = message.getReadOnly("cmd");
        
        if (cmd == null) {
            Log.logError("[ContainerHandle] No cmd in message");
            return CompletableFuture.completedFuture(null);
        }
        
        RoutedMessageExecutor msgExec = m_msgMap.get(cmd);
        
        if (msgExec != null) {
            return msgExec.execute(message, packet);
               
        }

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void handleStreamChannel(StreamChannel channel, ContextPath fromPath) {
        if (fromPath == null) {
            throw new NullPointerException("[ContainerHandle] handleStreamChannel from path is null");
        }
        
        if (fromPath.equals(renderingServicePath)) {
            Log.logMsg("[ContainerHandle] Event stream received");
        
            this.eventChannel = channel;
            
            VirtualExecutors.getVirtualExecutor().execute(() -> {
                Log.logMsg("[ContainerHandle] Event stream read thread started");
                try (
                    NoteBytesReader reader = new NoteBytesReader(
                        new PipedInputStream(channel.getChannelStream(), 
                            StreamUtils.PIPE_BUFFER_SIZE)
                    );
                ) {
                    channel.getReadyFuture().complete(null);
                    eventStreamReadyFuture.complete(null);
                    Log.logMsg("[ContainerHandle] Event stream reader active");
                    
                    NoteBytes nextBytes = reader.nextNoteBytes();
                    
                    while (nextBytes != null && isAlive()) {
                        if (nextBytes.getType() == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
                            RoutedEvent event = createRoutedEvent(nextBytes);
                            dispatchEvent(event);
                        }
                        nextBytes = reader.nextNoteBytes();
                    }
                    
                    Log.logMsg("[ContainerHandle] Event stream reader stopped");
                } catch (IOException e) {
                    Log.logError("[ContainerHandle] Event stream error: " + e.getMessage());
                    throw new CompletionException(e);
                }
            });
        }
    }

   

    protected abstract RoutedEvent createRoutedEvent(NoteBytes eventBytes) throws IOException;
        

    private ArrayList<EventDispatcher> dispatchers = new ArrayList<>();

    public CompletableFuture<EventDispatcher> addEventDispatcher(){
        EventDispatcher dispatcher = (event)->{
            dispatchEvent(event);
        };
        return uiExecutor.submit(()->{
            dispatchers.add(dispatcher);
            return dispatcher;
        });
    }

    public CompletableFuture<Boolean> removeEventDispatcher(EventDispatcher dispatcher){
        return uiExecutor.submit(()->{
            return dispatchers.remove(dispatcher);
        });
    }

    public CompletableFuture<Boolean> clearEventDispatchers(EventDispatcher dispatcher){
        return uiExecutor.submit(()->{
            dispatchers.clear();
            return null;
        });
    }


    public void dispatchEvent(RoutedEvent event) {
        uiExecutor.executeFireAndForget(()->{
       
            if(containerPredicate == null || !containerPredicate.test(event)){
                eventHandlerRegistry.dispatch(event);
            }
        
            if (event.isConsumed() || rootRenderable == null) {
                return;
            }

            if (filterList.isEnabled()) {
                if (!filterList.test(event)) {
                    // Event filtered out
                    Log.logMsg("[ContainerHandle:" + containerId + 
                        "] Event filtered: " + 
                        EventBytes.getEventName(event.getEventTypeBytes()) +
                        " from " + event.getSourcePath());
                    return;
                }
            }

            if(manageFocus){
                handleTraversalKey(event);

                
                if (shouldRouteToFocused(event)) {
                    dispatchToFocused(event);
                    return;
                }
            }

            rootRenderable.dispatchEvent(event);
        
        });
    }

    public CompletableFuture<Void> setContainerEventPredicate(Predicate<RoutedEvent> containerFilter){
        return uiExecutor.submit(()->{
            this.containerPredicate = containerFilter;
            return null;
        });
    }

    public SerializedVirtualExecutor getUiExecutor() {
        return uiExecutor;
    }
    
    // ===== CONTAINER OPERATIONS =====
 
    public CompletableFuture<Void> show() {
        NoteBytesMap msg = ContainerCommands.showContainer(containerId, rendererId);
        return sendToService(msg);
    }
    
    public CompletableFuture<Void> hide() {
        
        NoteBytesMap msg = ContainerCommands.hideContainer(containerId, rendererId);
        return sendToService(msg);
    }
    
    public CompletableFuture<Void> focus() {
        
        NoteBytesMap msg = ContainerCommands.focusContainer(containerId, rendererId);
        return sendToService(msg);
    }
    
    public CompletableFuture<Void> maximize() {
        
        NoteBytesMap msg = ContainerCommands.maximizeContainer(containerId, rendererId);
        return sendToService(msg);
    }
    
    public CompletableFuture<Void> restore() {
        
        NoteBytesMap msg = ContainerCommands.restoreContainer(containerId, rendererId);
        return sendToService(msg);
    }

    private CompletableFuture<Void> closingFuture = null;

    public CompletableFuture<Void> close() {
        if (rendererId == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("[ContainerHandle] container not yet created"));
        }

        if(closingFuture != null) return closingFuture;
        closingFuture = new CompletableFuture<>();
        destroy();
        return closingFuture;
    }

    private CompletableFuture<Void> destroy(){
        stateMachine.addState(Container.STATE_DESTROYING);
        return sendToService(ContainerCommands.destroyContainer(containerId, rendererId));
    }
    
    public CompletableFuture<RoutedPacket> queryContainer() {
       
        NoteBytesMap msg = ContainerCommands.queryContainer(containerId, rendererId);
        return request(renderingServicePath, msg.toNoteBytesReadOnly(), 
            Duration.ofSeconds(1));
    }
    
    // ===== RENDER COMMAND SENDING =====
    
    protected CompletableFuture<Void> sendRenderCommand(NoteBytes command) {
        return CompletableFuture.runAsync(() -> {
            Log.logNoteBytes("[ContainerHandle.sendRenderCommand]", command);
            
            if (!renderStream.isActive() || renderStream.isClosed()) {
                Log.logMsg("[ContainerHandle] Render stream is closed, skipping");
                return;
            }
            
            try {
                renderWriter.write(command);
                renderWriter.flush();
            } catch (IOException ex) {
                throw new CompletionException(ex);
            }
            
        }, renderStream.getWriteExecutor());
    }

    
    protected CompletableFuture<Void> sendToService(NoteBytesMap command) {
        if (isDestroyed()) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Container already destroyed")
            );
        }
        
        return request(renderingServicePath, command.toNoteBytesReadOnly(), 
                Duration.ofMillis(500))
            .thenAccept(reply -> {
                NoteBytesMap response = reply.getPayload().getAsNoteBytesMap();
                NoteBytesReadOnly status = response.getReadOnly(Keys.STATUS);
                
                if (status != null && !status.equals(ProtocolMesssages.SUCCESS)) {
                    String errorMsg = ProtocolObjects.getErrMsg(response);
                    throw new RuntimeException("[ContainerHandle] Command failed: " + errorMsg);
                }
            });
    }

    public boolean isDestroying() {
        return stateMachine.hasState(Container.STATE_DESTROYING);
    }
    
    // ===== GETTERS =====
    
    public ContainerId getId() {
        return containerId;
    }
    
    public String getTitle() {
        return title;
    }
    
    public ContainerConfig getConfig() {
        return containerConfig;
    }
    
    public ContextPath getRenderingServicePath() {
        return renderingServicePath;
    }

    protected void executeFireAndForget(Runnable run) {
        uiExecutor.executeFireAndForget(run);
    }

    protected CompletableFuture<Void> execute(Runnable run) {
        return uiExecutor.execute(run);
    }

    public CompletableFuture<Void> setRenderable(R renderable) {
        return setRenderable(renderable, null);
    }

    public CompletableFuture<Void> setRenderable(R renderable, LCB callback) {
        return uiExecutor.execute(() -> {
            R old = rootRenderable;
            if(old == renderable){
                return;
            }
            if (old != null) {
                old.unregisterRenderable();
                old.setRenderRequest(null);
                if (onRenderableUnregistered != null) {
                    onRenderableUnregistered.accept(old);
                }
            }
            
            this.rootRenderable = renderable;
            resetFocusInternal();

            if (onRenderableChanged != null && old != renderable) {
                onRenderableChanged.accept(old, renderable);
            }
            
            if (renderable != null && old != renderable) {
                renderable.setRenderRequest(this::renderableRequestRender);
                
                renderableLayoutManager.registerRenderable(renderable, callback);
                
                if (dimensionsInitialized && allocatedRegion != null) {
                    applyRegionToRenderable(renderable, allocatedRegion);
                }
                
                if (onRenderableRegistered != null) {
                    onRenderableRegistered.accept(renderable);
                }
            }
        });
    }

        
    protected void renderableRequestRender(R renderable) {
        uiExecutor.executeFireAndForget(()->renderableRequestRenderInternal(renderable));
    }

    private void renderableRequestRenderInternal(R renderable){
        if (rootRenderable != renderable) {
                return; // Stale request from old renderable
            }
            
            if (!renderReadySnapshot) {
                return; // Not ready to render yet
            }
            
            if (!rootRenderable.needsRender()) {
                return; // Already rendered
            }
            
            render();
    }

    public CompletableFuture<R> getRenderable() {
        return uiExecutor.submit(this::getRenderableInternal);
    }

    private R getRenderableInternal(){
        return rootRenderable;
    }

    public CompletableFuture<Void> clearRenderable() {
        return uiExecutor.execute(this::clearRenderableInternal);
      
        
    }

    private Void clearRenderableInternal(){
          if (this.rootRenderable != null) {
            // Async unregister
            renderableLayoutManager.unregisterRenderableTree(rootRenderable);
            rootRenderable.setRenderRequest(null);
            
            if (onRenderableUnregistered != null) {
                onRenderableUnregistered.accept(rootRenderable);
            }
        }
        this.rootRenderable = null;
        return null;
    }

    public void setOnRenderableRegistered(Consumer<R> callback) {
        this.onRenderableRegistered = callback;
    }

    public void setOnRenderableUnregistered(Consumer<R> callback) {
        this.onRenderableUnregistered = callback;
    }



    protected abstract B createBatch();
   

    private void render(){
        uiExecutor.executeFireAndForget(this::renderInternal);
    }

    /**
     * Perform actual render - runs on serialExec
     */
    private void renderInternal() {
       
        B batch = createBatch();
        rootRenderable.toBatch(batch);
        
        if (batch.isEmpty()) {
            rootRenderable.clearRenderFlag();
            return;
        }
        
        NoteBytes batchCommand = batch.build();
        
        // Send async on stream executor (don't block serialExec)
        renderStream.getWriteExecutor().execute(() -> {
            try {
                renderWriter.write(batchCommand);
                renderWriter.flush();
            } catch (IOException ex) {
                Log.logError("[ContainerHandle:" + containerId + 
                    "] Render write failed", ex);
            }
        });
        
        rootRenderable.clearRenderFlag();
      
    }
    
    @SuppressWarnings("unchecked")
    public H self() {
        return (H) this;
    }
    /*
     * Add handlers to the flow process message router
     */
    protected abstract void setupRoutedMessageMap();

    private void setupEventHandlers() {
        eventHandlerRegistry.register(EventBytes.EVENT_CONTAINER_RENDERED, 
            this::handleContainerRendered);
        eventHandlerRegistry.register(EventBytes.EVENT_CONTAINER_REGION_CHANGED, 
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
    }

    private void handleContainerRendered(RoutedEvent event) {
        onContainerRendered(event);
    }
    
    private void handleContainerResized(RoutedEvent event) {
        onContainerResized(event);
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
    
 
    protected abstract void onContainerRendered(RoutedEvent event);
    protected abstract void onContainerResized(RoutedEvent event);


    /**
     * Extract region from resize event
     * Concrete implementations interpret event data
     */
    protected abstract S extractRegionFromResizeEvent(RoutedEvent event);

    public abstract CompletableFuture<Void> requestContainerRegion(S region);
    /**
     * Check if two regions are equal
     */
    protected abstract boolean regionsEqual(S a, S b);


    public S getAllocatedRegion() { 
        return allocatedRegion != null ? allocatedRegion.copy() : null; 
    }


    protected void onContainerShown() {
        stateMachine.addState(Container.STATE_VISIBLE);
        Log.logMsg("[ContainerHandle:" + getName() + "] Container shown");
    }

    public boolean isVisible() {
        return stateMachine.hasState(Container.STATE_VISIBLE);
    }
    
    protected void onContainerHidden() {
        stateMachine.removeState(Container.STATE_VISIBLE);
        Log.logMsg("[ContainerHandle:" + getName() + "] Container hidden");
    }
    
    protected void onContainerFocusGained() {
        stateMachine.addState(Container.STATE_FOCUSED);
        Log.logMsg("[ContainerHandle:" + getName() + "] Container focused");
    }
    
    protected void onContainerFocusLost() {
        stateMachine.removeState(Container.STATE_FOCUSED);
        Log.logMsg("[ContainerHandle:" + getName() + "] Container focus lost");
    }



    void setTraversalKeyInternal(NoteBytesReadOnly key) {
        traversalKey = key != null ? key : KeyCodeBytes.TAB;
    }

    NoteBytesReadOnly getTraversalKeyInternal() {
        return traversalKey;
    }

    void setTraversalStrategyInternal(FocusTraversalStrategy strategy) {
        if (strategy != null) {
            this.strategy = strategy;
        }
    }

    FocusTraversalStrategy getTraversalStrategyInternal() {
        return strategy;
    }

    void resetFocusInternal() {
        if (focused != null) {
            focused.clearFocus();
        }
        focused = null;
    }

    void requestFocusInternal(R renderable) {
        if (renderable == null) {
            return;
        }
        if (!renderable.isFocusable()) {
            return;
        }
        setFocusedInternal(renderable);
    }


    void handleTraversalKey(RoutedEvent event) {
        
        NoteBytes keyBytes = getKeyBytes(event);
        if (keyBytes == null) {
            return;
        }

        NoteBytesReadOnly key = traversalKey;
        if (key != null && keyBytes.equals(key)) {
            boolean reverse = (event.getStateFlags() & EventBytes.StateFlags.MOD_SHIFT) != 0;
            if (focusNextInternal(reverse)) {
                event.setConsumed(true);
                if (event instanceof AutoCloseable closable) {
                    try {
                        closable.close();
                    } catch (Exception ex) {
                        Log.logError("[ContainerHandle] Focus event close failed", ex);
                    }
                }
            }
        }
    }

    boolean shouldRouteToFocused(RoutedEvent event) {
        return isKeyboardEvent(event) && getFocusedRenderableInternal() != null;
    }

    void dispatchToFocused(RoutedEvent event) {
        R target = getFocusedRenderableInternal();
        if (target != null) {
            target.dispatchEvent(event);
        }
    }

    protected boolean focusNextInternal(boolean reverse) {
        List<R> focusables = getOrderedFocusables();
        if (focusables.isEmpty()) {
            return false;
        }

        int size = focusables.size();
        int currentIndex = focused != null ? focusables.indexOf(focused) : -1;
        int nextIndex;
        if (currentIndex == -1) {
            nextIndex = reverse ? size - 1 : 0;
        } else if (reverse) {
            nextIndex = (currentIndex - 1 + size) % size;
        } else {
            nextIndex = (currentIndex + 1) % size;
        }

        setFocusedInternal(focusables.get(nextIndex));
        return true;
    }

    private void setFocusedInternal(R next) {
        if (next == focused) {
            return;
        }

        if (focused != null) {
            focused.clearFocus();
        }

        focused = next;
        if (focused != null) {
            focused.focus();
        }
    }

    protected R getFocusedRenderableInternal() {
        if (focused != null && !focused.isFocusable()) {
            focused.clearFocus();
            focused = null;
        }
        return focused;
    }

    protected List<R> getOrderedFocusables()
    {
        if (rootRenderable == null) {
            return Collections.emptyList();
        }

        List<R> focusables = rootRenderable.getFocusableDescendants();
        if (strategy == FocusTraversalStrategy.TAB_INDEX_THEN_SCREEN) {
            focusables.sort(this::compareByScreenPosition);
        }
        return focusables;
    }


    protected abstract int compareByScreenPosition(R a, R b);

    protected int compareFocusIndex(R a, R b) {
        int ai = a.getFocusIndex();
        int bi = b.getFocusIndex();
        boolean aSet = ai >= 0;
        boolean bSet = bi >= 0;
        if (aSet && bSet) {
            return Integer.compare(ai, bi);
        }
        if (aSet) {
            return -1;
        }
        if (bSet) {
            return 1;
        }
        return 0;
    }

    private NoteBytes getKeyBytes(RoutedEvent event) {
        if (event instanceof KeyDownEvent kd) {
            return kd.getKeyCodeBytes();
        }
        if (event instanceof EphemeralKeyDownEvent ek) {
            return ek.getKeyCodeBytes();
        }
        return null;
    }

    private boolean isKeyboardEvent(RoutedEvent event) {
        NoteBytes type = event.getEventTypeBytes();
        return EventBytes.EVENT_KEY_DOWN.equals(type)
            || EventBytes.EVENT_KEY_UP.equals(type)
            || EventBytes.EVENT_KEY_REPEAT.equals(type)
            || EventBytes.EVENT_KEY_CHAR.equals(type);
    }

    
    public RM getRenderableLayoutManager(){
        return renderableLayoutManager;
    }

    public CompletableFuture<Void> focusNext() {
        return uiExecutor.execute(() -> {
            focusNextInternal(false);
        });
    }

    public CompletableFuture<Void> focusPrevious() {
        return uiExecutor.execute(() -> {
            focusNextInternal(true);
        });
    }

    public CompletableFuture<Void> setFocusTraversalKey(NoteBytesReadOnly key) {
        return uiExecutor.execute(() -> {
            setTraversalKeyInternal(key);
        });
    }

    public CompletableFuture<Void> setFocusTraversalStrategy(FocusTraversalStrategy strategy) {
        return uiExecutor.execute(() -> {
            setTraversalStrategyInternal(strategy);
        });
    }

    public CompletableFuture<NoteBytesReadOnly> getFocusTraversalKey() {
        return uiExecutor.submit(() -> getTraversalKeyInternal());
    }

    public CompletableFuture<FocusTraversalStrategy> getFocusTraversalStrategy() {
        return uiExecutor.submit(() -> getTraversalStrategyInternal());
    }

    protected void onContainerClosed() {
        Log.logMsg("[ContainerHandle:" + containerId + "] Container closed");
        stateMachine.removeState(Container.STATE_STREAM_READY);
        
        CompletableFuture.allOf(
            deviceManagers.values().stream()
                .map(DM::detach)
                .toArray(CompletableFuture[]::new)
        )
        .thenCompose(v -> {
            deviceManagers.clear();
            return closeIOSession();
        })
        .thenRun(() -> {
            if (rootRenderable != null) {
                renderableLayoutManager.unregisterRenderableTree(rootRenderable);
            }
            // Shutdown handles executor cleanup
            renderableLayoutManager.shutdown();
        })
        .thenRun(this::kill)
        .exceptionally(ex -> {
            Log.logError("[ContainerHandle:" + containerId + "] Cleanup error", ex);
            kill();
            return null;
        });
        
        handleOnClosed();
    }

    /**
     * Connect this handle to IODaemon
     * Called automatically when device managers need it
     */
    /**
     * Ensure IODaemon session exists for this handle
     * Called automatically by DeviceManagers
     */
    public CompletableFuture<ClientSession> ensureIOSession() {
        if(ioDaemonPath == null){
            return CompletableFuture.failedFuture(new IllegalStateException("IoDaemon Path not set"));
        }
        return getIODaemonSession()
            .thenCompose(clientSession->{
                if (clientSession != null && clientSession.isAlive()) {
                    return CompletableFuture.completedFuture(clientSession);
                }

                String sessionId = NoteUUID.createSafeUUID128();
                int pid = (int) ProcessHandle.current().pid();
                
                return registry.request(
                    ioDaemonPath,
                    new NoteBytesObject(
                        new NoteBytesPair(Keys.CMD, SESSION_CMDS.CREATE_SESSION),
                        new NoteBytesPair(Keys.SESSION_ID, sessionId),
                        new NoteBytesPair(Keys.PID, pid)
                    ).readOnly(),
                    Duration.ofSeconds(2)
                )
                .thenCompose(reply -> {
                    NoteBytesReadOnly status = reply.getPayload().getAsNoteBytesMap()
                        .getReadOnly(Keys.STATUS);
                    
                    if (status == null || !status.equals(ProtocolMesssages.SUCCESS)) {
                        String errorMsg = reply.getPayload().getAsNoteBytesMap()
                            .get(Keys.MSG).getAsString();
                        throw new RuntimeException("Failed to create session: " + errorMsg);
                    }
                    
                    NoteBytes pathBytes = reply.getPayload().getAsNoteBytesMap().get(Keys.PATH);
                    ContextPath sessionPath = ContextPath.fromNoteBytes(pathBytes);
                    
                    ioSession = (ClientSession) registry.getProcess(sessionPath);
                    
                    Log.logMsg("[ContainerHandle:" + containerId + 
                        "] IODaemon session created: " + sessionPath);
                    
                    return setIdoDaemonSession(ioSession);
                       
                });
        });
        
    }

    private CompletableFuture<ClientSession> setIdoDaemonSession(ClientSession session){
        return uiExecutor.submit(()->{
            this.ioSession = session;
            return session;
        });
    }

    public CompletableFuture<ClientSession> getIODaemonSession(){
        return uiExecutor.submit(()->{
            return this.ioSession;
        });
    }
    
  
    /**
     * Close IODaemon session
     */
    private CompletableFuture<Void> closeIOSession() {
        
        return getIODaemonSession()
            .thenCompose(ioSession->{
                if (ioSession == null) {
                    return CompletableFuture.completedFuture(null);
                }
                
                ClientSession session = ioSession;
                ioSession = null;
                
                return session.shutdown()
                    .thenRun(() -> {
                        Log.logMsg("[ContainerHandle:" + containerId + 
                            "] IODaemon session closed");
                    })
                    .exceptionally(ex -> {
                        Log.logError("[ContainerHandle:" + containerId + 
                            "] Session close error: " + ex.getMessage());
                        return null;
                    });
             });
    }
   


    /**
     * Register a device manager with this handle
     */
    public CompletableFuture<DM> addDeviceManager(String id, DM manager) {
        return uiExecutor.submit(() -> {
            deviceManagers.put(id, manager);
            manager.attachToHandle(self());
            return manager;
        });
    }

     public CompletableFuture<DM> getDeviceManager(String id) {
        return uiExecutor.submit(() -> {
            return deviceManagers.get(id);
        });
    }


    /**
     * Remove a device manager
     */
    public CompletableFuture<Void> removeDeviceManager(String id) {
        return uiExecutor.submit(() -> deviceManagers.remove(id)).thenCompose(manager->{
            if(manager != null){
                Log.logMsg("[ContainerHandle:" + containerId + "] Device manager removed: " + id);
                return manager.detach();
            }
            return CompletableFuture.completedFuture(null);
        });
    }

}
