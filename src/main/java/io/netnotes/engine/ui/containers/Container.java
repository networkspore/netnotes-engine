package io.netnotes.engine.ui.containers;

import java.io.IOException;
import java.io.PipedInputStream;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.process.ChannelWriter;
import io.netnotes.engine.io.process.StreamChannel;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.messaging.NoteMessaging.MessageExecutor;
import io.netnotes.noteBytes.NoteBytes;
import io.netnotes.noteBytes.NoteBytesObject;
import io.netnotes.noteBytes.NoteBytesReadOnly;
import io.netnotes.noteBytes.collections.NoteBytesMap;
import io.netnotes.noteBytes.processing.NoteBytesMetaData;
import io.netnotes.noteBytes.processing.NoteBytesReader;
import io.netnotes.noteBytes.processing.NoteBytesWriter;
import io.netnotes.engine.state.BitFlagStateMachine;
import io.netnotes.engine.ui.SpatialPoint;
import io.netnotes.engine.ui.SpatialRegion;
import io.netnotes.engine.ui.containers.containerEvents.ContainerEventSerializer;
import io.netnotes.engine.utils.LoggingHelpers.Log;
import io.netnotes.engine.utils.streams.StreamUtils;
import io.netnotes.engine.utils.virtualExecutors.SerializedVirtualExecutor;
import io.netnotes.engine.utils.virtualExecutors.VirtualExecutors;

/**
 * Container - Abstract base class for all container implementations
 * 
 * STATE MACHINE MODEL:
 * - States represent USER-VISIBLE container states only
 * - Renderer determines what to render based on these states
 * - Container never decides if it's "actively rendering"
 * - Error states prevent rendering via renderer logic, not state removal
 * 
 * RENDERING MODEL:
 * - PULL-BASED: Container updates internal state, renderer pulls when ready
 * - Container NEVER renders itself directly
 * - Container calls requestRender() to signal changes
 * - UIRenderer polls/observes and renders on its own schedule
 */
public abstract class Container<
    P extends SpatialPoint<P>,
    S extends SpatialRegion<P,S>,
    T extends Container<P,S,T>
> {
    
    

    // ===== CONTAINER STATE BIT POSITIONS =====
    
    // Core lifecycle states (bits 0-3)
    public static final int STATE_CREATING       = 0;
    public static final int STATE_INITIALIZED    = 1;
    public static final int STATE_DESTROYING     = 2;
    public static final int STATE_DESTROYED      = 3;
    
    // Visibility states (bits 4-7)
    public static final int STATE_VISIBLE        = 4;
    public static final int STATE_HIDDEN         = 5;
    public static final int STATE_MINIMIZED      = 6;
    public static final int STATE_MAXIMIZED      = 7;
    
    // Focus state (bit 8)
    public static final int STATE_FOCUSED        = 8;
    
    // Stream states (bits 11-12)
    public static final int STATE_STREAM_CONNECTED = 11;
    public static final int STATE_STREAM_READY   = 12;
    
    // Advanced layout states (bits 14-21)
    public static final int STATE_FULLSCREEN     = 13;
    public static final int STATE_ANIMATING      = 14;
    public static final int STATE_TRANSITIONING  = 15;
    public static final int STATE_COMPOSITING    = 16;
    public static final int STATE_OVERLAY        = 17;
    public static final int STATE_BACKGROUND     = 18;
    public static final int STATE_SPLIT_SCREEN   = 19;
    public static final int STATE_PIP            = 20;
    
    // Resource states (bits 22-23)
    public static final int STATE_RESOURCES_LOADED = 22;
    public static final int STATE_RESOURCES_UNLOADING = 23;
    
    // Error states (bits 24-26)
    public static final int STATE_ERROR          = 24;
    public static final int STATE_RECOVERING     = 25;
    public static final int STATE_RENDER_ERROR   = 26;
    public static final int STATE_STREAM_ERROR   = 27;

    // Request states (bits 27-34)
   
    public static final int STATE_HIDE_REQUESTED     = 28;
    public static final int STATE_MAXIMIZE_REQUESTED = 29;
    public static final int STATE_RESTORE_REQUESTED  = 30;
    public static final int STATE_DESTROY_REQUESTED  = 31;
    public static final int STATE_UPDATE_REQUESTED   = 32;
    public static final int STATE_FOCUS_REQUESTED    = 33;
    public static final int STATE_RENDER_REQUESTED   = 34;
    public static final int STATE_SHOW_REQUESTED     = 35;


    // ===== CORE IDENTITY =====
    protected final ContainerId id;
    protected final AtomicReference<String> title;
    protected final AtomicReference<ContainerConfig> config;
    protected final ContextPath ownerPath;
    protected final ContextPath path;
    protected final String rendererId;
    protected final long createdTime;

    protected S bounds;
    
    // ===== STATE MACHINE =====
    protected final BitFlagStateMachine stateMachine;
    
    // ===== STREAM CHANNELS =====
    protected StreamChannel renderStreamChannel = null;
    protected ChannelWriter eventWriter = null;
 
    protected CompletableFuture<Void> renderStreamFuture = new CompletableFuture<>();
    
    // ===== MESSAGE DISPATCH =====
    protected final ConcurrentHashMap<NoteBytesReadOnly, MessageExecutor> msgMap = new ConcurrentHashMap<>();
    protected final HashMap<NoteBytes, MessageExecutor> batchMsgMap = new HashMap<>();

    protected final SerializedVirtualExecutor containerExecutor = new SerializedVirtualExecutor();

    protected Consumer<T> onRequestMade = null;

    // Handler futures
    protected CompletableFuture<Void> showFuture = null;
    protected CompletableFuture<Void> destroyFuture = null;
    protected CompletableFuture<Void> focusFuture = null;
    protected CompletableFuture<Void> maximizeFuture = null;
    protected CompletableFuture<Void> restoreFuture = null;
    protected CompletableFuture<Void> hideFuture = null;
    protected CompletableFuture<Void> updateFuture = null;

    /**
     * Full constructor
     */
    protected Container(
        ContainerId id,
        String title,
        ContextPath ownerPath,
        ContainerConfig config,
        String rendererId,
        S bounds
    ) {
        this.id = id;
        this.title = new AtomicReference<>(title);
        this.config = new AtomicReference<>(config);
        this.ownerPath = ownerPath;
        this.path = ownerPath != null ? ownerPath.append("container", id.toString()) : null;
        this.rendererId = rendererId;
        this.createdTime = System.currentTimeMillis();
        this.bounds = bounds;
        // Initialize state machine
        this.stateMachine = new BitFlagStateMachine("Container:" + id);
        this.stateMachine.addState(STATE_CREATING);
        
        // Setup base state transitions
        setupBaseStateTransitions();
        
        // Subclass adds its transitions
        setupStateTransitions();
        
        // Setup base message handlers
        setupBaseMessageMap();
        
        // Subclass adds its handlers
        setupMessageMap();

        setupBatchMsgMap();
    }
    
    public CompletableFuture<S> getRegion(){
        return containerExecutor.submit(()->bounds);
    }

    public abstract CompletableFuture<Void> setRegion(S bounds);


    // ===== ABSTRACT METHODS (Subclass Implementation) =====
    
    protected abstract void setupMessageMap();
    protected abstract void setupBatchMsgMap();
    protected void setupStateTransitions() {}
    protected abstract CompletableFuture<Void> initializeRenderer();
    
    // ===== BASE STATE TRANSITIONS =====
    
    private void setupBaseStateTransitions() {
        // INITIALIZED: Container ready for use
        stateMachine.onStateAdded(STATE_INITIALIZED, (old, now, bit) -> {
            stateMachine.removeState(STATE_CREATING);
        });
        
        // VISIBLE: Container is visible to user
        stateMachine.onStateAdded(STATE_VISIBLE, (old, now, bit) -> {
            stateMachine.removeState(STATE_HIDDEN);
            stateMachine.removeState(STATE_MINIMIZED);
        });
        
        // HIDDEN: Container is hidden from user
        stateMachine.onStateAdded(STATE_HIDDEN, (old, now, bit) -> {
            stateMachine.removeState(STATE_VISIBLE);
            // Hidden containers lose focus
            stateMachine.removeState(STATE_FOCUSED);
        });
        
        // FOCUSED: Container has input focus
        stateMachine.onStateAdded(STATE_FOCUSED, (old, now, bit) -> {
            stateMachine.removeState(STATE_FOCUS_REQUESTED);
            // Focused containers must be visible
            if (stateMachine.hasState(STATE_HIDDEN)) {
                stateMachine.removeState(STATE_HIDDEN);
                stateMachine.addState(STATE_VISIBLE);
            }
        });
        
        stateMachine.onStateRemoved(STATE_FOCUSED, (old, now, bit) -> {
            // Focus lost - container aware but takes no action
        });
        
        // MAXIMIZED: Container is maximized
        stateMachine.onStateAdded(STATE_MAXIMIZED, (old, now, bit) -> {
            // Maximized state - just tracks user intention
        });
        
        stateMachine.onStateRemoved(STATE_MAXIMIZED, (old, now, bit) -> {
            // Restore from maximized
        });
        
        // DESTROYING: Container being destroyed
        stateMachine.onStateAdded(STATE_DESTROYING, (old, now, bit) -> {
            stateMachine.removeState(STATE_FOCUSED);
            stateMachine.removeState(STATE_VISIBLE);
        });
        
        // DESTROYED: Container fully destroyed
        stateMachine.onStateAdded(STATE_DESTROYED, (old, now, bit) -> {
            stateMachine.removeState(STATE_DESTROYING);
        });
        
        // ERROR: Container in error state
        stateMachine.onStateAdded(STATE_ERROR, (old, now, bit) -> {
            Log.logError("[Container:" + id + "] ERROR state - renderer should stop rendering");
        });
        
        stateMachine.onStateRemoved(STATE_ERROR, (old, now, bit) -> {
            Log.logMsg("[Container:" + id + "] ERROR state cleared");
        });
        
        // RENDER_ERROR: Transient render failure
        stateMachine.onStateAdded(STATE_RENDER_ERROR, (old, now, bit) -> {
            Log.logError("[Container:" + id + "] RENDER_ERROR - renderer will retry");
        });
        
        stateMachine.onStateRemoved(STATE_RENDER_ERROR, (old, now, bit) -> {
            Log.logMsg("[Container:" + id + "] RENDER_ERROR cleared");
        });
        
        // STREAM_CONNECTED: Stream established
        stateMachine.onStateAdded(STATE_STREAM_CONNECTED, (old, now, bit) -> {
            // Stream connected
        });
        
        stateMachine.onStateRemoved(STATE_STREAM_CONNECTED, (old, now, bit) -> {
            stateMachine.removeState(STATE_STREAM_READY);
        });

        
    }

    @SuppressWarnings("unchecked")
    protected final T self() {
        return (T) this;
    }
    
    // ===== BASE MESSAGE MAP =====
    
    private void setupBaseMessageMap() {
        msgMap.put(ContainerCommands.SHOW_CONTAINER, this::handleShowContainer);
        msgMap.put(ContainerCommands.HIDE_CONTAINER, this::handleHideContainer);
        msgMap.put(ContainerCommands.DESTROY_CONTAINER, this::handleDestroyContainer);
        msgMap.put(ContainerCommands.FOCUS_CONTAINER, this::handleFocusContainer);
        msgMap.put(ContainerCommands.MAXIMIZE_CONTAINER, this::handleMaximizeContainer);
        msgMap.put(ContainerCommands.RESTORE_CONTAINER, this::handleRestoreContainer);
        msgMap.put(ContainerCommands.UPDATE_CONTAINER, this::handleUpdateContainer);
        msgMap.put(ContainerCommands.QUERY_CONTAINER, this::handleQueryContainer);
    }
    
    // ===== LIFECYCLE =====
    
    public CompletableFuture<Void> initialize() {
        Log.logMsg("[Container:" + id + "] Initializing");
        
        return initializeRenderer()
            .thenRun(() -> {
                stateMachine.addState(STATE_INITIALIZED);
                stateMachine.addState(STATE_VISIBLE);
                Log.logMsg("[Container:" + id + "] Initialized");
            })
            .exceptionally(ex -> {
                Log.logError("[Container:" + id + "] Failed to initialize: " + ex.getMessage());
                stateMachine.addState(STATE_ERROR);
                stateMachine.addState(STATE_DESTROYED);
                return null;
            });
    }
    
    // ===== STREAM HANDLING =====
    
    public void handleRenderStream(StreamChannel channel, ContextPath fromPath) {
        Log.logMsg("[Container:" + id + "] Render stream received from: " + fromPath);
        
        if (renderStreamChannel == null) {
            this.renderStreamChannel = channel;
            stateMachine.addState(STATE_STREAM_CONNECTED);
            
            CompletableFuture.runAsync(() -> {
                try (
                    NoteBytesReader reader = new NoteBytesReader(
                        new PipedInputStream(channel.getChannelStream(), StreamUtils.PIPE_BUFFER_SIZE)
                    );
                ) {
                    channel.getReadyFuture().complete(null);
                    stateMachine.addState(STATE_STREAM_READY);
                    
                    Log.logMsg("[Container:" + id + "] Render stream reader active");
                    
                    NoteBytesReadOnly nextBytes = reader.nextNoteBytesReadOnly();
                    
                    while (nextBytes != null && stateMachine.hasState(STATE_STREAM_CONNECTED)) {
                        if (nextBytes.getType() == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
                            NoteBytesMap command = nextBytes.getAsNoteBytesMap();
                            dispatchCommand(command);
                        }
                        nextBytes = reader.nextNoteBytesReadOnly();
                    }
                    
                } catch (IOException e) {
                    Log.logError("[Container:" + id + "] Render stream error: " + e.getMessage());
                    stateMachine.removeState(STATE_STREAM_CONNECTED);
                    throw new CompletionException(e);
                }
            }, VirtualExecutors.getVirtualExecutor())
                .thenRun(() -> {
                    Log.logMsg("[Container:" + id + "] Render stream completed");
                    stateMachine.removeState(STATE_STREAM_CONNECTED);
                });
        }
    }
    
    public void handleEventStream(StreamChannel channel, ContextPath fromPath) {
        Log.logMsg("[Container:" + id + "] Event stream established to: " + fromPath);

        this.eventWriter = new ChannelWriter(channel);
    }


    
    protected void dispatchCommand(NoteBytesMap command) {
        NoteBytes cmd = command.get(Keys.CMD);
        if (cmd == null) {
            Log.logError("[Container:" + id + "] No cmd in command");
            return;
        }

        Log.logNoteBytes("[Container: "+ getTitle()+"]", command);
        
        MessageExecutor executor = msgMap.get(cmd);
        if (executor != null) {
            try {
                executor.execute(command);
            } catch (Exception e) {
                Log.logError("[Container:" + id + "] Error executing command '" + cmd + "': " + e.getMessage());
            }
        } else {
            Log.logError("[Container:" + id + "] Unknown command: " + cmd);
        }
    }

    public void emitEvent(NoteBytesMap map){
        if (map == null) {
            Log.logError("[Container:" + id + "] Cannot emit event - event is null");
            return;
        }
        emitEvent(map.toNoteBytes());
    }
    
    public void emitEvent(NoteBytes event) {
        if (eventWriter == null) {
            Log.logNoteBytes("[Container:" + id + "] Cannot emit event - no event stream", event);
            return;
        }

        Log.logNoteBytes("[Container.emitEvent]", event);

        SerializedVirtualExecutor eventWriterExecutor =eventWriter.getWriteExec();
        if(eventWriterExecutor.isShutdown()){
            Log.logNoteBytes("[Container:" + id + "] Cannot emit event executor shutdown", event);
            return;
        }
        eventWriterExecutor.execute(()->{
            NoteBytesWriter writer = eventWriter.getWriter();
            if (writer == null) {
                Log.logMsg("[Container] event stream waiting: " + getId());
                try{
                    writer = eventWriter.getReadyWriter()
                        .orTimeout(2, TimeUnit.SECONDS)
                        .join();
                    
                }catch(Exception e){
                    Log.logError("[Container] event stream timed out: " + getId(), e);
                    return;
                }
            }
            try {
                writer.write(event);
            } catch (IOException e) {
                Log.logError("[Container:" + id + "]", "Error emitting event", e);
                throw new CompletionException(e);
            }
        })
            .exceptionally(ex->{
                eventWriterExecutor.shutdown();
                throw new RuntimeException(ex);
            });
    }
    
    // ===== STATE OPERATIONS =====
    
    /**
     * Grant show - renderer calls this to approve show request
     */
    public CompletableFuture<Void> grantShow() {
        return containerExecutor.execute(() -> {
            stateMachine.removeState(STATE_SHOW_REQUESTED);
            stateMachine.removeState(STATE_HIDDEN);
            stateMachine.addState(STATE_VISIBLE);
            onShowGranted();
        })
        .thenRun(() -> {
            if(showFuture != null) {
                showFuture.complete(null);
                showFuture = null;
            }
        })
        .exceptionally(ex -> {
            if(showFuture != null) {
                showFuture.completeExceptionally(ex);
                showFuture = null;
            }
            return null;
        });
    }
    
    /**
     * Grant focus - renderer calls this to approve focus request
     */
    public CompletableFuture<Void> grantFocus() {
        Log.logMsg("[Container].grantFocus");
        return containerExecutor.execute(() -> {
            stateMachine.removeState(STATE_FOCUS_REQUESTED);
            stateMachine.addState(STATE_FOCUSED);
            onFocusGranted();
        })
        .thenRun(() -> {
            if(focusFuture != null) {
                focusFuture.complete(null);
                focusFuture = null;
            }
        })
        .exceptionally(ex -> {
            if(focusFuture != null) {
                focusFuture.completeExceptionally(ex);
                focusFuture = null;
            }
            return null;
        });
    }
    
    /**
     * Grant hide
     */
    public CompletableFuture<Void> grantHide() {
        return containerExecutor.execute(() -> {
            stateMachine.removeState(STATE_HIDE_REQUESTED);
            stateMachine.removeState(STATE_VISIBLE);
            stateMachine.addState(STATE_HIDDEN);
            onHideGranted();
        })
        .thenRun(()->{
            if(hideFuture != null) {
                hideFuture.complete(null);
                hideFuture = null;
            }
        })
        .exceptionally(ex->{
            if(hideFuture != null){
                hideFuture.completeExceptionally(ex);
                hideFuture = null;
            }
            return null;
        });
    }

    /**
     * Revoke focus - renderer calls this when focus moves elsewhere
     */
    public CompletableFuture<Void> revokeFocus() {
        return containerExecutor.execute(() -> {
            stateMachine.removeState(STATE_FOCUSED);
            stateMachine.removeState(STATE_FOCUS_REQUESTED);
            onFocusRevoked();
        });
    }
    
    public CompletableFuture<Void> grantMaximize() {
        return containerExecutor.execute(() -> {
            stateMachine.removeState(STATE_MAXIMIZE_REQUESTED);
            stateMachine.addState(STATE_MAXIMIZED);
            onMaximizeGranted();
        })
        .thenRun(()->{
            if(maximizeFuture != null){
                maximizeFuture.complete(null);
                maximizeFuture = null;
            }
        })
        .exceptionally(ex->{
            if(maximizeFuture != null){
                maximizeFuture.completeExceptionally(ex);
                maximizeFuture = null;
            }
            return null;
        }); 
    }

    public CompletableFuture<Void> grantRestore() {
        return containerExecutor.execute(() -> {
            stateMachine.removeState(STATE_RESTORE_REQUESTED);
            stateMachine.removeState(STATE_MAXIMIZED);
            stateMachine.removeState(STATE_HIDDEN);
            stateMachine.addState(STATE_VISIBLE);
            onRestoreGranted();
        })
        .thenRun(()->{
            if(restoreFuture != null) {
                restoreFuture.complete(null);
                restoreFuture = null;
            }
        })
        .exceptionally(ex->{
            if(restoreFuture != null) {
                restoreFuture.completeExceptionally(ex);
                restoreFuture = null;
            }
            return null;
        });
    }

    public CompletableFuture<Void> grantDestroy() {
        return containerExecutor.execute(() -> {
            stateMachine.removeState(STATE_DESTROY_REQUESTED);
            stateMachine.addState(STATE_DESTROYING);
            Log.logMsg("[Container:" + id + "] Destroying");
        
            closeStreams();
            stateMachine.addState(STATE_DESTROYED);
            containerExecutor.shutdown();
            Log.logMsg("[Container:" + id + "] Destroyed");

            onDestroyGranted();
        })
        .thenRun(()->{
            if(destroyFuture != null) {
                destroyFuture.complete(null);
                destroyFuture = null;
            }
        })
        .exceptionally(ex->{
            Log.logError("[Container:" + id + "] Error during destroy: " + ex.getMessage());
            containerExecutor.shutdownNow();
            if(destroyFuture != null){
                destroyFuture.completeExceptionally(ex);
                destroyFuture = null;
            }
            return null;
        });
    }

    /**
     * Clear pending request - renderer calls if it denies a request
     */
    public CompletableFuture<Void> clearRequest(int requestStateBit) {
        return containerExecutor.execute(() -> {
            stateMachine.removeState(requestStateBit);
            
            // Complete futures with denial
            if(requestStateBit == STATE_FOCUS_REQUESTED && focusFuture != null) {
                focusFuture.completeExceptionally(new Exception("Focus request denied"));
                focusFuture = null;
            } else if(requestStateBit == STATE_SHOW_REQUESTED && showFuture != null) {
                showFuture.completeExceptionally(new Exception("Show request denied"));
                showFuture = null;
            } else if(requestStateBit == STATE_HIDE_REQUESTED && hideFuture != null) {
                hideFuture.completeExceptionally(new Exception("Hide request denied"));
                hideFuture = null;
            } else if(requestStateBit == STATE_MAXIMIZE_REQUESTED && maximizeFuture != null) {
                maximizeFuture.completeExceptionally(new Exception("Maximize request denied"));
                maximizeFuture = null;
            } else if(requestStateBit == STATE_RESTORE_REQUESTED && restoreFuture != null) {
                restoreFuture.completeExceptionally(new Exception("Restore request denied"));
                restoreFuture = null;
            } else if(requestStateBit == STATE_DESTROY_REQUESTED && destroyFuture != null) {
                destroyFuture.completeExceptionally(new Exception("Destroy request denied"));
                destroyFuture = null;
            }
        });
    }

    /*
     * ============ On Event Dispatching ==========
     */

    protected void onDestroyGranted() {
        emitEvent(ContainerEventSerializer.createCloseEvent());
    }
    
    protected void onFocusGranted() {
        emitEvent(ContainerEventSerializer.createFocusEvent());
    }
    
    protected void onFocusRevoked() {
        emitEvent(ContainerEventSerializer.createFocusLostEvent());
    }
    
    protected void onHideGranted() {
        emitEvent(ContainerEventSerializer.createHiddenEvent());
    }
    
    protected void onMaximizeGranted() {
        emitEvent(ContainerEventSerializer.createMaximizeEvent());
    }
    
    protected void onRestoreGranted() {
        emitEvent(ContainerEventSerializer.createRestoredEvent());
    }
    
    protected void onShowGranted() {
        emitEvent(ContainerEventSerializer.createShownEvent());
    }

    public void setOnRequestMade(Consumer<T> notifier) {
        this.onRequestMade = notifier;
    }

    /**
     * Notify render manager of pending request
     */
    protected void notifyRequestMade() {
        if (onRequestMade != null) {
            onRequestMade.accept(self());
        }
    }

    // ===== MESSAGE HANDLERS =====
   
    public CompletableFuture<Void> handleShowContainer(NoteBytesMap command) {
        if(showFuture == null) {
            showFuture = new CompletableFuture<>();
            containerExecutor.execute(() -> stateMachine.addState(STATE_SHOW_REQUESTED));
            notifyRequestMade();
        }
        return showFuture;
    }
    
    public CompletableFuture<Void> handleHideContainer(NoteBytesMap command) {
        if(hideFuture == null) {
            hideFuture = new CompletableFuture<>();
            containerExecutor.execute(() -> stateMachine.addState(STATE_HIDE_REQUESTED));
            notifyRequestMade();
        }
        return hideFuture;
    }
    
    public CompletableFuture<Void> handleDestroyContainer(NoteBytesMap command) {
         if(destroyFuture == null) {
            destroyFuture = new CompletableFuture<>();
            containerExecutor.execute(() -> stateMachine.addState(STATE_DESTROY_REQUESTED));
            notifyRequestMade();
        }
        return destroyFuture;
    }

    public CompletableFuture<Void> destroyNow() {
        if (destroyFuture == null) {
            destroyFuture = new CompletableFuture<>();
            containerExecutor.execute(() -> stateMachine.addState(STATE_DESTROY_REQUESTED));
            notifyRequestMade();
        }
        return destroyFuture;
    }   
 
    public CompletableFuture<Void> handleFocusContainer(NoteBytesMap command) {
        if(focusFuture == null) {
            focusFuture = new CompletableFuture<>();
            containerExecutor.execute(() -> stateMachine.addState(STATE_FOCUS_REQUESTED));
            notifyRequestMade();
        }
        return focusFuture;
    }

    public CompletableFuture<Void> requestFocus() {
        if(focusFuture == null) {
            focusFuture = new CompletableFuture<>();
            containerExecutor.execute(() -> stateMachine.addState(STATE_FOCUS_REQUESTED));
            Log.logMsg("[Container " +id+ "].requestFocus" );
            notifyRequestMade();
        }
        return focusFuture;
    }
    
    public CompletableFuture<Void> handleMaximizeContainer(NoteBytesMap command) {
        if(maximizeFuture == null) {
            maximizeFuture = new CompletableFuture<>();
            containerExecutor.execute(() -> stateMachine.addState(STATE_MAXIMIZE_REQUESTED));
            notifyRequestMade();
        }
        return maximizeFuture;
    }
    
    public CompletableFuture<Void> handleRestoreContainer(NoteBytesMap command) {
        if(restoreFuture == null) {
            restoreFuture = new CompletableFuture<>();
            containerExecutor.execute(() -> stateMachine.addState(STATE_RESTORE_REQUESTED));
            notifyRequestMade();
        }   
        return restoreFuture;
    }
   
    public CompletableFuture<Void> handleUpdateContainer(NoteBytesMap command) {
        if(updateFuture == null) {
            updateFuture = new CompletableFuture<>();
           containerExecutor.execute(() -> {
                NoteBytes updatesBytes = command.get(Keys.UPDATES);
                if (updatesBytes != null && 
                    updatesBytes.getType() == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
                    
                    NoteBytesMap updates = updatesBytes.getAsNoteBytesMap();
                    NoteBytes titleBytes = updates.get(Keys.TITLE);
                    if (titleBytes != null) {
                        title.set(titleBytes.getAsString());
                        stateMachine.addState(STATE_UPDATE_REQUESTED);
                    }
                }
            })
            .exceptionally(ex -> {
                updateFuture.completeExceptionally(ex);
                return null;
            });
        }
        return updateFuture;
    }
  
    private CompletableFuture<Void> handleQueryContainer(NoteBytesMap command) {
        NoteBytesObject response = queryContainer();
        emitEvent(response);
        return CompletableFuture.completedFuture(null);
    }

    public NoteBytesObject queryContainer() {
        ContainerInfo info = getInfo();
        return info.toNoteBytes();
    }
    
    // ===== HELPERS =====
    
    protected void closeStreams() {
        StreamUtils.safeClose(renderStreamChannel);
        if (eventWriter != null) {
            eventWriter.shutdown();
        }
    }
    
    // ===== GETTERS =====

    public ContainerId getId() { return id; }
    public ContextPath getPath() { return path; }
    public long getCreatedTime() { return createdTime; }
    public String getRendererId() { return rendererId; }
    public ContextPath getOwnerPath() { return ownerPath; }
    public String getTitle() { return title.get(); }
    public BigInteger getState() { return stateMachine.getState(); }
    public BitFlagStateMachine getStateMachine() { return stateMachine; }
    public ContainerConfig getConfig() { return config.get(); }
    
    protected BitFlagStateMachine getInternalStateMachine() { return stateMachine; }
    
    // State queries for renderer decisions
    public boolean isFocused() { return stateMachine.hasState(STATE_FOCUSED); }
    public boolean isHidden() { return stateMachine.hasState(STATE_HIDDEN); }
    public boolean isMaximized() { return stateMachine.hasState(STATE_MAXIMIZED); }
    public boolean isVisible() { return stateMachine.hasState(STATE_VISIBLE); }
    public boolean isStreamActive() { return stateMachine.hasState(STATE_STREAM_CONNECTED); }
    public boolean hasError() { return stateMachine.hasState(STATE_ERROR); }
    public boolean hasRenderError() { return stateMachine.hasState(STATE_RENDER_ERROR); }
    
    /**
     * Should this container be rendered?
     * Renderer uses this to decide - NOT the container's responsibility
     */
    public boolean shouldRender() {
        // Don't render if in fatal error state
        if (hasError()) {
            return false;
        }
        
        // Don't render if hidden
        if (isHidden()) {
            return false;
        }
        
        // Don't render if destroyed/destroying
        if (stateMachine.hasState(STATE_DESTROYED) || 
            stateMachine.hasState(STATE_DESTROYING)) {
            return false;
        }
        
        // Must be visible
        return isVisible();
    }
    
    public CompletableFuture<Void> getRenderStreamFuture() { return renderStreamFuture; }
    
    public ContainerInfo getInfo() {
        return new ContainerInfo(
            id, title.get(),rendererId, stateMachine.getState(),
            ownerPath, config.get(), createdTime
        );
    }
    
    @Override
    public String toString() {
        return String.format(
            "Container[id=%s, title=%s, state=0x%X, visible=%s, focused=%s, renderer=%s]",
            id, title.get(), getState(), isVisible(), isFocused(), rendererId
        );
    }
}