package io.netnotes.engine.io.process;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiPredicate;

import io.netnotes.engine.io.ContextPath;

/**
 * FlowProcessService - Instance-based process registry
 * 
 * KEY DESIGN PRINCIPLES:
 * 1. NOT a singleton - instantiated by owner (AppData, tests, etc.)
 * 2. Owner controls who gets interfaces and what they can do
 * 3. No enum-based interface types - rules defined at instantiation
 * 4. Security through controlled interface creation, not access control lists
 * 
 * USAGE:
 * ```java
 * // AppData creates and owns the registry
 * FlowProcessService registry = new FlowProcessService();
 * 
 * // AppData controls interface creation
 * ProcessRegistryInterface nodeInterface = registry.createInterface(
 *     nodePath,
 *     (callerPath, targetPath) -> callerPath.canReach(targetPath)  // Custom rules
 * );
 * ```
 */
public class FlowProcessService {
    
    // Path → Process
    private final ConcurrentHashMap<ContextPath, FlowProcess> processes = 
        new ConcurrentHashMap<>();
    
    // Parent-child relationships
    private final ConcurrentHashMap<ContextPath, Set<ContextPath>> children = 
        new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ContextPath, ContextPath> parents = 
        new ConcurrentHashMap<>();
    
    // Connections: subscriber path → publisher paths
    private final ConcurrentHashMap<ContextPath, Set<ContextPath>> connections = 
        new ConcurrentHashMap<>();
    
    // Stream capabilities
    private final ConcurrentHashMap<ContextPath, Boolean> streamCapable = 
        new ConcurrentHashMap<>();
    
    // Executor
    private final Executor virtualExec = Executors.newVirtualThreadPerTaskExecutor();
    
    /**
     * Public constructor - anyone can create their own registry
     * Security is handled by who controls interface creation
     */
    public FlowProcessService() {
        System.out.println("[FlowProcessService] New registry instance created");
    }
    
    // ===== INTERNAL OPERATIONS (Package-private for interfaces) =====
    
    /**
     * Register a process (called by interfaces after validation)
     */
    ContextPath registerProcess(
            FlowProcess process,
            ContextPath path,
            ContextPath parentPath,
            ProcessRegistryInterface interfaceForNewProcess) {
        
        if (processes.containsKey(path)) {
            throw new IllegalStateException("Process already registered at: " + path);
        }
        
        // Initialize process with provided interface
        process.initialize(parentPath, interfaceForNewProcess);
        
        // Store
        processes.put(path, process);
        
        // Track relationships
        if (parentPath != null) {
            parents.put(path, parentPath);
            children.computeIfAbsent(parentPath, k -> ConcurrentHashMap.newKeySet())
                .add(path);
        }
        
        // Detect stream capability
        detectStreamCapability(process, path);
        
        System.out.println("[ProcessService] Registered: " + path +
            " (stream: " + streamCapable.getOrDefault(path, false) + ")");
        
        return path;
    }
    
    private void detectStreamCapability(FlowProcess process, ContextPath path) {
        try {
            process.handleStreamChannel(null, null);
        } catch (UnsupportedOperationException e) {
            // Not stream capable
        } catch (Exception e) {
            // IS stream capable (threw different exception)
            streamCapable.put(path, true);
        }
    }
    
    /**
     * Unregister a process
     */
    void unregisterProcess(ContextPath path) {
        FlowProcess process = processes.remove(path);
        if (process == null) return;
        
        if (process.isAlive()) {
            process.kill();
        }
        
        streamCapable.remove(path);
        
        // Remove relationships
        ContextPath parentPath = parents.remove(path);
        if (parentPath != null) {
            Set<ContextPath> siblings = children.get(parentPath);
            if (siblings != null) {
                siblings.remove(path);
            }
        }
        
        // Remove connections
        connections.remove(path);
        connections.values().forEach(set -> set.remove(path));
        
        // Recursively unregister children
        Set<ContextPath> childPaths = children.remove(path);
        if (childPaths != null) {
            childPaths.forEach(this::unregisterProcess);
        }
        
        System.out.println("[ProcessService] Unregistered: " + path);
    }
    
    /**
     * Connect two processes (reactive streams wiring)
     */
    void connect(ContextPath upstreamPath, ContextPath downstreamPath) {
        FlowProcess upstream = processes.get(upstreamPath);
        FlowProcess downstream = processes.get(downstreamPath);
        
        if (upstream == null) {
            throw new IllegalArgumentException("Upstream process not found: " + upstreamPath);
        }
        if (downstream == null) {
            throw new IllegalArgumentException("Downstream process not found: " + downstreamPath);
        }
        
        // Subscribe downstream to upstream
        upstream.subscribe(downstream.getSubscriber());
        
        // Track connection
        connections.computeIfAbsent(downstreamPath, k -> ConcurrentHashMap.newKeySet())
            .add(upstreamPath);
        
        System.out.println("[ProcessService] Connected: " + upstreamPath + " → " + downstreamPath);
    }
    
    /**
     * Disconnect processes
     */
    void disconnect(ContextPath upstreamPath, ContextPath downstreamPath) {
        Set<ContextPath> upstreams = connections.get(downstreamPath);
        if (upstreams != null) {
            upstreams.remove(upstreamPath);
        }
        
        System.out.println("[ProcessService] Disconnected: " + upstreamPath + " ⊣ " + downstreamPath);
    }
    
    /**
     * Start a process
     */
    CompletableFuture<Void> startProcess(ContextPath path) {
        FlowProcess process = processes.get(path);
        if (process == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Process not found: " + path));
        }
        
        return CompletableFuture.runAsync(() -> {
            try {
                process.onStart();
                process.run().join();
            } catch (Exception e) {
                System.err.println("Process " + path + " error: " + e.getMessage());
                e.printStackTrace();
            } finally {
                process.onStop();
                process.complete();
            }
        }, virtualExec);
    }
    
    /**
     * Kill a process
     */
    void killProcess(ContextPath path) {
        FlowProcess process = processes.get(path);
        if (process != null) {
            process.kill();
        }
    }
    
    /**
     * Request stream channel
     */
    CompletableFuture<StreamChannel> requestStreamChannel(
            ContextPath from,
            ContextPath to) {
        
        return CompletableFuture.supplyAsync(() -> {
            FlowProcess target = processes.get(to);
            if (target == null) {
                throw new IllegalArgumentException("Target process not found: " + to);
            }
            
            if (!target.isAlive()) {
                throw new IllegalStateException("Target process not alive: " + to);
            }
            
            try {
                StreamChannel channel = new StreamChannel(from, to);
                target.handleStreamChannel(channel, from);
                return channel;
            } catch (Exception e) {
                throw new RuntimeException("Failed to create stream channel", e);
            }
        }, virtualExec);
    }
    
    // ===== PUBLIC QUERIES (Safe - read-only) =====
    
    public FlowProcess getProcess(ContextPath path) {
        return processes.get(path);
    }
    
    public boolean exists(ContextPath path) {
        return processes.containsKey(path);
    }
    
    public Set<ContextPath> getChildren(ContextPath parentPath) {
        Set<ContextPath> childPaths = children.get(parentPath);
        return childPaths != null ? new HashSet<>(childPaths) : Collections.emptySet();
    }
    
    public <T extends FlowProcess> List<T> findChildrenByType(ContextPath parentPath, Class<T> type) {
        Set<ContextPath> childPaths = children.get(parentPath);
        if (childPaths == null) return Collections.emptyList();
        
        return childPaths.stream()
            .map(processes::get)
            .filter(Objects::nonNull)
            .filter(type::isInstance)
            .map(type::cast)
            .toList();
    }
    
    public ContextPath getParent(ContextPath childPath) {
        return parents.get(childPath);
    }
    
    public boolean isStreamCapable(ContextPath path) {
        return streamCapable.getOrDefault(path, false);
    }
    
    public Set<ContextPath> getUpstreams(ContextPath processPath) {
        Set<ContextPath> upstreams = connections.get(processPath);
        return upstreams != null ? new HashSet<>(upstreams) : Collections.emptySet();
    }
    
    public Set<ContextPath> getDownstreams(ContextPath processPath) {
        return connections.entrySet().stream()
            .filter(e -> e.getValue().contains(processPath))
            .map(Map.Entry::getKey)
            .collect(java.util.stream.Collectors.toSet());
    }
    
    public List<FlowProcess> findProcessesUnder(ContextPath prefix) {
        return processes.entrySet().stream()
            .filter(e -> e.getKey().startsWith(prefix))
            .map(Map.Entry::getValue)
            .toList();
    }
    
    public String getSummary() {
        int total = processes.size();
        int alive = (int) processes.values().stream()
            .filter(FlowProcess::isAlive).count();
        
        return String.format("Processes: %d total (%d alive)", total, alive);
    }
    
    // ===== SHUTDOWN =====
    
    public void shutdown() {
        new ArrayList<>(processes.keySet()).forEach(this::unregisterProcess);
        processes.clear();
        children.clear();
        parents.clear();
        connections.clear();
        streamCapable.clear();
        System.out.println("[ProcessService] Shutdown complete");
    }
    
    /**
     * Validator interface for custom access control
     */
    public interface InterfaceValidator {
        void validateRegister(ContextPath caller, ContextPath target);
        void validateStart(ContextPath caller, ContextPath target);
        void validateGet(ContextPath caller, ContextPath target);
        void validateConnect(ContextPath caller, ContextPath upstream, ContextPath downstream);
        void validateUnregister(ContextPath caller, ContextPath target);
        void validateStream(ContextPath caller, ContextPath target);
    }
}