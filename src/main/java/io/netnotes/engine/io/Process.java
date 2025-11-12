package io.netnotes.engine.io;

import io.netnotes.engine.noteBytes.NoteBytesReadOnly;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Process - Universal base class for ALL system components.
 * 
 * Unified Design:
 * - Input sources (keyboard, mouse) are processes that PRODUCE packets
 * - Compute processes TRANSFORM packets (input → output)
 * - Sink processes CONSUME packets (logger, renderer)
 * - All use Reactive Streams for backpressure and flow control
 * 
 * Process Types:
 * - SOURCE: Generates packets (keyboard, mouse, network, timers)
 * - TRANSFORM: Receives and sends (workers, filters, routers)
 * - SINK: Only receives (loggers, file writers, UI renderers)
 * - BIDIRECTIONAL: Both sends and receives (databases, network connections)
 * 
 * Key Features:
 * - CompletableFuture-based async API
 * - Request-reply pattern support
 * - Virtual thread-friendly
 * - Composable (pipeline, map-reduce, scatter-gather)
 * - Automatic backpressure management
 */
public abstract class Process implements Flow.Publisher<RoutedPacket> {
    
    // ===== IDENTITY =====
    protected ContextPath contextPath;
    protected ProcessId processId;  // Derived from contextPath
    protected ContextPath parentPath;
    
    // ===== REGISTRY =====
    protected ProcessRegistry registry;
    
    // ===== LIFECYCLE =====
    private volatile boolean alive = true;
    private volatile boolean killed = false;
    private volatile long startTime;
    private volatile long endTime;
    private final CompletableFuture<Void> completionFuture = new CompletableFuture<>();
    
    // ===== REACTIVE STREAMS =====
    
    // Incoming: We are a SUBSCRIBER (receive packets)
    private final ProcessSubscriber incomingSubscriber = new ProcessSubscriber();
    private Flow.Subscription incomingSubscription;
    
    // Outgoing: We are a PUBLISHER (send packets)
    private final SubmissionPublisher<RoutedPacket> outgoingPublisher;
    private final ExecutorService publisherExecutor;
    
    // ===== ASYNC INFRASTRUCTURE =====
    private final ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final ConcurrentHashMap<String, CompletableFuture<RoutedPacket>> pendingRequests = 
        new ConcurrentHashMap<>();
    
    // ===== PROCESS TYPE =====
    private final ProcessType processType;
    
    /**
     * Constructor - specify process type
     */
    public Process(ProcessType type) {
        this.processType = type;
        this.publisherExecutor = getExecutorForType(type);
        this.outgoingPublisher = new SubmissionPublisher<>(
            publisherExecutor,
            getBufferSizeForType(type)
        );
    }
    
    // ===== CORE LIFECYCLE METHODS =====
    
    /**
     * Main execution method - override for long-running processes.
     * SOURCE processes typically run forever, generating packets.
     * TRANSFORM/SINK processes can return immediately.
     */
    public CompletableFuture<Void> run() {
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Handle incoming packet - MAIN METHOD FOR TRANSFORM/SINK PROCESSES
     * SOURCE processes typically don't override this.
     */
    protected CompletableFuture<Void> handleMessage(RoutedPacket packet) {
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Configure backpressure for INCOMING messages
     */
    protected BackpressureStrategy getBackpressureStrategy() {
        return switch (processType) {
            case SOURCE -> BackpressureStrategy.UNBOUNDED; // Sources generate, don't receive
            case TRANSFORM -> BackpressureStrategy.BUFFERED_100;
            case SINK -> BackpressureStrategy.BUFFERED_100;
            case BIDIRECTIONAL -> BackpressureStrategy.BUFFERED_100;
        };
    }
    
    /**
     * Lifecycle hooks
     */
    protected void onStart() {}
    protected void onStop() {}
    protected void onStreamError(Throwable error) {
        System.err.println("Process " + contextPath + " error: " + error.getMessage());
        error.printStackTrace();
    }
    protected void onStreamComplete() {}
    
    // ===== OUTGOING PACKET EMISSION (PUBLISHER) =====
    
    /**
     * Emit a packet to all subscribers.
     * Use this in SOURCE processes to generate events.
     * Use this in TRANSFORM processes to send output.
     */
    protected void emit(RoutedPacket packet) {
        if (!alive) return;
        
        try {
            int lag = outgoingPublisher.submit(packet);
            if (lag > 100) {
                System.out.println("WARNING: " + contextPath + " downstream lagging (buffer: " + lag + ")");
            }
        } catch (Exception e) {
            System.err.println("Error emitting packet: " + e.getMessage());
        }
    }
    
    /**
     * Emit with automatic source path stamping
     */
    protected void emit(NoteBytesReadOnly payload) {
        emit(RoutedPacket.create(contextPath, payload));
    }
    
    /**
     * Emit to specific destination
     */
    protected void emitTo(ContextPath destination, NoteBytesReadOnly payload) {
        emit(RoutedPacket.createDirect(contextPath, destination, payload));
    }
    
    // ===== PUBLISHER INTERFACE IMPLEMENTATION =====
    
    @Override
    public void subscribe(Flow.Subscriber<? super RoutedPacket> subscriber) {
        outgoingPublisher.subscribe(subscriber);
    }
    
    /**
     * Get number of downstream subscribers
     */
    public int getSubscriberCount() {
        return outgoingPublisher.getNumberOfSubscribers();
    }
    
    /**
     * Check if anyone is listening
     */
    public boolean hasSubscribers() {
        return getSubscriberCount() > 0;
    }
    
    // ===== INCOMING PACKET SUBSCRIPTION (SUBSCRIBER) =====
    
    /**
     * Get subscriber for incoming packets.
     * Used by registry to wire up message flow.
     */
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
            
            // Check if this is a reply to pending request
            if (packet.hasMetadata("correlationId")) {
                handleReply(packet);
            }
            
            // Process message asynchronously
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
    
    /**
     * Send request and wait for reply
     */
    protected CompletableFuture<RoutedPacket> request(
            ContextPath targetPath,
            NoteBytesReadOnly payload,
            Duration timeout) {
        
        String correlationId = generateCorrelationId();
        CompletableFuture<RoutedPacket> future = new CompletableFuture<>();
        
        pendingRequests.put(correlationId, future);
        
        // Create request packet
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
                    pending.completeExceptionally(new TimeoutException("Request timed out"));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
        return future;
    }
    
    /**
     * Reply to a request
     */
    protected void reply(RoutedPacket originalRequest, NoteBytesReadOnly payload) {
        String correlationId = originalRequest.getMetadataString("correlationId");
        String replyTo = originalRequest.getMetadataString("replyTo");
        
        if (correlationId == null || replyTo == null) {
            System.err.println("Cannot reply: missing correlation metadata");
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
    
    // ===== COMPOSITION PATTERNS =====
    
    /**
     * Pipeline: chain multiple processes
     */
    protected CompletableFuture<RoutedPacket> pipeline(
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
    
    /**
     * Scatter-gather: parallel fan-out then collect
     */
    protected CompletableFuture<List<RoutedPacket>> scatterGather(
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
    
    /**
     * Map-reduce: transform in parallel, then reduce
     */
    protected <T> CompletableFuture<T> mapReduce(
            Collection<NoteBytesReadOnly> inputs,
            ContextPath mapper,
            Function<List<RoutedPacket>, T> reducer,
            Duration timeout) {
        
        List<CompletableFuture<RoutedPacket>> mapFutures = inputs.stream()
            .map(input -> request(mapper, input, timeout))
            .toList();
        
        return CompletableFuture.allOf(mapFutures.toArray(new CompletableFuture[0]))
            .thenApply(v -> mapFutures.stream()
                .map(CompletableFuture::join)
                .toList())
            .thenApply(reducer);
    }
    
    // ===== VIRTUAL THREAD HELPERS =====
    
    /**
     * Block safely (virtual threads make this cheap)
     */
    protected <T> T await(CompletableFuture<T> future) {
        return await(future, Duration.ofSeconds(30));
    }
    
    protected <T> T await(CompletableFuture<T> future, Duration timeout) {
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
    
    protected void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
    
    // ===== CHILD PROCESS MANAGEMENT =====
    
    protected CompletableFuture<ContextPath> spawnChild(
            Process child,
            String childName) {
        
        return CompletableFuture.supplyAsync(() -> {
            ContextPath childPath = contextPath.append(childName);
            return registry.registerProcess(child, childPath, this.contextPath);
        }, virtualExecutor);
    }
    
    // ===== LIFECYCLE CONTROL =====
    
    public void initialize(ContextPath path, ContextPath parentPath, ProcessRegistry registry) {
        this.contextPath = path;
        this.processId = new ProcessId(path.hashCode());
        this.parentPath = parentPath;
        this.registry = registry;
        this.startTime = System.currentTimeMillis();
    }
    
    public void kill() {
        killed = true;
        alive = false;
        
        // Cancel pending requests
        pendingRequests.values().forEach(f -> 
            f.completeExceptionally(new CancellationException("Process killed")));
        pendingRequests.clear();
        
        // Close publisher
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
    
    public ProcessId getProcessId() {
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
    
    private String generateCorrelationId() {
        return processId.asInt() + "-" + System.nanoTime();
    }
    
    private ExecutorService getExecutorForType(ProcessType type) {
        return switch (type) {
            case SOURCE, BIDIRECTIONAL -> Executors.newVirtualThreadPerTaskExecutor();
            case TRANSFORM -> Executors.newWorkStealingPool();
            case SINK -> Executors.newVirtualThreadPerTaskExecutor();
        };
    }
    
    private int getBufferSizeForType(ProcessType type) {
        return switch (type) {
            case SOURCE -> 1000;  // Sources generate lots of events
            case TRANSFORM -> 256;
            case SINK -> 100;     // Sinks don't need large buffers
            case BIDIRECTIONAL -> 256;
        };
    }
    
    // ===== ENUMS =====
    
    public enum ProcessType {
        /**
         * SOURCE: Generates packets (keyboard, mouse, timers, network input)
         * - Typically runs forever in run()
         * - Calls emit() to send packets
         * - Usually doesn't receive packets
         */
        SOURCE,
        
        /**
         * TRANSFORM: Receives packets, transforms them, emits new packets
         * - Overrides handleMessage()
         * - Calls emit() to send results
         * - Examples: filters, parsers, routers
         */
        TRANSFORM,
        
        /**
         * SINK: Only receives packets (loggers, file writers, UI renderers)
         * - Overrides handleMessage()
         * - Does NOT emit packets
         * - Terminal nodes in the flow
         */
        SINK,
        
        /**
         * BIDIRECTIONAL: Both generates and receives (databases, connections)
         * - Overrides both run() and handleMessage()
         * - Can emit() at any time
         * - Examples: database connections, network sockets
         */
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