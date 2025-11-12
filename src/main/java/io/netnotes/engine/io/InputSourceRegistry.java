package io.netnotes.engine.io;

import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.state.BitFlagStateMachine;
import io.netnotes.engine.state.InputStateFlags;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Flow;

/**
 * InputSourceRegistry - Manages input sources with ContextPath addressing.
 * 
 * Architecture:
 * - ContextPath provides hierarchical organization
 * - SourceId (INTEGER) is the leaf node for daemon protocol compatibility
 * - Each source is a Flow.Publisher (Reactive Streams)
 * - BitFlagStateMachine tracks lifecycle and state
 * - Virtual threads for all async operations
 * 
 * Path Structure:
 *   /daemon/main/keyboard/123  <- sourceId 123 is the leaf
 *   /daemon/main/mouse/456     <- sourceId 456 is the leaf
 *   /window/main/canvas/789    <- sourceId 789 is the leaf
 * 
 * The sourceId is:
 * 1. Generated sequentially (AtomicInteger)
 * 2. Encoded as NoteBytesReadOnly(int) for daemon protocol
 * 3. Used as the final segment in ContextPath
 * 4. Unique system-wide
 */
public class InputSourceRegistry {
    private static final InputSourceRegistry INSTANCE = new InputSourceRegistry();
    
    // ID generation
    private final AtomicInteger nextSourceId = new AtomicInteger(1);
    
    // Primary indexes
    private final ConcurrentHashMap<Integer, InputSourceInfo> sourcesById = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ContextPath, InputSourceInfo> sourcesByPath = new ConcurrentHashMap<>();
    
    // Routing: source path → subscribers
    private final ConcurrentHashMap<ContextPath, List<Flow.Subscriber<RoutedPacket>>> subscribers = 
        new ConcurrentHashMap<>();
    
    // Prefix routing: /daemon/* → subscribers
    private final ConcurrentHashMap<ContextPath, List<Flow.Subscriber<RoutedPacket>>> prefixSubscribers = 
        new ConcurrentHashMap<>();
    
    // Publishers for reactive streams
    private final ConcurrentHashMap<Integer, SubmissionPublisher<RoutedPacket>> publishers = 
        new ConcurrentHashMap<>();
    
    // Virtual thread executor
    private final ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
    
    private InputSourceRegistry() {}
    
    public static InputSourceRegistry getInstance() {
        return INSTANCE;
    }
    
    // ===== REGISTRATION =====
    
    /**
     * Register an input source with automatic sourceId generation
     * 
     * @param basePath Path without sourceId (e.g., /daemon/main/keyboard)
     * @param name Human-readable name
     * @param capabilities What this source can do
     * @return Complete path with sourceId as leaf (e.g., /daemon/main/keyboard/123)
     */
    public ContextPath registerSource(
            ContextPath basePath,
            String name,
            InputSourceCapabilities capabilities) {
        
        // Generate unique sourceId
        int sourceId = nextSourceId.getAndIncrement();
        
        // Create full path with sourceId as leaf
        ContextPath fullPath = basePath.append(String.valueOf(sourceId));
        
        // Create state machine for lifecycle
        BitFlagStateMachine stateMachine = new BitFlagStateMachine(
            "source-" + sourceId,
            InputStateFlags.CONTAINER_ACTIVE
        );
        
        // Setup state transitions
        setupStateTransitions(stateMachine, sourceId);
        
        // Create source info
        InputSourceInfo info = new InputSourceInfo(
            sourceId,
            fullPath,
            name,
            capabilities,
            stateMachine
        );
        
        // Store in indexes
        sourcesById.put(sourceId, info);
        sourcesByPath.put(fullPath, info);
        
        // Create publisher for reactive streams
        SubmissionPublisher<RoutedPacket> publisher = new SubmissionPublisher<>(
            virtualExecutor,
            Flow.defaultBufferSize()
        );
        publishers.put(sourceId, publisher);
        
        // Initialize routing lists
        subscribers.put(fullPath, new CopyOnWriteArrayList<>());
        
        System.out.println("Registered input source: " + name + 
                         " (id=" + sourceId + ", path=" + fullPath + ")");
        
        return fullPath;
    }
    
    /**
     * Setup state machine transitions for input source lifecycle
     */
    private void setupStateTransitions(BitFlagStateMachine sm, int sourceId) {
        // Log state changes
        sm.onStateChanged((oldState, newState) -> {
            System.out.println("Source " + sourceId + " state: " + 
                             sm.getStateString(null, null));
        });
        
        // When source becomes active, ensure container is enabled
        sm.onStateAdded(InputStateFlags.CONTAINER_ACTIVE, (old, now, bit) -> {
            if (!sm.hasFlag(InputStateFlags.CONTAINER_ENABLED)) {
                sm.setFlag(InputStateFlags.CONTAINER_ENABLED);
            }
        });
        
        // When killed, mark as inactive
        sm.onStateAdded(InputStateFlags.PROCESS_KILLED, (old, now, bit) -> {
            sm.clearFlag(InputStateFlags.CONTAINER_ACTIVE);
            sm.clearFlag(InputStateFlags.CONTAINER_ENABLED);
        });
    }
    
    /**
     * Unregister an input source
     */
    public void unregisterSource(ContextPath fullPath) {
        InputSourceInfo info = sourcesByPath.remove(fullPath);
        if (info == null) return;
        
        // Mark as killed
        info.stateMachine.setFlag(InputStateFlags.PROCESS_KILLED);
        
        // Remove from indexes
        sourcesById.remove(info.sourceId);
        
        // Close publisher
        SubmissionPublisher<RoutedPacket> publisher = publishers.remove(info.sourceId);
        if (publisher != null) {
            publisher.close();
        }
        
        // Remove subscribers
        subscribers.remove(fullPath);
        
        System.out.println("Unregistered source: " + info.name + " at " + fullPath);
    }
    
    /**
     * Unregister by sourceId
     */
    public void unregisterSource(int sourceId) {
        InputSourceInfo info = sourcesById.get(sourceId);
        if (info != null) {
            unregisterSource(info.fullPath);
        }
    }
    
    // ===== SUBSCRIPTION (REACTIVE STREAMS) =====
    
    /**
     * Subscribe to packets from a specific source
     */
    public void subscribe(ContextPath sourcePath, Flow.Subscriber<RoutedPacket> subscriber) {
        InputSourceInfo info = sourcesByPath.get(sourcePath);
        if (info == null) {
            throw new IllegalArgumentException("Source not found: " + sourcePath);
        }
        
        // Add to subscriber list for routing
        subscribers.computeIfAbsent(sourcePath, k -> new CopyOnWriteArrayList<>())
                  .add(subscriber);
        
        // Subscribe to publisher
        SubmissionPublisher<RoutedPacket> publisher = publishers.get(info.sourceId);
        if (publisher != null) {
            publisher.subscribe(subscriber);
            System.out.println("Subscribed to " + sourcePath);
        }
    }
    
    /**
     * Subscribe to all sources under a path prefix
     * Example: subscribe to /daemon/main/* catches all devices
     */
    public void subscribePrefix(ContextPath prefix, Flow.Subscriber<RoutedPacket> subscriber) {
        prefixSubscribers.computeIfAbsent(prefix, k -> new CopyOnWriteArrayList<>())
                        .add(subscriber);
        
        // Subscribe to all existing sources under this prefix
        sourcesByPath.keySet().stream()
            .filter(path -> path.startsWith(prefix))
            .forEach(path -> subscribe(path, subscriber));
        
        System.out.println("Subscribed to prefix: " + prefix + "/*");
    }
    
    /**
     * Unsubscribe from a source
     */
    public void unsubscribe(ContextPath sourcePath, Flow.Subscriber<RoutedPacket> subscriber) {
        List<Flow.Subscriber<RoutedPacket>> subs = subscribers.get(sourcePath);
        if (subs != null) {
            subs.remove(subscriber);
        }
    }
    
    // ===== PACKET EMISSION =====
    
    /**
     * Emit a packet from an input source (called by the source itself)
     * 
     * @param sourceId The source emitting the packet
     * @param payload The packet payload
     */
    public void emit(int sourceId, NoteBytesReadOnly payload) {
        InputSourceInfo info = sourcesById.get(sourceId);
        if (info == null) {
            System.err.println("Cannot emit: unknown sourceId " + sourceId);
            return;
        }
        
        // Check if source is active
        if (!info.stateMachine.hasFlag(InputStateFlags.CONTAINER_ACTIVE)) {
            System.err.println("Cannot emit: source " + sourceId + " not active");
            return;
        }
        
        // Create routed packet
        RoutedPacket packet = RoutedPacket.create(info.fullPath, payload);
        
        // Publish to all subscribers
        SubmissionPublisher<RoutedPacket> publisher = publishers.get(sourceId);
        if (publisher != null) {
            try {
                int lag = publisher.submit(packet);
                if (lag > 100) {
                    System.out.println("WARNING: Source " + sourceId + " lagging (buffer: " + lag + ")");
                }
                
                // Update statistics
                info.statistics.incrementPacketCount();
                
            } catch (Exception e) {
                System.err.println("Error emitting packet from " + sourceId + ": " + e.getMessage());
            }
        }
    }
    
    /**
     * Emit packet from ContextPath (finds sourceId from path leaf)
     */
    public void emit(ContextPath sourcePath, NoteBytesReadOnly payload) {
        InputSourceInfo info = sourcesByPath.get(sourcePath);
        if (info != null) {
            emit(info.sourceId, payload);
        } else {
            System.err.println("Cannot emit: source not found at " + sourcePath);
        }
    }
    
    // ===== QUERIES =====
    
    /**
     * Get source info by full path
     */
    public InputSourceInfo getSourceInfo(ContextPath fullPath) {
        return sourcesByPath.get(fullPath);
    }
    
    /**
     * Get source info by sourceId
     */
    public InputSourceInfo getSourceInfo(int sourceId) {
        return sourcesById.get(sourceId);
    }
    
    /**
     * Extract sourceId from ContextPath (the leaf node)
     */
    public int getSourceIdFromPath(ContextPath fullPath) {
        String leafName = fullPath.name();
        try {
            return Integer.parseInt(leafName);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Path leaf is not a sourceId: " + leafName);
        }
    }
    
    /**
     * Get NoteBytesReadOnly sourceId (for daemon protocol)
     */
    public NoteBytesReadOnly getSourceIdAsNoteBytes(ContextPath fullPath) {
        int sourceId = getSourceIdFromPath(fullPath);
        return new NoteBytesReadOnly(sourceId);
    }
    
    /**
     * Find all sources under a path prefix
     */
    public List<InputSourceInfo> findSourcesUnder(ContextPath prefix) {
        return sourcesByPath.entrySet().stream()
            .filter(e -> e.getKey().startsWith(prefix))
            .map(Map.Entry::getValue)
            .toList();
    }
    
    /**
     * Find sources by capability
     */
    public List<InputSourceInfo> findSourcesByCapability(
            java.util.function.Predicate<InputSourceCapabilities> predicate) {
        return sourcesById.values().stream()
            .filter(info -> predicate.test(info.capabilities))
            .toList();
    }
    
    /**
     * Check if source exists
     */
    public boolean exists(ContextPath fullPath) {
        return sourcesByPath.containsKey(fullPath);
    }
    
    public boolean exists(int sourceId) {
        return sourcesById.containsKey(sourceId);
    }
    
    /**
     * Get all registered sources
     */
    public Collection<InputSourceInfo> getAllSources() {
        return Collections.unmodifiableCollection(sourcesById.values());
    }
    
    /**
     * Get source count
     */
    public int getSourceCount() {
        return sourcesById.size();
    }
    
    // ===== STATE MANAGEMENT =====
    
    /**
     * Set state flag for a source
     */
    public void setSourceState(ContextPath fullPath, long stateFlag) {
        InputSourceInfo info = sourcesByPath.get(fullPath);
        if (info != null) {
            info.stateMachine.setFlag(stateFlag);
        }
    }
    
    public void setSourceState(int sourceId, long stateFlag) {
        InputSourceInfo info = sourcesById.get(sourceId);
        if (info != null) {
            info.stateMachine.setFlag(stateFlag);
        }
    }
    
    /**
     * Clear state flag
     */
    public void clearSourceState(ContextPath fullPath, long stateFlag) {
        InputSourceInfo info = sourcesByPath.get(fullPath);
        if (info != null) {
            info.stateMachine.clearFlag(stateFlag);
        }
    }
    
    /**
     * Check state flag
     */
    public boolean hasSourceState(ContextPath fullPath, long stateFlag) {
        InputSourceInfo info = sourcesByPath.get(fullPath);
        return info != null && info.stateMachine.hasFlag(stateFlag);
    }
    
    /**
     * Get state machine for advanced control
     */
    public BitFlagStateMachine getStateMachine(ContextPath fullPath) {
        InputSourceInfo info = sourcesByPath.get(fullPath);
        return info != null ? info.stateMachine : null;
    }
    
    // ===== LIFECYCLE CONTROL =====
    
    /**
     * Pause a source (stop emitting packets)
     */
    public CompletableFuture<Void> pauseSource(ContextPath fullPath) {
        return CompletableFuture.runAsync(() -> {
            InputSourceInfo info = sourcesByPath.get(fullPath);
            if (info != null) {
                info.stateMachine.clearFlag(InputStateFlags.CONTAINER_ACTIVE);
                System.out.println("Paused source: " + fullPath);
            }
        }, virtualExecutor);
    }
    
    /**
     * Resume a source
     */
    public CompletableFuture<Void> resumeSource(ContextPath fullPath) {
        return CompletableFuture.runAsync(() -> {
            InputSourceInfo info = sourcesByPath.get(fullPath);
            if (info != null) {
                info.stateMachine.setFlag(InputStateFlags.CONTAINER_ACTIVE);
                System.out.println("Resumed source: " + fullPath);
            }
        }, virtualExecutor);
    }
    
    /**
     * Kill a source permanently
     */
    public CompletableFuture<Void> killSource(ContextPath fullPath) {
        return CompletableFuture.runAsync(() -> {
            unregisterSource(fullPath);
        }, virtualExecutor);
    }
    
    // ===== STATISTICS =====
    
    public String getSummary() {
        int total = sourcesById.size();
        int active = (int) sourcesById.values().stream()
            .filter(info -> info.stateMachine.hasFlag(InputStateFlags.CONTAINER_ACTIVE))
            .count();
        int paused = total - active;
        
        long totalPackets = sourcesById.values().stream()
            .mapToLong(info -> info.statistics.getPacketCount())
            .sum();
        
        return String.format(
            "InputSources: %d total, %d active, %d paused | Packets: %d total",
            total, active, paused, totalPackets
        );
    }
    
    /**
     * Get publisher stats for monitoring
     */
    public PublisherStats getPublisherStats(ContextPath fullPath) {
        InputSourceInfo info = sourcesByPath.get(fullPath);
        if (info == null) return null;
        
        SubmissionPublisher<RoutedPacket> publisher = publishers.get(info.sourceId);
        if (publisher == null) return null;
        
        return new PublisherStats(
            fullPath,
            publisher.getNumberOfSubscribers(),
            publisher.estimateMaximumLag(),
            publisher.isClosed()
        );
    }
    
    /**
     * Get detailed stats for a source
     */
    public Map<String, Object> getSourceStats(ContextPath fullPath) {
        InputSourceInfo info = sourcesByPath.get(fullPath);
        if (info == null) return null;
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("sourceId", info.sourceId);
        stats.put("path", fullPath.toString());
        stats.put("name", info.name);
        stats.put("active", info.stateMachine.hasFlag(InputStateFlags.CONTAINER_ACTIVE));
        stats.put("uptime_ms", info.getUptimeMillis());
        stats.put("packets", info.statistics.getPacketCount());
        stats.put("subscribers", publishers.get(info.sourceId).getNumberOfSubscribers());
        
        return stats;
    }
    
    // ===== SHUTDOWN =====
    
    public void shutdown() {
        // Close all publishers
        publishers.values().forEach(SubmissionPublisher::close);
        
        // Clear all data
        publishers.clear();
        sourcesById.clear();
        sourcesByPath.clear();
        subscribers.clear();
        prefixSubscribers.clear();
        
        virtualExecutor.shutdown();
        
        System.out.println("InputSourceRegistry shutdown complete");
    }
    
    // ===== DATA STRUCTURES =====
    
    /**
     * InputSourceInfo - Complete information about an input source
     */
    public static class InputSourceInfo {
        public final int sourceId;
        public final ContextPath fullPath;  // Includes sourceId as leaf
        public final String name;
        public final InputSourceCapabilities capabilities;
        public final BitFlagStateMachine stateMachine;
        public final SourceStatistics statistics;
        public final long registrationTime;
        
        public InputSourceInfo(
                int sourceId,
                ContextPath fullPath,
                String name,
                InputSourceCapabilities capabilities,
                BitFlagStateMachine stateMachine) {
            
            this.sourceId = sourceId;
            this.fullPath = fullPath;
            this.name = name;
            this.capabilities = capabilities;
            this.stateMachine = stateMachine;
            this.statistics = new SourceStatistics();
            this.registrationTime = System.currentTimeMillis();
        }
        
        /**
         * Get sourceId as NoteBytesReadOnly (for daemon protocol)
         */
        public NoteBytesReadOnly getSourceIdAsNoteBytes() {
            return new NoteBytesReadOnly(sourceId);
        }
        
        /**
         * Get base path (without sourceId leaf)
         */
        public ContextPath getBasePath() {
            return fullPath.parent();
        }
        
        public long getUptimeMillis() {
            return System.currentTimeMillis() - registrationTime;
        }
        
        @Override
        public String toString() {
            return String.format(
                "InputSource{id=%d, name='%s', path=%s, active=%s}",
                sourceId, name, fullPath,
                stateMachine.hasFlag(InputStateFlags.CONTAINER_ACTIVE)
            );
        }
    }
    
    /**
     * Publisher statistics
     */
    public record PublisherStats(
        ContextPath path,
        int subscriberCount,
        long maxLag,
        boolean closed
    ) {}
}