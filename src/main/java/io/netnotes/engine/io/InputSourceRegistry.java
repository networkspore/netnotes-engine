package io.netnotes.engine.io;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import io.netnotes.engine.noteBytes.NoteBytesReadOnly;

/**
 * InputSourceRegistry - Central hub for routing input source packets.
 * 
 * Architecture:
 * - Generates sourceIds as INTEGER NoteBytesReadOnly
 * - Uses ContextPath for hierarchical organization and registry chaining
 * - Supports both unicast (default) and broadcast routing
 * - Provides bidirectional communication channels
 * - Thread-safe for concurrent access
 * 
 * Identity System:
 * - sourceId (NoteBytesReadOnly INTEGER): Unique numeric identifier for efficient routing
 * - ContextPath: Hierarchical path for organization, filtering, and registry chaining
 *   Example: /daemon/usb/keyboard/0 or /window/main/canvas
 * 
 * Registry Chaining:
 * - Parent registries can route to child registries based on ContextPath prefixes
 * - Example: /daemon/* routes to DaemonRegistry, /window/* routes to WindowRegistry
 */
public class InputSourceRegistry {
    private static final InputSourceRegistry INSTANCE = new InputSourceRegistry();
    
    private final AtomicInteger nextSourceId = new AtomicInteger(1);
    
    // Primary indexes
    private final ConcurrentHashMap<NoteBytesReadOnly, SourceInfo> sourceById = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ContextPath, SourceInfo> sourceByPath = new ConcurrentHashMap<>();
    
    // Routing tables
    private final ConcurrentHashMap<NoteBytesReadOnly, List<PacketDestination>> routingTable = new ConcurrentHashMap<>();
    
    // Reply channels (destination → source communication)
    private final ConcurrentHashMap<NoteBytesReadOnly, Consumer<NoteBytesReadOnly>> sourceReplyChannels = new ConcurrentHashMap<>();
    
    // Registry chaining support
    private final ConcurrentHashMap<ContextPath, InputSourceRegistry> childRegistries = new ConcurrentHashMap<>();
    private InputSourceRegistry parentRegistry = null;
    private ContextPath registryPath = ContextPath.ROOT;
    
    // Path-based routing (for hierarchical packet filtering)
    private final ConcurrentHashMap<ContextPath, List<PacketDestination>> pathBasedRouting = new ConcurrentHashMap<>();
    
    private InputSourceRegistry() {
        // Singleton
    }
    
    public static InputSourceRegistry getInstance() {
        return INSTANCE;
    }
    
    /**
     * Create a child registry for a specific path prefix.
     * Useful for isolating different subsystems (daemon, window, overlay, etc.)
     */
    public InputSourceRegistry createChildRegistry(ContextPath pathPrefix) {
        InputSourceRegistry child = new InputSourceRegistry();
        child.parentRegistry = this;
        child.registryPath = pathPrefix;
        childRegistries.put(pathPrefix, child);
        return child;
    }
    
    /**
     * Register a new source with both sourceId and ContextPath.
     * 
     * @param name Human-readable name
     * @param capabilities What this source can do
     * @param contextPath Hierarchical path for organization
     * @return Generated sourceId (INTEGER NoteBytesReadOnly)
     */
    public NoteBytesReadOnly registerSource(
            String name,
            InputSourceCapabilities capabilities,
            ContextPath contextPath) {
        
        // Generate INTEGER sourceId
        int id = nextSourceId.getAndIncrement();
        NoteBytesReadOnly sourceId = new NoteBytesReadOnly(id);
        
        // Create source info
        SourceInfo info = new SourceInfo(sourceId, name, capabilities, contextPath);
        
        // Set initial state
        info.setLocalFlag(SourceState.REGISTERED_BIT, true);
        info.setLocalFlag(SourceState.INITIALIZING_BIT, true);
        
        // Index by both sourceId and path
        sourceById.put(sourceId, info);
        sourceByPath.put(contextPath, info);
        
        // Initialize empty routing table entry
        routingTable.put(sourceId, new ArrayList<>());
        
        System.out.println("Registered source: " + name + 
                         " (id=" + id + ", path=" + contextPath + ")");
        
        return sourceId;
    }
    
    /**
     * Unregister a source by sourceId
     */
    public void unregisterSource(NoteBytesReadOnly sourceId) {
        SourceInfo info = sourceById.remove(sourceId);
        if (info != null) {
            sourceByPath.remove(info.contextPath);
            routingTable.remove(sourceId);
            sourceReplyChannels.remove(sourceId);
            
            System.out.println("Unregistered source: " + info.name + 
                             " (id=" + sourceId.getAsInt() + ")");
        }
    }
    
    /**
     * Unregister a source by ContextPath
     */
    public void unregisterSource(ContextPath contextPath) {
        SourceInfo info = sourceByPath.remove(contextPath);
        if (info != null) {
            unregisterSource(info.sourceId);
        }
    }
    
    /**
     * Add a destination for a specific source (unicast by default)
     */
    public void addDestination(NoteBytesReadOnly sourceId, PacketDestination destination) {
        routingTable.computeIfAbsent(sourceId, k -> new ArrayList<>())
                    .add(destination);
    }
    
    /**
     * Add a destination for a source identified by ContextPath
     */
    public void addDestination(ContextPath contextPath, PacketDestination destination) {
        SourceInfo info = sourceByPath.get(contextPath);
        if (info != null) {
            addDestination(info.sourceId, destination);
        } else {
            throw new IllegalArgumentException("No source found at path: " + contextPath);
        }
    }
    
    /**
     * Add a path-based destination that receives packets from all sources 
     * under a specific path prefix.
     * 
     * Example: Listen to all sources under /daemon/usb/*
     */
    public void addPathDestination(ContextPath pathPrefix, PacketDestination destination) {
        pathBasedRouting.computeIfAbsent(pathPrefix, k -> new ArrayList<>())
                       .add(destination);
    }
    
    /**
     * Remove a path-based destination
     */
    public void removePathDestination(ContextPath pathPrefix, PacketDestination destination) {
        List<PacketDestination> destinations = pathBasedRouting.get(pathPrefix);
        if (destinations != null) {
            destinations.remove(destination);
            if (destinations.isEmpty()) {
                pathBasedRouting.remove(pathPrefix);
            }
        }
    }
    
    /**
     * Enable broadcasting: packets fan out to all registered destinations
     */
    public void enableBroadcast(NoteBytesReadOnly sourceId) {
        SourceInfo info = sourceById.get(sourceId);
        if (info != null) {
            info.setLocalFlag(SourceState.BROADCASTING_BIT, true);
        }
    }
    
    /**
     * Disable broadcasting: packets go to first destination only (unicast)
     */
    public void disableBroadcast(NoteBytesReadOnly sourceId) {
        SourceInfo info = sourceById.get(sourceId);
        if (info != null) {
            info.setLocalFlag(SourceState.BROADCASTING_BIT, false);
        }
    }
    
    /**
     * Route a packet to its destination(s).
     * 
     * Routing logic:
     * 1. Check if source is in this registry
     * 2. If not, try child registries based on ContextPath
     * 3. Apply unicast vs broadcast logic
     * 4. Apply path-based routing (hierarchical listeners)
     */
    public void routePacket(RoutedPacket packet) {
        NoteBytesReadOnly sourceId = packet.getSourceId();
        SourceInfo info = sourceById.get(sourceId);
        
        if (info == null) {
            // Try to route to child registry
            routeToChildRegistry(packet);
            return;
        }
        
        // Get direct destinations for this source
        List<PacketDestination> destinations = routingTable.get(sourceId);
        
        if (destinations == null || destinations.isEmpty()) {
            // No direct destinations, but check path-based routing
            routeToPathDestinations(info, packet);
            return;
        }
        
        boolean broadcasting = info.hasLocalFlag(SourceState.BROADCASTING_BIT);
        
        if (broadcasting) {
            // Fan-out: copy packet to all destinations
            for (PacketDestination dest : destinations) {
                try {
                    dest.handlePacket(sourceId, packet.getPacket());
                } catch (Exception e) {
                    System.err.println("Error routing to destination: " + e.getMessage());
                }
            }
        } else {
            // Unicast: send to first destination only
            try {
                destinations.get(0).handlePacket(sourceId, packet.getPacket());
            } catch (Exception e) {
                System.err.println("Error routing to destination: " + e.getMessage());
            }
        }
        
        // Also route to path-based listeners
        routeToPathDestinations(info, packet);
        
        // Update statistics
        info.statistics.incrementPacketCount();
    }
    
    /**
     * Route to child registries based on ContextPath prefix matching
     */
    private void routeToChildRegistry(RoutedPacket packet) {
        NoteBytesReadOnly sourceId = packet.getSourceId();
        
        // We don't have direct info, but we can ask child registries
        for (Map.Entry<ContextPath, InputSourceRegistry> entry : childRegistries.entrySet()) {
            InputSourceRegistry childRegistry = entry.getValue();
            SourceInfo info = childRegistry.sourceById.get(sourceId);
            
            if (info != null) {
                // Found it in a child registry
                childRegistry.routePacket(packet);
                return;
            }
        }
        
        // Not found anywhere
        System.err.println("No destination found for sourceId: " + sourceId.getAsInt());
    }
    
    /**
     * Route to destinations listening on path prefixes
     */
    private void routeToPathDestinations(SourceInfo info, RoutedPacket packet) {
        ContextPath sourcePath = info.contextPath;
        
        // Find all path-based listeners that match this source's path
        for (Map.Entry<ContextPath, List<PacketDestination>> entry : pathBasedRouting.entrySet()) {
            ContextPath listenerPath = entry.getKey();
            
            // Check if source path is under this listener path
            if (sourcePath.startsWith(listenerPath)) {
                List<PacketDestination> pathDests = entry.getValue();
                for (PacketDestination dest : pathDests) {
                    try {
                        dest.handlePacket(info.sourceId, packet.getPacket());
                    } catch (Exception e) {
                        System.err.println("Error routing to path destination: " + e.getMessage());
                    }
                }
            }
        }
    }
    
    /**
     * Register a reply channel for bidirectional communication
     */
    public void registerSourceReplyChannel(
            NoteBytesReadOnly sourceId,
            Consumer<NoteBytesReadOnly> commandHandler) {
        sourceReplyChannels.put(sourceId, commandHandler);
    }
    
    /**
     * Send a command from destination back to source
     */
    public void sendToSource(NoteBytesReadOnly sourceId, NoteBytesReadOnly command) {
        Consumer<NoteBytesReadOnly> handler = sourceReplyChannels.get(sourceId);
        if (handler != null) {
            try {
                handler.accept(command);
            } catch (Exception e) {
                System.err.println("Error sending to source: " + e.getMessage());
            }
        } else {
            System.err.println("No reply channel for sourceId: " + sourceId.getAsInt());
        }
    }
    
    /**
     * Get source info by sourceId
     */
    public SourceInfo getSourceInfo(NoteBytesReadOnly sourceId) {
        return sourceById.get(sourceId);
    }
    
    /**
     * Get source info by ContextPath
     */
    public SourceInfo getSourceInfo(ContextPath contextPath) {
        return sourceByPath.get(contextPath);
    }
    
    /**
     * Find all sources under a path prefix
     */
    public List<SourceInfo> getSourcesUnderPath(ContextPath pathPrefix) {
        List<SourceInfo> results = new ArrayList<>();
        
        for (SourceInfo info : sourceByPath.values()) {
            if (info.contextPath.startsWith(pathPrefix)) {
                results.add(info);
            }
        }
        
        return results;
    }
    
    /**
     * Set a state flag for a source
     */
    public void setSourceState(NoteBytesReadOnly sourceId, int bitPosition, boolean value) {
        SourceInfo info = sourceById.get(sourceId);
        if (info != null) {
            info.setLocalFlag(bitPosition, value);
        }
    }
    
    /**
     * Check if broadcasting is enabled for a source
     */
    public boolean isBroadcasting(NoteBytesReadOnly sourceId) {
        SourceInfo info = sourceById.get(sourceId);
        return info != null && info.hasLocalFlag(SourceState.BROADCASTING_BIT);
    }
    
    /**
     * Get number of destinations for a source
     */
    public int getDestinationCount(NoteBytesReadOnly sourceId) {
        List<PacketDestination> destinations = routingTable.get(sourceId);
        return destinations != null ? destinations.size() : 0;
    }
    
    /**
     * Get all registered sources
     */
    public Collection<SourceInfo> getAllSources() {
        return Collections.unmodifiableCollection(sourceById.values());
    }
    
    /**
     * Get registry statistics summary
     */
    public String getSummary() {
        int total = sourceById.size();
        int active = 0;
        int paused = 0;
        int error = 0;
        int broadcasting = 0;
        
        for (SourceInfo info : sourceById.values()) {
            if (info.hasLocalFlag(SourceState.ACTIVE_BIT)) active++;
            if (info.hasLocalFlag(SourceState.PAUSED_BIT)) paused++;
            if (info.hasLocalFlag(SourceState.ERROR_BIT)) error++;
            if (info.hasLocalFlag(SourceState.BROADCASTING_BIT)) broadcasting++;
        }
        
        return String.format(
            "Sources: %d total, %d active, %d paused, %d error, %d broadcasting | " +
            "Child registries: %d | Path listeners: %d",
            total, active, paused, error, broadcasting,
            childRegistries.size(), pathBasedRouting.size()
        );
    }
    
    /**
     * Get the registry's context path (for chained registries)
     */
    public ContextPath getRegistryPath() {
        return registryPath;
    }
    
    /**
     * Get parent registry (null if this is root)
     */
    public InputSourceRegistry getParentRegistry() {
        return parentRegistry;
    }
    
    /**
     * Check if this registry has a child at the given path
     */
    public boolean hasChildRegistry(ContextPath pathPrefix) {
        return childRegistries.containsKey(pathPrefix);
    }
    
    /**
     * Get child registry at path
     */
    public InputSourceRegistry getChildRegistry(ContextPath pathPrefix) {
        return childRegistries.get(pathPrefix);
    }
    
    /**
     * SourceInfo - Metadata about a registered source
     */
    public static class SourceInfo {
        public final NoteBytesReadOnly sourceId;
        public final String name;
        public final InputSourceCapabilities capabilities;
        public final ContextPath contextPath;
        public final SourceStatistics statistics;
        
        private volatile int localFlags = 0;
        private final long registrationTime;
        
        public SourceInfo(
                NoteBytesReadOnly sourceId,
                String name,
                InputSourceCapabilities capabilities,
                ContextPath contextPath) {
            this.sourceId = sourceId;
            this.name = name;
            this.capabilities = capabilities;
            this.contextPath = contextPath;
            this.statistics = new SourceStatistics();
            this.registrationTime = System.currentTimeMillis();
        }
        
        public void setLocalFlag(int bitPosition, boolean value) {
            if (value) {
                localFlags = SourceState.setFlag(localFlags, bitPosition);
            } else {
                localFlags = SourceState.clearFlag(localFlags, bitPosition);
            }
        }
        
        public boolean hasLocalFlag(int bitPosition) {
            return SourceState.hasFlag(localFlags, bitPosition);
        }
        
        public int getLocalFlags() {
            return localFlags;
        }
        
        public long getRegistrationTime() {
            return registrationTime;
        }
        
        public long getUptimeMillis() {
            return System.currentTimeMillis() - registrationTime;
        }
        
        @Override
        public String toString() {
            return String.format("SourceInfo{id=%d, name='%s', path=%s, flags=%s}",
                sourceId.getAsInt(), name, contextPath, 
                SourceState.describe(localFlags));
        }
    }
    
}
