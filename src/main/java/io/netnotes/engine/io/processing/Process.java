package io.netnotes.engine.io.processing;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;

import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Process - Base class for all processes in the system.
 * 
 * Architecture:
 * - Processes are isolated compute units
 * - Communication via Reactive Streams (Flow API)
 * - Automatic backpressure management
 * - No dropped messages, no OOM
 * 
 * Simple API for Process Authors:
 * - Override handleMessage(packet) - that's it!
 * - Optionally configure backpressure strategy
 * 
 */
public abstract class Process implements ProcessRegistry.ProcessHandle {
    // ===== PUBLIC API (what process authors see) =====
    
    // Process identity
    protected ProcessId pid;
    protected ContextPath contextPath;
    protected ProcessId parentPid;
    
    // Registries
    protected ProcessRegistry processRegistry;
    
    // Lifecycle state
    private volatile boolean alive = true;
    private volatile boolean killed = false;
    private volatile long startTime;
    private volatile long endTime;
    
    /**
     * Main execution method - implement in subclasses.
     * This runs in parallel with message handling.
     */
    public abstract void run();
    
    /**
     * Handle incoming message - MAIN METHOD TO OVERRIDE
     * 
     * This is called for each message delivered to this process.
     * Executed on the subscriber's executor thread (configurable).
     * 
     * @param packet The message to handle
     */
    protected abstract void handleMessage(RoutedPacket packet);
    
    /**
     * Configure backpressure behavior.
     * Override to control how fast messages are consumed.
     * 
     * Default: UNBOUNDED (no backpressure, maximum throughput)
     */
    protected BackpressureStrategy getBackpressureStrategy() {
        return BackpressureStrategy.UNBOUNDED;
    }
    
    /**
     * Optional: Called when process starts (before run())
     */
    protected void onStart() {
        // Override in subclasses
    }
    
    /**
     * Optional: Called when process ends (after run())
     */
    protected void onStop() {
        // Override in subclasses
    }
    
    /**
     * Optional: Called when message stream has an error
     */
    protected void onStreamError(Throwable error) {
        System.err.println("Process " + pid + " stream error: " + error.getMessage());
        error.printStackTrace();
    }
    
    /**
     * Optional: Called when message stream completes
     */
    protected void onStreamComplete() {
        // Default: do nothing
    }
    
    /**
     * Send message to another process by ID
     */
    protected final boolean sendToProcess(ProcessId targetPid, RoutedPacket packet) {
        return processRegistry.sendToProcess(targetPid, packet);
    }
    
    /**
     * Send message to another process by path
     */
    protected final boolean sendToProcess(ContextPath targetPath, RoutedPacket packet) {
        return processRegistry.sendToProcess(targetPath, packet);
    }
    
    /**
     * Send message to parent process
     */
    protected final boolean sendToParent(RoutedPacket packet) {
        if (parentPid == null) return false;
        return processRegistry.sendToParent(pid, packet);
    }
    
    /**
     * Broadcast message to all children
     */
    protected final void broadcastToChildren(RoutedPacket packet) {
        processRegistry.broadcastToChildren(pid, packet);
    }
    
    /**
     * Spawn a child process
     */
    protected final ProcessId spawnChild(Process childProcess, String childName) {
        ContextPath childPath = contextPath.append(childName);
        return spawnChild(childProcess, childPath);
    }
    
    /**
     * Spawn a child process with explicit path
     */
    protected final ProcessId spawnChild(Process childProcess, ContextPath childPath) {
        // Register child
        ProcessId childPid = processRegistry.registerProcess(childProcess, childPath, pid);
        childProcess.initialize(childPid, childPath, pid);
        
        // Start child in new thread
        Thread childThread = new Thread(() -> {
            try {
                childProcess.onStart();
                childProcess.run();
            } catch (Exception e) {
                System.err.println("Process " + childPid + " error: " + e.getMessage());
                e.printStackTrace();
            } finally {
                childProcess.onStop();
                childProcess.complete();
            }
        }, "Process-" + childPid.asInt());
        
        childThread.setDaemon(true);
        childThread.start();
        
        return childPid;
    }
    
    /**
     * Kill this process
     */
    public final void kill() {
        killed = true;
        alive = false;
    }
    
    /**
     * Check if process was killed
     */
    public final boolean isKilled() {
        return killed;
    }
    
    /**
     * Check if process is alive
     */
    @Override
    public final boolean isAlive() {
        return alive;
    }
    
    /**
     * Get process ID
     */
    public final ProcessId getPid() {
        return pid;
    }
    
    /**
     * Get context path
     */
    public final ContextPath getContextPath() {
        return contextPath;
    }
    
    /**
     * Get parent process ID
     */
    public final ProcessId getParentPid() {
        return parentPid;
    }
    
    /**
     * Get uptime in milliseconds
     */
    public final long getUptimeMillis() {
        if (!alive) {
            return endTime - startTime;
        }
        return System.currentTimeMillis() - startTime;
    }
    
    /**
     * Get process info for debugging
     */
    @Override
    public String getInfo() {
        return String.format("Process{pid=%s, path=%s, alive=%s, uptime=%dms}",
            pid, contextPath, alive, getUptimeMillis());
    }
    
    // ===== INTERNAL API (hidden from process authors) =====
    
    private final ProcessSubscriber subscriber = new ProcessSubscriber();
    
    /**
     * Initialize process with identity
     * Called by ProcessRegistry during registration
     */
    final void initialize(ProcessId pid, ContextPath contextPath, ProcessId parentPid) {
        this.pid = pid;
        this.contextPath = contextPath;
        this.parentPid = parentPid;
        this.processRegistry = ProcessRegistry.getInstance();
        this.startTime = System.currentTimeMillis();
    }
    
    /**
     * Get the Flow.Subscriber for this process
     * Used by ProcessRegistry to wire up message delivery
     */
    public final Flow.Subscriber<RoutedPacket> getSubscriber() {
        return subscriber;
    }
    
    /**
     * Complete process execution
     */
    final void complete() {
        alive = false;
        endTime = System.currentTimeMillis();
        processRegistry.unregisterProcess(pid);
    }
    
    /**
     * Legacy interface implementation (for non-reactive code)
     * Routes through the reactive stream
     */
    @Override
    public final boolean sendMessage(RoutedPacket packet) {
        // This shouldn't be called directly - messages go through publisher
        // But we implement it for compatibility
        return processRegistry.sendToProcess(pid, packet);
    }
    
    // ===== REACTIVE STREAMS IMPLEMENTATION =====
    
    /**
     * Internal subscriber that handles Flow API complexity
     * Process authors never see this!
     */
    private class ProcessSubscriber implements Flow.Subscriber<RoutedPacket> {
        private Flow.Subscription subscription;
        private final AtomicLong pending = new AtomicLong(0);
        private final BackpressureStrategy strategy = getBackpressureStrategy();
        
        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            
            if (this.subscription != null) {
                // Already subscribed - cancel duplicate
                subscription.cancel();
                return;
            }
            
            this.subscription = subscription;
            requestMore();
        }
        
        @Override
        public void onNext(RoutedPacket packet) {
            if (!alive) {
                // Process is dead, ignore message
                return;
            }
            
            pending.decrementAndGet();
            
            try {
                handleMessage(packet);
            } catch (Exception e) {
                onError(e);
            }
            
            // Auto-request more based on strategy
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
            if (subscription == null) return;
            
            long amount = getRequestAmount(strategy);
            pending.addAndGet(amount);
            subscription.request(amount);
        }
        
        private boolean shouldRequestMore() {
            if (strategy == BackpressureStrategy.UNBOUNDED) {
                return false; // Already requested infinite
            }
            
            long threshold = getThreshold(strategy);
            return pending.get() < threshold;
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
                case BUFFERED_1000 -> 100;  // Refill at 10%
                case BUFFERED_100 -> 10;    // Refill at 10%
                case ONE_AT_A_TIME -> 0;    // Request after each
            };
        }
    }
    
    /**
     * Backpressure strategies for message delivery
     */
    public enum BackpressureStrategy {
        /**
         * No backpressure - request all messages immediately.
         * Use for: Fast processes that can keep up with any rate
         * Pros: Maximum throughput
         * Cons: No flow control
         */
        UNBOUNDED,
        
        /**
         * Request messages in batches of 1000.
         * Use for: Most processes with moderate throughput
         * Pros: Good balance of throughput and memory
         * Cons: Still consumes significant memory
         */
        BUFFERED_1000,
        
        /**
         * Request messages in batches of 100.
         * Use for: Processes with slower processing or limited memory
         * Pros: Lower memory footprint
         * Cons: Slightly reduced throughput
         */
        BUFFERED_100,
        
        /**
         * Request one message at a time.
         * Use for: Very slow processes or when strict ordering matters
         * Pros: Minimum memory, maximum backpressure
         * Cons: Lowest throughput
         */
        ONE_AT_A_TIME
    }
    
    @Override
    public String toString() {
        return getInfo();
    }
}