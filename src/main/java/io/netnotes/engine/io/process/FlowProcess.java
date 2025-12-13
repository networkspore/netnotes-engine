package io.netnotes.engine.io.process;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.noteBytes.*;
import io.netnotes.engine.noteBytes.collections.*;
import io.netnotes.engine.utils.LoggingHelpers.Log;

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
    private Flow.Subscription incomingSubscription;
    private final SubmissionPublisher<RoutedPacket> outgoingPublisher;
    private final ExecutorService publisherExecutor;
    
    // ===== ASYNC INFRASTRUCTURE =====
    private final ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final ConcurrentHashMap<String, CompletableFuture<RoutedPacket>> pendingRequests = 
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
        if (!alive) return;
        
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
        emit(RoutedPacket.createDirect(contextPath, destination, payload));
    }
    
    // ===== PUBLISHER INTERFACE =====
    
    @Override
    public void subscribe(Flow.Subscriber<? super RoutedPacket> subscriber) {
        outgoingPublisher.subscribe(subscriber);
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
        return outgoingPublisher.getNumberOfSubscribers();
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
            if (incomingSubscription != null) {
                subscription.cancel();
                return;
            }
            
            incomingSubscription = subscription;
            requestMore();
        }
        
        @Override
        public void onNext(RoutedPacket packet) {
            if (!alive) return;
            
            pending.decrementAndGet();
            
            if (packet.hasMetadata("correlationId")) {
                handleReply(packet);
            }
            
            handleMessage(packet)
                .exceptionally(ex -> {
                    onError(ex);
                    return null;
                });
            
            if (shouldRequestMore()) {
                requestMore();
            }
        }
        
        @Override
        public void onError(Throwable throwable) {
            onStreamError(throwable);
        }
        
        @Override
        public void onComplete() {
            onStreamComplete();
            complete();
        }
        
        private void requestMore() {
            if (incomingSubscription == null) return;
            
            long amount = getRequestAmount(strategy);
            pending.addAndGet(amount);
            incomingSubscription.request(amount);
        }
        
        private boolean shouldRequestMore() {
            if (strategy == BackpressureStrategy.UNBOUNDED) {
                return false;
            }
            return pending.get() < getThreshold(strategy);
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
        
        String correlationId = processId.getNextCorrelationId();
        CompletableFuture<RoutedPacket> future = new CompletableFuture<>();
        
        pendingRequests.put(correlationId, future);
        
        RoutedPacket request = RoutedPacket
            .createDirect(contextPath, targetPath, payload)
            .withMetadata("correlationId", correlationId)
            .withMetadata("replyTo", contextPath.toString());
        
            

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
        String correlationId = originalRequest.getMetadataString("correlationId");
        String replyTo = originalRequest.getMetadataString("replyTo");
        
        if (correlationId == null || replyTo == null) {
            Log.logError("Cannot reply: missing correlation metadata");
            return;
        }
        
        RoutedPacket reply = RoutedPacket
            .createDirect(contextPath, ContextPath.parse(replyTo), payload)
            .withMetadata("correlationId", correlationId);
        
        emit(reply);
    }
    
    private void handleReply(RoutedPacket reply) {
        String correlationId = reply.getMetadataString("correlationId");
        if (correlationId != null) {
            CompletableFuture<RoutedPacket> pending = pendingRequests.remove(correlationId);
            if (pending != null) {
                pending.complete(reply);
            }
        }
    }
    
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
                new IllegalStateException("Process not initialized"));
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
        
        pendingRequests.values().forEach(f -> 
            f.completeExceptionally(new CancellationException("Process killed")));
        pendingRequests.clear();
        
        outgoingPublisher.close();
        completionFuture.complete(null);
    }
    
    public void complete() {
        alive = false;
        endTime = System.currentTimeMillis();
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
