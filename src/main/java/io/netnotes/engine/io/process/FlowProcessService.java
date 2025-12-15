package io.netnotes.engine.io.process;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.utils.LoggingHelpers.Log;

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
        Log.logMsg("[FlowProcessService] New registry instance created");
    }
    
    public ProcessRegistryInterface getRegistryInterface(){
        return new FullRegistryInterface();
    }

    // ===== INTERNAL OPERATIONS (Package-private for interfaces) =====
    
    /**
     * Register process at EXPLICIT path
     * 
     * Path is independent of parent relationship
     */
    ContextPath registerProcess(
        FlowProcess process,
        ContextPath path,
        ContextPath parentPath,
        ProcessRegistryInterface interfaceForNewProcess
    ) {
        Log.logMsg("[FlowProcessService] registering " + process.getName() + "...");
        if (processes.containsKey(path)) {
            throw new IllegalStateException("Process already registered at: " + path);
        }
        
        // Initialize with explicit path
        process.initialize(path, parentPath, interfaceForNewProcess);
        
        // Store
        processes.put(path, process);
        
        // Track hierarchy (separate from path structure!)
        if (parentPath != null) {
            parents.put(path, parentPath);
            children.computeIfAbsent(parentPath, k -> ConcurrentHashMap.newKeySet())
                .add(path);
        }
        
        // Detect stream capability
        detectStreamCapability(process, path);
        
        Log.logMsg("[ProcessService] Registered: " + path +
            " (parent: " + parentPath + ", stream: " + 
            streamCapable.getOrDefault(path, false) + ")");
        
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
        
        // Remove from hierarchy
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
        
        Log.logMsg("[ProcessService] Unregistered: " + path);
    }
    
    /**
     * Connect two processes (reactive streams wiring)
     */
    void connect(ContextPath upstreamPath, ContextPath downstreamPath) {
        FlowProcess upstream = processes.get(upstreamPath);
        FlowProcess downstream = processes.get(downstreamPath);
        
        // ADD THESE LOGS
        Log.logMsg("[FlowProcessService] connect() called: " + upstreamPath + " → " + downstreamPath);
        Log.logMsg("  Upstream process exists: " + (upstream != null));
        Log.logMsg("  Downstream process exists: " + (downstream != null));
        
        if (upstream == null) {
            Log.logError("[FlowProcessService] Upstream process not found: " + upstreamPath);
            throw new IllegalArgumentException("Upstream process not found: " + upstreamPath);
        }
        if (downstream == null) {
            Log.logError("[FlowProcessService] Downstream process not found: " + downstreamPath);
            throw new IllegalArgumentException("Downstream process not found: " + downstreamPath);
        }
        
        // ADD THIS LOG
        Log.logMsg("  Upstream subscribers before: " + upstream.getSubscriberCount());
        
        // Subscribe downstream to upstream
        upstream.subscribe(downstream.getSubscriber());
        
        // ADD THIS LOG
        Log.logMsg("  Upstream subscribers after: " + upstream.getSubscriberCount());
        
        // Track connection
        connections.computeIfAbsent(downstreamPath, k -> ConcurrentHashMap.newKeySet())
            .add(upstreamPath);
        
        Log.logMsg("[ProcessService] Connected: " + upstreamPath + " → " + downstreamPath);
    }
    
    /**
     * Disconnect processes
     */
    void disconnect(ContextPath upstreamPath, ContextPath downstreamPath) {
        Set<ContextPath> upstreams = connections.get(downstreamPath);
        if (upstreams != null) {
            upstreams.remove(upstreamPath);
        }
        
        Log.logMsg("[ProcessService] Disconnected: " + upstreamPath + " ⊣ " + downstreamPath);
    }
    
    /**
     * Start a process
     * 
     * FIXED: Separates "startup" from "completion"
     * - run() future = startup phase (initialization, setup)
     * - getCompletionFuture() = actual process lifetime
     * 
     * When run() completes: process is started and running
     * When getCompletionFuture() completes: process is done
     */
    CompletableFuture<Void> startProcess(ContextPath path) {
        FlowProcess process = processes.get(path);
        if (process == null) {
            Log.logError("[FlowProcessService] startProcess: Process not found at " + path);
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Process not found: " + path));
        }

        Log.logMsg("[FlowProcessService] Starting process: " + path + 
            " (type: " + process.getClass().getSimpleName() + ")");
        
        // Start the process in background
        CompletableFuture<Void> startupFuture = new CompletableFuture<>();
        
        CompletableFuture.runAsync(() -> {
            try {
                Log.logMsg("[FlowProcessService] Calling onStart() for " + path);
                process.onStart();
                
                Log.logMsg("[FlowProcessService] Calling run() for " + path);
                CompletableFuture<Void> runFuture = process.run();
                
                Log.logMsg("[FlowProcessService] run() returned future for " + path + 
                    " (completed: " + runFuture.isDone() + ")");
                
                // Wait for run() to complete (startup phase)
                runFuture.whenComplete((v, ex) -> {
                    if (ex != null) {
                        Log.logError("[FlowProcessService] Process " + path + 
                            " startup error: " + ex);
                        ex.printStackTrace();
                        startupFuture.completeExceptionally(ex);
                    } else {
                        Log.logMsg("[FlowProcessService] Process " + path + 
                            " startup complete, now running");
                        startupFuture.complete(null);
                    }
                });
                
                // SEPARATELY: Wait for process completion (lifetime)
                // This doesn't block startup, it just sets up cleanup
                process.getCompletionFuture().whenComplete((v, ex) -> {
                    Log.logMsg("[FlowProcessService] Process " + path + " completed");
                    try {
                        process.onStop();
                    } catch (Exception e) {
                        Log.logError("[FlowProcessService] onStop() error for " + path + 
                            ": " + e);
                    }
                });
                
            } catch (Exception e) {
                Log.logError("[FlowProcessService] Process " + path + 
                    " startup exception: " + e);
                e.printStackTrace();
                startupFuture.completeExceptionally(e);
                process.complete();
            }
        }, virtualExec);
        
        // Return the startup future, not the completion future
        return startupFuture;
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

    /**
     * Find by path prefix - KEY NEW FEATURE
     * 
     * No process needs to exist at prefix path!
     * Searches by string matching on paths.
     */
    public List<FlowProcess> findByPathPrefix(ContextPath prefix) {
        return processes.entrySet().stream()
            .filter(e -> e.getKey().startsWith(prefix))
            .map(Map.Entry::getValue)
            .collect(Collectors.toList());
    }

    public Set<ContextPath> getAllPaths() {
        return new HashSet<>(processes.keySet());
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

    public List<FlowProcess> findChildrenByName(ContextPath parentPath, String name) {
        Set<ContextPath> childPaths = children.get(parentPath);
        if (childPaths == null) return Collections.emptyList();
        
        return childPaths.stream()
            .map(processes::get)
            .filter(Objects::nonNull)
            .filter((flowProcess)->flowProcess.getName().equals(name))
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
        Log.logMsg("[ProcessService] Shutdown complete");
    }

    /**
     * Inner class implementing full interface
     */
    private class FullRegistryInterface implements ProcessRegistryInterface {
        
        @Override
        public ContextPath registerProcess(
                FlowProcess process, 
                ContextPath path, 
                ContextPath parentPath,
                ProcessRegistryInterface interfaceForNewProcess) {
            return FlowProcessService.this.registerProcess(
                process, path, parentPath, interfaceForNewProcess);
        }
        
        @Override
        public void unregisterProcess(ContextPath path) {
            FlowProcessService.this.unregisterProcess(path);
        }
        
        @Override
        public FlowProcess getProcess(ContextPath path) {
            return FlowProcessService.this.getProcess(path);
        }
        
        @Override
        public boolean exists(ContextPath path) {
            return FlowProcessService.this.exists(path);
        }
        
        @Override
        public List<FlowProcess> findByPathPrefix(ContextPath prefix) {
            return FlowProcessService.this.findByPathPrefix(prefix);
        }
        
        @Override
        public Set<ContextPath> getAllPaths() {
            return FlowProcessService.this.getAllPaths();
        }
        
        @Override
        public ContextPath getParent(ContextPath childPath) {
            return FlowProcessService.this.getParent(childPath);
        }
        
        @Override
        public Set<ContextPath> getChildren(ContextPath parentPath) {
            return FlowProcessService.this.getChildren(parentPath);
        }
        
        @Override
        public <T extends FlowProcess> List<T> findChildrenByType(
                ContextPath parentPath, Class<T> type) {
            return FlowProcessService.this.findChildrenByType(parentPath, type);
        }
        
        @Override
        public List<FlowProcess> findChildrenByName(ContextPath parentPath, String name) {
            return FlowProcessService.this.findChildrenByName(parentPath, name);
        }
        
        @Override
        public CompletableFuture<Void> startProcess(ContextPath path) {
            return FlowProcessService.this.startProcess(path);
        }
        
        @Override
        public void killProcess(ContextPath path) {
            FlowProcessService.this.killProcess(path);
        }
        
        @Override
        public void connect(ContextPath upstreamPath, ContextPath downstreamPath) {
            FlowProcessService.this.connect(upstreamPath, downstreamPath);
        }
        
        @Override
        public void disconnect(ContextPath upstreamPath, ContextPath downstreamPath) {
            FlowProcessService.this.disconnect(upstreamPath, downstreamPath);
        }
        
        @Override
        public Set<ContextPath> getUpstreams(ContextPath processPath) {
            return FlowProcessService.this.getUpstreams(processPath);
        }
        
        @Override
        public Set<ContextPath> getDownstreams(ContextPath processPath) {
            return FlowProcessService.this.getDownstreams(processPath);
        }
        
        @Override
        public CompletableFuture<StreamChannel> requestStreamChannel(
                ContextPath callerPath, ContextPath target) {
            return FlowProcessService.this.requestStreamChannel(callerPath, target);
        }
        
        @Override
        public boolean isStreamCapable(ContextPath path) {
            return FlowProcessService.this.isStreamCapable(path);
        }
        
        @Override
        public String getSummary() {
            return FlowProcessService.this.getSummary();
        }
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