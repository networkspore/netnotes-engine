package io.netnotes.engine.io.process;

import java.util.*;
import java.util.concurrent.*;

import io.netnotes.engine.io.ContextPath;

/**
 * UnifiedProcessRegistry - Single registry for ALL processes.
 * 
 * Simplified Design:
 * - Everything is a Process (sources, transforms, sinks)
 * - All use Reactive Streams (no special input source handling)
 * - Path-based addressing (ContextPath)
 * - Automatic wiring via subscribe()
 * 
 * Process Flow:
 * 1. Register processes with paths
 * 2. Wire them together (upstream subscribes to downstream)
 * 3. Start processes (they begin emitting/processing)
 * 4. Backpressure handled automatically by Flow API
 */
public class FlowProcessRegistry {
    private static final FlowProcessRegistry INSTANCE = new FlowProcessRegistry();
    
    // Path → Process
    private final ConcurrentHashMap<ContextPath, FlowProcess> processes = new ConcurrentHashMap<>();
    
    // Parent-child relationships
    private final ConcurrentHashMap<ContextPath, Set<ContextPath>> children = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ContextPath, ContextPath> parents = new ConcurrentHashMap<>();
    
    // Connections: subscriber path → publisher paths
    private final ConcurrentHashMap<ContextPath, Set<ContextPath>> connections = new ConcurrentHashMap<>();
    
    private final Executor virtualExec = Executors.newVirtualThreadPerTaskExecutor();;

    private FlowProcessRegistry() {}
    
    public static FlowProcessRegistry getInstance() {
        return INSTANCE;
    }
    
    // ===== REGISTRATION =====
    
    /**
     * Register a process at a path
     */
    public ContextPath registerProcess(FlowProcess process, ContextPath path) {
        return registerProcess(process, path, null);
    }
    
    /**
     * Register a process with parent
     */
    public ContextPath registerProcess(FlowProcess process, ContextPath path, ContextPath parentPath) {
        // Check for duplicates
        if (processes.containsKey(path)) {
            throw new IllegalStateException("Process already registered at: " + path);
        }
        
        // Initialize process
        process.initialize(path, parentPath, this);
        
        // Store process
        processes.put(path, process);
        
        // Setup parent-child relationship
        if (parentPath != null) {
            parents.put(path, parentPath);
            children.computeIfAbsent(parentPath, k -> ConcurrentHashMap.newKeySet()).add(path);
        }
        
        System.out.println("Registered process: " + path + " (type: " + process.getProcessType() + ")");
        
        return path;
    }
    
    /**
     * Unregister a process
     */
    public void unregisterProcess(ContextPath path) {
        FlowProcess process = processes.remove(path);
        if (process == null) return;
        
        // Kill process if still alive
        if (process.isAlive()) {
            process.kill();
        }
        
        // Remove parent-child relationships
        ContextPath parentPath = parents.remove(path);
        if (parentPath != null) {
            Set<ContextPath> siblings = children.get(parentPath);
            if (siblings != null) {
                siblings.remove(path);
            }
        }
        
        // Recursively unregister children
        Set<ContextPath> childPaths = children.remove(path);
        if (childPaths != null) {
            childPaths.forEach(this::unregisterProcess);
        }
        
        // Remove connections
        connections.remove(path);
        connections.values().forEach(set -> set.remove(path));
        
        System.out.println("Unregistered process: " + path);
    }
    
    // ===== WIRING (REACTIVE STREAMS) =====
    
    /**
     * Connect processes: downstream subscribes to upstream
     * 
     * Flow: upstream.emit() → downstream.handleMessage()
     */
    public void connect(ContextPath upstreamPath, ContextPath downstreamPath) {
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
        
        System.out.println("Connected: " + upstreamPath + " → " + downstreamPath);
    }
    
    /**
     * Connect one upstream to multiple downstreams (fan-out)
     */
    public void fanOut(ContextPath upstreamPath, ContextPath... downstreamPaths) {
        for (ContextPath downstream : downstreamPaths) {
            connect(upstreamPath, downstream);
        }
    }
    
    /**
     * Connect multiple upstreams to one downstream (fan-in)
     * Each upstream gets its own subscription
     */
    public void fanIn(ContextPath downstreamPath, ContextPath... upstreamPaths) {
        for (ContextPath upstream : upstreamPaths) {
            connect(upstream, downstreamPath);
        }
    }
    
    /**
     * Disconnect processes
     */
    public void disconnect(ContextPath upstreamPath, ContextPath downstreamPath) {
        // Note: Flow API doesn't provide clean disconnect after subscribe()
        // In practice, we'd need to track subscriptions to cancel them
        // For now, just remove from our tracking
        
        Set<ContextPath> upstreams = connections.get(downstreamPath);
        if (upstreams != null) {
            upstreams.remove(upstreamPath);
        }
        
        System.out.println("Disconnected: " + upstreamPath + " ⊣ " + downstreamPath);
    }
    
    // ===== PROCESS LIFECYCLE =====
    
    /**
     * Start a process (begin execution)
     */
    public CompletableFuture<Void> startProcess(ContextPath path) {
        FlowProcess process = processes.get(path);
        if (process == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Process not found: " + path));
        }
        
        // Start in virtual thread
        return CompletableFuture.runAsync(() -> {
            try {
                process.onStart();
                process.run().join();  // Wait for async completion
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
     * Start all processes under a path prefix
     */
    public void startAll(ContextPath prefix) {
        processes.keySet().stream()
            .filter(path -> path.startsWith(prefix))
            .forEach(this::startProcess);
    }
    
    /**
     * Kill a process
     */
    public void killProcess(ContextPath path) {
        FlowProcess process = processes.get(path);
        if (process != null) {
            process.kill();
        }
    }
    
    /**
     * Kill all processes under a prefix
     */
    public void killAll(ContextPath prefix) {
        processes.keySet().stream()
            .filter(path -> path.startsWith(prefix))
            .forEach(this::killProcess);
    }
    
    // ===== QUERIES =====
    
    /**
     * Get process at path
     */
    public FlowProcess getProcess(ContextPath path) {
        return processes.get(path);
    }
    
    /**
     * Check if process exists
     */
    public boolean exists(ContextPath path) {
        return processes.containsKey(path);
    }
    
    /**
     * Find all processes under a prefix
     */
    public List<FlowProcess> findProcessesUnder(ContextPath prefix) {
        return processes.entrySet().stream()
            .filter(e -> e.getKey().startsWith(prefix))
            .map(Map.Entry::getValue)
            .toList();
    }
    
    /**
     * Find processes by type
     */
    public List<FlowProcess> findProcessesByType(FlowProcess.ProcessType type) {
        return processes.values().stream()
            .filter(p -> p.getProcessType() == type)
            .toList();
    }
    
    /**
     * Get children of a process
     */
    public Set<ContextPath> getChildren(ContextPath parentPath) {
        Set<ContextPath> childPaths = children.get(parentPath);
        return childPaths != null ? new HashSet<>(childPaths) : Collections.emptySet();
    }
    
    /**
     * Get parent of a process
     */
    public ContextPath getParent(ContextPath childPath) {
        return parents.get(childPath);
    }
    
    /**
     * Get upstream processes (what this process subscribes to)
     */
    public Set<ContextPath> getUpstreams(ContextPath processPath) {
        Set<ContextPath> upstreams = connections.get(processPath);
        return upstreams != null ? new HashSet<>(upstreams) : Collections.emptySet();
    }
    
    /**
     * Get downstream processes (what subscribes to this process)
     */
    public Set<ContextPath> getDownstreams(ContextPath processPath) {
        return connections.entrySet().stream()
            .filter(e -> e.getValue().contains(processPath))
            .map(Map.Entry::getKey)
            .collect(java.util.stream.Collectors.toSet());
    }
    
    // ===== TOPOLOGY HELPERS =====
    
    /**
     * Build a linear pipeline: A → B → C → D
     */
    public void buildPipeline(ContextPath... stages) {
        for (int i = 0; i < stages.length - 1; i++) {
            connect(stages[i], stages[i + 1]);
        }
    }
    
    /**
     * Build a fan-out topology: A → [B, C, D]
     */
    public void buildFanOut(ContextPath source, ContextPath... targets) {
        fanOut(source, targets);
    }
    
    /**
     * Build a fan-in topology: [A, B, C] → D
     */
    public void buildFanIn(ContextPath target, ContextPath... sources) {
        fanIn(target, sources);
    }
    
    /**
     * Get process topology as DOT graph (for visualization)
     */
    public String getTopologyDOT() {
        StringBuilder dot = new StringBuilder("digraph Processes {\n");
        dot.append("  rankdir=LR;\n");
        dot.append("  node [shape=box];\n\n");
        
        // Add nodes
        for (Map.Entry<ContextPath, FlowProcess> entry : processes.entrySet()) {
            ContextPath path = entry.getKey();
            FlowProcess process = entry.getValue();
            String color = switch (process.getProcessType()) {
                case SOURCE -> "lightblue";
                case TRANSFORM -> "lightgreen";
                case SINK -> "lightyellow";
                case BIDIRECTIONAL -> "lightpink";
            };
            
            dot.append(String.format("  \"%s\" [style=filled, fillcolor=%s, label=\"%s\\n%s\"];\n",
                path, color, path.toString(), process.getProcessType()));
        }
        
        dot.append("\n");
        
        // Add edges
        for (Map.Entry<ContextPath, Set<ContextPath>> entry : connections.entrySet()) {
            ContextPath downstream = entry.getKey();
            for (ContextPath upstream : entry.getValue()) {
                dot.append(String.format("  \"%s\" -> \"%s\";\n", upstream, downstream));
            }
        }
        
        dot.append("}\n");
        return dot.toString();
    }
    
    // ===== STATISTICS =====
    
    public String getSummary() {
        int total = processes.size();
        int alive = (int) processes.values().stream().filter(FlowProcess::isAlive).count();
        int sources = (int) processes.values().stream()
            .filter(p -> p.getProcessType() == FlowProcess.ProcessType.SOURCE).count();
        int transforms = (int) processes.values().stream()
            .filter(p -> p.getProcessType() == FlowProcess.ProcessType.TRANSFORM).count();
        int sinks = (int) processes.values().stream()
            .filter(p -> p.getProcessType() == FlowProcess.ProcessType.SINK).count();
        
        int totalConnections = connections.values().stream().mapToInt(Set::size).sum();
        
        return String.format(
            "Processes: %d total (%d alive) | Types: %d sources, %d transforms, %d sinks | Connections: %d",
            total, alive, sources, transforms, sinks, totalConnections
        );
    }
    
    /**
     * Get detailed process info
     */
    public Map<String, Object> getProcessStats(ContextPath path) {
        FlowProcess process = processes.get(path);
        if (process == null) return null;
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("path", path.toString());
        stats.put("type", process.getProcessType().toString());
        stats.put("alive", process.isAlive());
        stats.put("subscribers", process.getSubscriberCount());
        stats.put("uptime_ms", process.getUptimeMillis());
        stats.put("upstreams", getUpstreams(path).size());
        stats.put("downstreams", getDownstreams(path).size());
        
        return stats;
    }
    
    // ===== SHUTDOWN =====
    
    public void shutdown() {
        // Kill all processes
        new ArrayList<>(processes.keySet()).forEach(this::unregisterProcess);
        
        processes.clear();
        connections.clear();
        children.clear();
        parents.clear();
        
        System.out.println("UnifiedProcessRegistry shutdown complete");
    }
}