package io.netnotes.engine.io.process;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.noteBytes.*;
import io.netnotes.noteBytes.collections.*;
import io.netnotes.engine.utils.LoggingHelpers.Log;
import io.netnotes.engine.utils.noteBytes.NoteUUID;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * FlowProcess - Universal base class for ALL system components
 * 
 * REFACTORED:
 * - No longer has direct registry access
 * - Uses ProcessRegistryInterface for controlled access
 * - Interface is set during initialization (not constructor)
 * - Bootstrap processes get BootstrapProcessInterface
 * - Node processes get ScopedProcessInterface
 */
public abstract class FlowProcess implements Flow.Publisher<RoutedPacket> {
    
    // ===== IDENTITY =====
    protected ContextPath contextPath;
    protected FlowProcessId processId;
    protected ContextPath parentPath;
    
    // ===== REGISTRY ACCESS (Interface, not direct) =====
    // Changed from: public FlowProcessRegistry registry
    // To: protected ProcessRegistryInterface registryInterface
    protected ProcessRegistryInterface registry;
    
    // ===== LIFECYCLE =====
    private volatile boolean alive = true;
    private volatile boolean killed = false;
    private volatile long startTime;
    private volatile long endTime;
    private final CompletableFuture<Void> completionFuture = new CompletableFuture<>();
    
    // ===== REACTIVE STREAMS =====
    private final ProcessSubscriber incomingSubscriber;
    private final ConcurrentHashMap<NoteBytes, Flow.Subscription> incomingSubscriptions = new ConcurrentHashMap<>();

    private final SubmissionPublisher<RoutedPacket> outgoingPublisher;
    private final ExecutorService publisherExecutor;
    
    // ===== ASYNC INFRASTRUCTURE =====
    private final ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final ConcurrentHashMap<NoteBytes, CompletableFuture<RoutedPacket>> pendingRequests = 
        new ConcurrentHashMap<>();
    
    // ===== PROCESS TYPE =====
    private final ProcessType processType;
    private final String name;
    
    /**
     * Constructor - specify process type
     * 
     * NO registry reference in constructor!
     * Registry interface is set later during initialize()
     */
    public FlowProcess(String name, ProcessType type) {

     
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Process name required");
        }
        if (name.contains("/") || name.contains("\\")) {
            throw new IllegalArgumentException(
                "Process name cannot contain path separators: " + name);
        }
        
        this.processType = type;
        this.name = name;
        this.incomingSubscriber = new ProcessSubscriber();
        this.publisherExecutor = getExecutorForType(type);
        this.outgoingPublisher = new SubmissionPublisher<>(
            publisherExecutor,
            getBufferSizeForType(type)
        );
    }

    public String getName() {
        return name;
    }
    
    // ===== INITIALIZATION =====
    
     /**
     * Initialize with EXPLICIT path
     * 
     * 
     * @param contextPath Explicit path for this process
     * @param parentPath Parent's path (for hierarchy tracking, can be null)
     * @param registryInterface Interface for registry operations
     */
    public void initialize(
            ContextPath contextPath,
            ContextPath parentPath,
            ProcessRegistryInterface registryInterface) {
        
        this.contextPath = contextPath;
        this.processId = new FlowProcessId(contextPath);
        this.parentPath = parentPath;
        this.registry = registryInterface;
        this.startTime = System.currentTimeMillis();
        
        Log.logMsg("[FlowProcess] Initialized: " + contextPath + 
            " (name: " + name + ", parent: " + parentPath + 
            ", interface: " + registryInterface.getClass().getSimpleName() + ")");
    }


    /**
     * Intitializes a process directly beneath the parent path
     * utlizing the child's name
     * 
     * @param parentPath
     * @param registryInterface
     */

    public ContextPath initializeParentsChild(FlowProcess process) {
        
        ContextPath computedPath = contextPath.append(process.getName());
        
        initialize(computedPath, contextPath, registry);
        return computedPath;
    }

    /**
     * Register child at EXPLICIT path
     * 
     * @param child Child process
     * @param childPath Explicit path (can be multi-level: "services/group/worker")
     * @return The registered path
     */
    public ContextPath registerChildAt(FlowProcess child, ContextPath childPath) {
        if (registry == null) {
            throw new IllegalStateException("Process not initialized");
        }
        
        // Register with explicit path, this process as parent
        return registry.registerProcess(
            child, 
            childPath, 
            this.contextPath,  // parent for hierarchy
            registry   // same interface
        );
    }


    // ===== CORE LIFECYCLE METHODS =====
    
    public CompletableFuture<Void> run() {
        return CompletableFuture.completedFuture(null);
    }
    
    public CompletableFuture<Void> handleMessage(RoutedPacket packet) {
        return CompletableFuture.completedFuture(null);
    }
    
    public BackpressureStrategy getBackpressureStrategy() {
        return switch (processType) {
            case SOURCE -> BackpressureStrategy.UNBOUNDED;
            case TRANSFORM -> BackpressureStrategy.BUFFERED_100;
            case SINK -> BackpressureStrategy.BUFFERED_100;
            case BIDIRECTIONAL -> BackpressureStrategy.BUFFERED_100;
        };
    }
    
    public abstract void handleStreamChannel(StreamChannel channel, ContextPath fromPath);
    
    public void onStart() {}
    public void onStop() {}
    public void onStreamError(Throwable error) {
        Log.logError("Process " + contextPath + " error: " + error.getMessage());
        error.printStackTrace();
    }
    public void onStreamComplete() {}
    
    // ===== OUTGOING PACKET EMISSION =====
    
    public void emit(RoutedPacket packet) {
        if (!alive) {
            Log.logMsg("[FlowProcess:" + contextPath + "] Cannot emit - process not alive");
            return;
        }
        
        Log.logMsg("[FlowProcess.emit]: source:" + packet.getSourcePath() + 
            " Destination:" + (packet.getDestinationPath() != null ? packet.getDestinationPath() : "broadcast"));
        
        try {
            int lag = outgoingPublisher.submit(packet);
            if (lag > 100) {
                Log.logMsg("WARNING: " + contextPath + 
                    " downstream lagging (buffer: " + lag + ")");
            }
        } catch (Exception e) {
            Log.logError("Error emitting packet: " + e.getMessage());
        }
    }
    
    public void emit(NoteBytesMap map) {
        emit(map.toNoteBytes());
    }
    
    public void emit(NoteBytesObject object) {
        emit(object.readOnly());
    }
    
    public void emit(NoteBytesReadOnly payload) {
        emit(RoutedPacket.create(contextPath, payload));
    }
    
    public void emitTo(ContextPath destination, NoteBytesPair... pairs) {
        emitTo(destination, new NoteBytesObject(pairs));
    }
    
    public void emitTo(ContextPath destination, NoteBytesMap payload) {
        emitTo(destination, payload.toNoteBytes());
    }
    
    public void emitTo(ContextPath destination, NoteBytesObject payload) {
        emitTo(destination, payload.readOnly());
    }
    
    public void emitTo(ContextPath destination, NoteBytesReadOnly payload) {
        // ADD THIS LOGGING
        Log.logMsg("[FlowProcess:" + contextPath + "] emitTo " + destination);
        
        emit(RoutedPacket.createDirect(contextPath, destination, payload));
    }
    
    // ===== PUBLISHER INTERFACE =====
    
    @Override
    public void subscribe(Flow.Subscriber<? super RoutedPacket> subscriber) {
        Log.logMsg("[FlowProcess:" + contextPath + "] subscribe() called");
        Log.logMsg("  Subscriber: " + subscriber.getClass().getSimpleName());
        Log.logMsg("  Process alive: " + alive);
        Log.logMsg("  Publisher closed: " + outgoingPublisher.isClosed());
        Log.logMsg("  Current subscribers: " + getSubscriberCount());
        
        outgoingPublisher.subscribe(subscriber);

        Log.logMsg("  Subscribers after subscribe: " + getSubscriberCount());
    }

    public void subscribe(Flow.Subscriber<? super RoutedPacket> subscriber, 
                         ContextPath filterPath) {
        Flow.Subscriber<RoutedPacket> filteredSubscriber = new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscriber.onSubscribe(subscription);
            }
            
            @Override
            public void onNext(RoutedPacket packet) {
                if (packet.getSourcePath().startsWith(filterPath)) {
                    subscriber.onNext(packet);
                }
            }
            
            @Override
            public void onError(Throwable throwable) {
                subscriber.onError(throwable);
            }
            
            @Override
            public void onComplete() {
                subscriber.onComplete();
            }
        };
        
        outgoingPublisher.subscribe(filteredSubscriber);
    }
    
    public int getSubscriberCount() {
        try {
            int count = outgoingPublisher.getNumberOfSubscribers();
            
            // Temporarily log every call to see when it changes
            if (contextPath != null && contextPath.toString().contains("container-service")) {
                Log.logMsg("[FlowProcess:" + contextPath + "] getSubscriberCount() = " + count + 
                    " (thread: " + Thread.currentThread().getName() + ")");
            }
            
            return count;
        } catch (Exception e) {
            Log.logError("[FlowProcess:" + contextPath + "] Error getting subscriber count: " + e);
            return 0;
        }
    }
    
    public boolean hasSubscribers() {
        return getSubscriberCount() > 0;
    }
    
    // ===== INCOMING PACKET SUBSCRIPTION =====
    
    public Flow.Subscriber<RoutedPacket> getSubscriber() {
        return incomingSubscriber;
    }
    
    private class ProcessSubscriber implements Flow.Subscriber<RoutedPacket> {
        private final AtomicLong pending = new AtomicLong(0);
        private final BackpressureStrategy strategy = getBackpressureStrategy();
        
        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            Log.logMsg("[ProcessSubscriber:" + contextPath + "] onSubscribe called");
            
            // Generate unique ID for this subscription
            NoteBytes subscriptionId = NoteUUID.createLocalUUID64();
            
            // Accept multiple subscriptions
            incomingSubscriptions.put(subscriptionId, subscription);
            
            Log.logMsg("[ProcessSubscriber:" + contextPath + "] Subscription ACCEPTED as " + 
                subscriptionId + " (total: " + incomingSubscriptions.size() + ")");
            
            // Request items from this new subscription
            requestMore(subscription);
            
            Log.logMsg("[ProcessSubscriber:" + contextPath + "] Initial request sent to " + 
                subscriptionId);
        }
            
        @Override
        public void onNext(RoutedPacket packet) {
            if (!alive) {
                Log.logMsg("[ProcessSubscriber:" + contextPath + "] Ignoring packet - not alive");
                return;
            }

            // ROUTING FILTER: Only process packets addressed to us or broadcasts (null destination)
            ContextPath destination = packet.getDestinationPath();
            if (destination != null && !destination.equals(contextPath)) {
                // Packet is for someone else, ignore it
                Log.logMsg("[ProcessSubscriber:" + contextPath + "] Ignoring packet from " + 
                    packet.getSourcePath() + " to " + destination + " (not for me)");
                return;
            }
            
            Log.logMsg("[ProcessSubscriber:" + contextPath + "] Processing packet from " + 
                packet.getSourcePath() + " to " + (destination != null ? destination : "broadcast"));
            
            pending.decrementAndGet();
            
            // CHECK IF THIS IS A REPLY FIRST
            if (packet.hasMetadata(ProcessKeys.CORRELATION_ID)) {
                NoteBytes corrId = packet.getMetadata(ProcessKeys.CORRELATION_ID);
                Log.logMsg("[ProcessSubscriber:" + contextPath + "] Packet has correlationId: " + corrId);
                
                // Try to handle as reply - if it was consumed, don't process as message
                CompletableFuture<RoutedPacket> pendingRequest = pendingRequests.remove(corrId);
                if (pendingRequest != null) {
                    Log.logMsg("[ProcessSubscriber:" + contextPath + "] Reply consumed by pending request, skipping handleMessage");
                    pendingRequest.complete(packet);
                    
                    if (shouldRequestMore()) {
                        requestMoreFromAll();
                    }
                    return;  // ← EXIT HERE
                }
                
                // No pending request found, might be an unsolicited message with correlationId
                Log.logMsg("[ProcessSubscriber:" + contextPath + "] No pending request for correlationId, processing as regular message");
            }
            
            // Process as regular message
            handleMessage(packet)
                .exceptionally(ex -> {
                    Log.logError("[ProcessSubscriber:" + contextPath + "] handleMessage error: " + 
                        ex.getMessage());
                    onStreamError(ex);
                    return null;
                });
            
            if (shouldRequestMore()) {
                requestMoreFromAll();
            }
        }
        
        @Override
        public void onError(Throwable throwable) {
            Log.logMsg("[ProcessSubscriber:" + contextPath + "] onError called: " + 
                throwable.getMessage());
            onStreamError(throwable);
        }
        
        @Override
        public void onComplete() {
            Log.logMsg("[ProcessSubscriber:" + contextPath + "] onComplete called");
            // Don't complete the process just because one subscription ended
            // The process stays alive as long as it has other subscriptions
        }

        private void requestMore(Flow.Subscription subscription) {
            long amount = getRequestAmount(strategy);
            pending.addAndGet(amount);
            subscription.request(amount);
        }
        
        private boolean shouldRequestMore() {
            if (strategy == BackpressureStrategy.UNBOUNDED) {
                return false;
            }
            return pending.get() < getThreshold(strategy);
        }
        

        private void requestMoreFromAll() {
            long amount = getRequestAmount(strategy);
            pending.addAndGet(amount * incomingSubscriptions.size());
            
            for (Flow.Subscription subscription : incomingSubscriptions.values()) {
                subscription.request(amount);
            }
        }

        
        
        private long getRequestAmount(BackpressureStrategy strategy) {
            return switch (strategy) {
                case UNBOUNDED -> Long.MAX_VALUE;
                case BUFFERED_1000 -> 1000;
                case BUFFERED_100 -> 100;
                case ONE_AT_A_TIME -> 1;
            };
        }
        
        private long getThreshold(BackpressureStrategy strategy) {
            return switch (strategy) {
                case UNBOUNDED -> Long.MAX_VALUE;
                case BUFFERED_1000 -> 100;
                case BUFFERED_100 -> 10;
                case ONE_AT_A_TIME -> 0;
            };
        }
    }
    
    // ===== REQUEST-REPLY PATTERN =====
    
    public CompletableFuture<RoutedPacket> request(
            ContextPath targetPath,
            Duration timeout, 
            NoteBytesPair... pairs) {
        return request(targetPath, new NoteBytesObject(pairs), timeout);
    }
    
    public CompletableFuture<RoutedPacket> request(
            ContextPath targetPath,
            NoteBytesMap map,
            Duration timeout) {
        return request(targetPath, map.toNoteBytes(), timeout);
    }
    
    public CompletableFuture<RoutedPacket> request(
            ContextPath targetPath,
            NoteBytesObject object,
            Duration timeout) {
        return request(targetPath, object.readOnly(), timeout);
    }
    
    public CompletableFuture<RoutedPacket> request(
            ContextPath targetPath,
            NoteBytesReadOnly payload,
            Duration timeout) {

        NoteBytes correlationId = processId.getNextCorrelationId();
        CompletableFuture<RoutedPacket> future = new CompletableFuture<>();
        
        pendingRequests.put(correlationId, future);
        
        RoutedPacket request = RoutedPacket
            .createDirect(contextPath, targetPath, payload)
            .withMetadata(ProcessKeys.CORRELATION_ID, correlationId)
            .withMetadata(ProcessKeys.REPLY_TO, contextPath.getSegments());
        
        Log.logMsg("[FlowProcess:" + contextPath + "] Emitting request to " + targetPath);

        emit(request);

        
        // Setup timeout
        virtualExecutor.execute(() -> {
            try {
                Thread.sleep(timeout.toMillis());
                CompletableFuture<RoutedPacket> pending = pendingRequests.remove(correlationId);
                if (pending != null && !pending.isDone()) {
                    pending.completeExceptionally(
                        new TimeoutException("Request timed out"));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
        return future;
    }

     public void reply(RoutedPacket originalRequest, NoteBytesPair... payload) {
        reply(originalRequest, new NoteBytesObject(payload));
    }
    
    public void reply(RoutedPacket originalRequest, NoteBytes payload) {
        reply(originalRequest, payload.readOnly());
    }
    
    public void reply(RoutedPacket originalRequest, NoteBytesReadOnly payload) {
        NoteBytes correlationId = originalRequest.getMetadata(ProcessKeys.CORRELATION_ID);
        ContextPath replyTo = originalRequest.getMetadataAsPath(ProcessKeys.REPLY_TO);
        
        if (correlationId == null || replyTo == null) {
            Log.logError("Cannot reply: " + 
                (correlationId == null ? "correlationId is null " : "") +
                (replyTo == null ? "replyTo is null " : "")
            );
            return;
        }
        
        RoutedPacket reply = RoutedPacket
            .createDirect(contextPath, replyTo, payload)
            .withMetadata(ProcessKeys.CORRELATION_ID, correlationId);
        
        emit(reply);
    }
    
    /*private void handleReply(RoutedPacket reply) {
        String correlationId = reply.getMetadataString("correlationId");
        if (correlationId != null) {
            CompletableFuture<RoutedPacket> pending = pendingRequests.remove(correlationId);
            if (pending != null) {
                pending.complete(reply);
            }
        }
    }*/
    
    // ===== CHILD PROCESS MANAGEMENT (Uses interface!) =====
    
    /**
     * Spawn child - name determines path automatically
     * 
     * OLD: spawnChild(child, "worker-1")
     * NEW: Same, but path = this.path + "worker-1"
     * 
     * Child's name must match what you pass here!
     */
    public CompletableFuture<ContextPath> spawnChild(FlowProcess child) {
        if (registry == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Process not initialized"));
        }
        
        return CompletableFuture.supplyAsync(() -> {
            // Interface computes child path = parent path + child name
            return registry.registerChild(contextPath, child);
        }, virtualExecutor);
    }

    public ContextPath registerChild(FlowProcess process) {
        if (registry == null) {
            throw new NullPointerException("Process cannot be null");
        }
        return registry.registerChild(contextPath, process);
    }

    /**
     * CONVENIENCE: Register child under sub-path
     * 
     * Path = this.contextPath + "/" + subPath + "/" + child.getName()
     * 
     * Example: parent at "system", subPath "services" → "system/services/auth"
     */
    public ContextPath registerChildUnder(FlowProcess child, String subPath) {
        if (registry == null) {
            throw new IllegalStateException("You must register the process before adding children");
        }
        
        ContextPath fullPath = this.contextPath.append(subPath).append(child.getName());
        
        return registry.registerProcess(
            child,
            fullPath,
            this.contextPath,  // parent is still THIS process
            registry
        );
    }

    /**
     * Find all processes under a path prefix
     * 
     * Can search intermediate paths that have no process!
     */
    public List<FlowProcess> findByPathPrefix(ContextPath prefix) {
        if (registry == null) {
            return Collections.emptyList();
        }
        
        return registry.findByPathPrefix(prefix);
    }

    /**
     * Find children under sub-path
     * 
     * Example: parent at "system", query "system/services"
     */
    public List<FlowProcess> findChildrenUnder(String subPath) {
        if (registry == null) {
            return Collections.emptyList();
        }
        
        ContextPath searchPath = this.contextPath.append(subPath);
        return registry.findByPathPrefix(searchPath);
    }

    
    /**
     * Start a child process
     * 
     * Uses interface for validation
     */
    public CompletableFuture<Void> startChild(ContextPath childPath) {
        if (registry == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Process not initialized"));
        }
        
        return registry.startProcess(childPath);
    }

    public <T extends FlowProcess> List<T> findChildrenByType(Class<T> type) {
        if (registry == null) {
            throw new IllegalStateException("Process not initialized");
        }
        
        return registry.findChildrenByType(contextPath, type);
    }
    
    
    /**
     * Get a process (read-only)
     * 
     * Uses interface - scoped processes can only access reachable processes
     */
    public FlowProcess getProcess(ContextPath path) {
        if (registry == null) {
            throw new IllegalStateException("Process not initialized");
        }
        
        return registry.getProcess(path);
    }
    
    /**
     * Request stream channel to another process
     * 
     * Uses interface - validates reachability
     */
    public CompletableFuture<StreamChannel> requestStreamChannel(ContextPath target) {
        if (registry == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Process not registered"));
        }

        if(contextPath == null){
            return CompletableFuture.failedFuture(
                new IllegalStateException("Process not fully initialized"));
        }
        
        return registry.requestStreamChannel(contextPath, target);
    }

    public CompletableFuture<Void> startProcess(ContextPath processPath) {
        if (registry == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Process not initialized"));
        }
        
        return registry.startProcess(processPath);
    }

    public void unregisterProcess(ContextPath processPath) {
        if (registry == null) {
            throw new IllegalStateException("Process not initialized");
        }
        
        registry.unregisterProcess(processPath);
    }
    
    /**
     * Get children of this process
     */
    public List<ContextPath> getChildren() {
        if (registry == null) {
            return Collections.emptyList();
        }
        
        return registry.getChildren();
    }

    public FlowProcess getChildProcess(String childName) {
        if (registry == null) {
            throw new IllegalStateException("Process not initialized");
        }
        
        return registry.getChildProcess(contextPath, childName);
    }
    
    // ===== PROTECTED INTERFACE ACCESS =====
    
    /**
     * Get registry interface (for subclasses that need advanced operations)
     * 
     * Protected - only subclasses can access
     * Still goes through interface (not direct service)
     */
    protected ProcessRegistryInterface getRegistry() {
        return registry;
    }
    
    // ===== COMPOSITION PATTERNS =====
    
    public CompletableFuture<RoutedPacket> pipeline(
            NoteBytes input,
            ContextPath... stages) {
        return pipeline(input.readOnly(), stages);
    }
    
    public CompletableFuture<RoutedPacket> pipeline(
            NoteBytesReadOnly input,
            ContextPath... stages) {
        
        CompletableFuture<RoutedPacket> result = CompletableFuture.completedFuture(
            RoutedPacket.create(contextPath, input)
        );
        
        for (ContextPath stage : stages) {
            result = result.thenCompose(packet -> 
                request(stage, packet.getPayload(), Duration.ofSeconds(10)));
        }
        
        return result;
    }
    
    public CompletableFuture<List<RoutedPacket>> scatterGather(
            NoteBytes input,
            Collection<ContextPath> targets,
            Duration timeout) {
        return scatterGather(input.readOnly(), targets, timeout);
    }
    
    public CompletableFuture<List<RoutedPacket>> scatterGather(
            NoteBytesReadOnly input,
            Collection<ContextPath> targets,
            Duration timeout) {
        
        List<CompletableFuture<RoutedPacket>> futures = targets.stream()
            .map(target -> request(target, input, timeout)
                .exceptionally(ex -> null))
            .toList();
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .toList());
    }
    
    public <T> CompletableFuture<T> mapReduce(
            Collection<NoteBytes> inputs,
            ContextPath mapper,
            Function<List<RoutedPacket>, T> reducer,
            Duration timeout) {
        
        List<CompletableFuture<RoutedPacket>> mapFutures = inputs.stream()
            .map(input -> request(mapper, input.readOnly(), timeout))
            .toList();
        
        return CompletableFuture.allOf(mapFutures.toArray(new CompletableFuture[0]))
            .thenApply(v -> mapFutures.stream()
                .map(CompletableFuture::join)
                .toList())
            .thenApply(reducer);
    }
    
    // ===== VIRTUAL THREAD HELPERS =====
    
    public <T> T await(CompletableFuture<T> future) {
        return await(future, Duration.ofSeconds(30));
    }
    
    public <T> T await(CompletableFuture<T> future, Duration timeout) {
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CompletionException(e);
        } catch (ExecutionException e) {
            throw new CompletionException(e.getCause());
        } catch (TimeoutException e) {
            throw new CompletionException(e);
        }
    }
    
    public void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
    
    // ===== LIFECYCLE CONTROL =====
    
    public void kill() {
        killed = true;
        alive = false;
        
        Log.logMsg("[FlowProcess:" + contextPath + "] kill() called");
        
        // Cancel all incoming subscriptions
        for (Flow.Subscription subscription : incomingSubscriptions.values()) {
            try {
                subscription.cancel();
            } catch (Exception e) {
                Log.logError("[FlowProcess:" + contextPath + "] Error canceling subscription: " + e);
            }
        }
        incomingSubscriptions.clear();
        
        pendingRequests.values().forEach(f -> 
            f.completeExceptionally(new CancellationException("Process killed")));
        pendingRequests.clear();
        
        Log.logMsg("  Closing outgoingPublisher (subscribers: " + getSubscriberCount() + ")");
        outgoingPublisher.close();
        completionFuture.complete(null);
    }
    
    public void complete() {
        alive = false;
        endTime = System.currentTimeMillis();
        
        Log.logMsg("[FlowProcess:" + contextPath + "] complete() called");
        Log.logMsg("  Closing outgoingPublisher (subscribers: " + getSubscriberCount() + ")");
        
        outgoingPublisher.close();
        virtualExecutor.shutdown();
        completionFuture.complete(null);
    }
    
    // ===== GETTERS =====
    
    public ContextPath getContextPath() {
        return contextPath;
    }
    
    public FlowProcessId getProcessId() {
        return processId;
    }
    
    public ContextPath getParentPath() {
        return parentPath;
    }
    
    public boolean isAlive() {
        return alive;
    }
    
    public boolean isKilled() {
        return killed;
    }
    
    public ProcessType getProcessType() {
        return processType;
    }
    
    public CompletableFuture<Void> getCompletionFuture() {
        return completionFuture;
    }
    
    public long getUptimeMillis() {
        if (!alive) {
            return endTime - startTime;
        }
        return System.currentTimeMillis() - startTime;
    }
    
    public String getInfo() {
        return String.format(
            "Process{path=%s, type=%s, alive=%s, subscribers=%d, pending=%d, uptime=%dms}",
            contextPath, processType, alive, getSubscriberCount(), 
            pendingRequests.size(), getUptimeMillis()
        );
    }
    
    // ===== UTILITIES =====
    

    private ExecutorService getExecutorForType(ProcessType type) {
        return switch (type) {
            case SOURCE, BIDIRECTIONAL -> Executors.newVirtualThreadPerTaskExecutor();
            case TRANSFORM -> Executors.newWorkStealingPool();
            case SINK -> Executors.newVirtualThreadPerTaskExecutor();
        };
    }
    
    private int getBufferSizeForType(ProcessType type) {
        return switch (type) {
            case SOURCE -> 1000;
            case TRANSFORM -> 256;
            case SINK -> 100;
            case BIDIRECTIONAL -> 256;
        };
    }
    
    // ===== ENUMS =====
    
    public enum ProcessType {
        SOURCE,
        TRANSFORM,
        SINK,
        BIDIRECTIONAL
    }
    
    public enum BackpressureStrategy {
        UNBOUNDED,
        BUFFERED_1000,
        BUFFERED_100,
        ONE_AT_A_TIME
    }
    
    @Override
    public String toString() {
        return getInfo();
    }
}
