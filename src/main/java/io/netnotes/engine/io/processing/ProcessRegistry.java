package io.netnotes.engine.io.processing;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;

/**
 * ProcessRegistry - Manages process lifecycle and Reactive Streams message routing.
 * 
 * Architecture:
 * - Each process has a SubmissionPublisher for message delivery
 * - Publishers handle backpressure automatically
 * - Thread pools are configurable per process type
 * - No dropped messages (backpressure blocks sender)
 * 
 * This registry is separate from InputSourceRegistry:
 * - InputSourceRegistry: Routes input events (keyboard, mouse, etc.)
 * - ProcessRegistry: Routes messages between processes
 * 
 * However, they work together:
 * - Input sources can send to processes
 * - Processes can register as input destinations
 */
public class ProcessRegistry {
    private static final ProcessRegistry INSTANCE = new ProcessRegistry();
    
    private final AtomicInteger nextProcessId = new AtomicInteger(1);
    
    // Process ID → SubmissionPublisher (for message delivery)
    private final ConcurrentHashMap<ProcessId, SubmissionPublisher<RoutedPacket>> publishers = 
        new ConcurrentHashMap<>();
    
    // Process ID → ProcessHandle
    private final ConcurrentHashMap<ProcessId, ProcessHandle> processes = 
        new ConcurrentHashMap<>();
    
    // Process ID → ContextPath
    private final ConcurrentHashMap<ProcessId, ContextPath> processPaths = 
        new ConcurrentHashMap<>();
    
    // ContextPath → Process ID (for path-based lookup)
    private final ConcurrentHashMap<ContextPath, ProcessId> pathToProcess = 
        new ConcurrentHashMap<>();
    
    // Parent → Children mapping
    private final ConcurrentHashMap<ProcessId, Set<ProcessId>> children = 
        new ConcurrentHashMap<>();
    
    // Child → Parent mapping
    private final ConcurrentHashMap<ProcessId, ProcessId> parents = 
        new ConcurrentHashMap<>();
    
    // Default executor for message delivery
    private final Executor defaultExecutor = Executors.newVirtualThreadPerTaskExecutor();
    
    // Configurable executors per process type
    private final ConcurrentHashMap<String, Executor> executorsByType = 
        new ConcurrentHashMap<>();
    
    private ProcessRegistry() {
        // Singleton
        
        // Pre-configure executors for common process types
        executorsByType.put("ui", Executors.newSingleThreadExecutor()); // UI must be single-threaded
        executorsByType.put("compute", Executors.newWorkStealingPool()); // Parallel compute
        executorsByType.put("io", Executors.newVirtualThreadPerTaskExecutor()); // IO-bound
    }
    
    public static ProcessRegistry getInstance() {
        return INSTANCE;
    }
    
    /**
     * Register a process with default configuration
     */
    public ProcessId registerProcess(ProcessHandle handle, ContextPath path, ProcessId parentId) {
        return registerProcess(handle, path, parentId, null, 256);
    }
    
    /**
     * Register a process with custom executor and buffer size
     * 
     * @param handle The process to register
     * @param path Hierarchical path for organization
     * @param parentId Parent process (null for root)
     * @param executorType Type of executor ("ui", "compute", "io", or null for default)
     * @param maxBufferCapacity Per-subscriber buffer size (default: 256)
     */
    public ProcessId registerProcess(
            ProcessHandle handle, 
            ContextPath path, 
            ProcessId parentId,
            String executorType,
            int maxBufferCapacity) {
        
        ProcessId pid = new ProcessId(nextProcessId.getAndIncrement());
        
        // Choose executor
        Executor executor = executorType != null 
            ? executorsByType.getOrDefault(executorType, defaultExecutor)
            : defaultExecutor;
        
        // Create publisher for this process
        SubmissionPublisher<RoutedPacket> publisher = new SubmissionPublisher<>(
            executor,
            maxBufferCapacity
        );
        
        // Subscribe the process to its publisher
        publisher.subscribe(handle.getSubscriber());
        
        // Store mappings
        publishers.put(pid, publisher);
        processes.put(pid, handle);
        processPaths.put(pid, path);
        pathToProcess.put(path, pid);
        
        // Handle parent-child relationship
        if (parentId != null) {
            parents.put(pid, parentId);
            children.computeIfAbsent(parentId, k -> ConcurrentHashMap.newKeySet()).add(pid);
        }
        
        System.out.println("Registered process: " + pid + " at " + path + 
                         " (executor=" + (executorType != null ? executorType : "default") + 
                         ", buffer=" + maxBufferCapacity + ")");
        
        return pid;
    }
    
    /**
     * Unregister a process and cleanup
     */
    public void unregisterProcess(ProcessId pid) {
        // Close publisher (completes stream for subscriber)
        SubmissionPublisher<RoutedPacket> publisher = publishers.remove(pid);
        if (publisher != null) {
            publisher.close();
        }
        
        ProcessHandle handle = processes.remove(pid);
        if (handle == null) return;
        
        ContextPath path = processPaths.remove(pid);
        if (path != null) {
            pathToProcess.remove(path);
        }
        
        // Remove from parent's children
        ProcessId parent = parents.remove(pid);
        if (parent != null) {
            Set<ProcessId> parentChildren = children.get(parent);
            if (parentChildren != null) {
                parentChildren.remove(pid);
            }
        }
        
        // Unregister all children recursively
        Set<ProcessId> processChildren = children.remove(pid);
        if (processChildren != null) {
            for (ProcessId child : processChildren) {
                unregisterProcess(child);
            }
        }
        
        System.out.println("Unregistered process: " + pid);
    }
    
    /**
     * Send message to process by ID
     * 
     * This uses SubmissionPublisher.submit() which:
     * - Returns immediately (non-blocking)
     * - Applies backpressure if subscriber is slow
     * - Never drops messages (blocks sender if buffer full)
     */
    public boolean sendToProcess(ProcessId pid, RoutedPacket packet) {
        SubmissionPublisher<RoutedPacket> publisher = publishers.get(pid);
        if (publisher != null) {
            try {
                // Submit message - blocks if subscriber can't keep up
                int lag = publisher.submit(packet);
                
                // Lag is estimated number of items in buffer
                if (lag > 100) {
                    System.out.println("WARNING: Process " + pid + " is lagging (buffer: " + lag + ")");
                }
                
                return true;
            } catch (Exception e) {
                System.err.println("Error sending to process " + pid + ": " + e.getMessage());
                return false;
            }
        }
        return false;
    }
    
    /**
     * Send message to process by path
     */
    public boolean sendToProcess(ContextPath path, RoutedPacket packet) {
        ProcessId pid = pathToProcess.get(path);
        if (pid != null) {
            return sendToProcess(pid, packet);
        }
        return false;
    }
    
    /**
     * Broadcast message to all children of a process
     */
    public void broadcastToChildren(ProcessId parentPid, RoutedPacket packet) {
        Set<ProcessId> processChildren = children.get(parentPid);
        if (processChildren != null) {
            for (ProcessId childPid : processChildren) {
                sendToProcess(childPid, packet);
            }
        }
    }
    
    /**
     * Send message to parent process
     */
    public boolean sendToParent(ProcessId childPid, RoutedPacket packet) {
        ProcessId parentPid = parents.get(childPid);
        if (parentPid != null) {
            return sendToProcess(parentPid, packet);
        }
        return false;
    }
    
    /**
     * Get process handle
     */
    public ProcessHandle getProcess(ProcessId pid) {
        return processes.get(pid);
    }
    
    /**
     * Get process by path
     */
    public ProcessHandle getProcess(ContextPath path) {
        ProcessId pid = pathToProcess.get(path);
        return pid != null ? processes.get(pid) : null;
    }
    
    /**
     * Get process path
     */
    public ContextPath getProcessPath(ProcessId pid) {
        return processPaths.get(pid);
    }
    
    /**
     * Get parent process ID
     */
    public ProcessId getParent(ProcessId childPid) {
        return parents.get(childPid);
    }
    
    /**
     * Get children process IDs
     */
    public Set<ProcessId> getChildren(ProcessId parentPid) {
        Set<ProcessId> processChildren = children.get(parentPid);
        return processChildren != null ? new HashSet<>(processChildren) : Collections.emptySet();
    }
    
    /**
     * Find all processes under a path prefix
     */
    public List<ProcessId> findProcessesUnder(ContextPath prefix) {
        return processPaths.entrySet().stream()
            .filter(e -> e.getValue().startsWith(prefix))
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }
    
    /**
     * Check if process exists
     */
    public boolean exists(ProcessId pid) {
        return processes.containsKey(pid);
    }
    
    /**
     * Get all registered processes
     */
    public Collection<ProcessId> getAllProcesses() {
        return new ArrayList<>(processes.keySet());
    }
    
    /**
     * Get publisher statistics for monitoring
     */
    public PublisherStats getStats(ProcessId pid) {
        SubmissionPublisher<RoutedPacket> publisher = publishers.get(pid);
        if (publisher == null) return null;
        
        return new PublisherStats(
            pid,
            publisher.getNumberOfSubscribers(),
            publisher.estimateMaximumLag(),
            publisher.isClosed()
        );
    }
    
    /**
     * Configure executor for a process type
     */
    public void setExecutorForType(String type, Executor executor) {
        executorsByType.put(type, executor);
    }
    
    /**
     * Get registry statistics
     */
    public String getSummary() {
        int total = processes.size();
        int roots = (int) processes.keySet().stream()
            .filter(pid -> !parents.containsKey(pid))
            .count();
        
        int lagging = 0;
        for (SubmissionPublisher<RoutedPacket> pub : publishers.values()) {
            if (pub.estimateMaximumLag() > 100) {
                lagging++;
            }
        }
        
        return String.format("Processes: %d total, %d roots, %d with children, %d lagging",
            total, roots, children.size(), lagging);
    }
    
    /**
     * Shutdown all processes and publishers
     */
    public void shutdown() {
        // Close all publishers (completes streams)
        for (SubmissionPublisher<RoutedPacket> publisher : publishers.values()) {
            publisher.close();
        }
        
        publishers.clear();
        processes.clear();
        processPaths.clear();
        pathToProcess.clear();
        children.clear();
        parents.clear();
        
        System.out.println("ProcessRegistry shutdown complete");
    }
    
    /**
     * ProcessHandle - Interface for process message delivery
     */
    public interface ProcessHandle {
        /**
         * Get the Flow.Subscriber for this process
         */
        java.util.concurrent.Flow.Subscriber<RoutedPacket> getSubscriber();
        
        /**
         * Check if process is alive
         */
        boolean isAlive();
        
        /**
         * Get process info
         */
        String getInfo();
        
        /**
         * Legacy interface for non-reactive code
         * @deprecated Use reactive streams instead
         */
        @Deprecated
        boolean sendMessage(RoutedPacket packet);
    }
    
    /**
     * Publisher statistics for monitoring
     */
    public record PublisherStats(
        ProcessId processId,
        int subscriberCount,
        long maxLag,
        boolean closed
    ) {}
}