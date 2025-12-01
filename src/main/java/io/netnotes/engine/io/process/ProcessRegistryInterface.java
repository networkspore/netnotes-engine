package io.netnotes.engine.io.process;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiPredicate;

import io.netnotes.engine.io.ContextPath;

public interface ProcessRegistryInterface {
    
    /**
     * Register a child process under caller's path
     * 
     * Path = caller.path + child.name
     * 
     * @param process Process to register (has name already)
     * @return Full path where child was registered
     */
    ContextPath registerChild(FlowProcess process);
    
    /**
     * Start a child process
     */
    CompletableFuture<Void> startChild(ContextPath path);
    
    /**
     * Get a process (read-only)
     */
    FlowProcess getProcess(ContextPath path);
    
    boolean exists(ContextPath path);
    
    /**
     * Request stream channel to another process
     */
    CompletableFuture<StreamChannel> requestStreamChannel(ContextPath target);
    
    /**
     * Get children under caller's path
     */
    List<ContextPath> getChildren();
    
    <T extends FlowProcess> List<T> findChildrenByType(Class<T> type);
    
    /**
     * Connect processes: downstream subscribes to upstream
     */
    void connect(ContextPath upstreamPath, ContextPath downstreamPath);
    
    /**
     * Start a process (begin execution)
     */
    CompletableFuture<Void> startProcess(ContextPath path);
    
    /**
     * Unregister a process
     */
    void unregisterProcess(ContextPath path);
    
    FlowProcess getChildProcess(String childName);

     /**
     * Create child interface with additional restrictions
     * 
     * Caller's interface becomes the parent constraint.
     * New interface has (caller's policy AND new policy).
     */
    ProcessRegistryInterface createChildInterface(
        ContextPath basePath,
        BiPredicate<ContextPath, ContextPath> additionalPolicy
    );
}

