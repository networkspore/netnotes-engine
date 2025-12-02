package io.netnotes.engine.io.process;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;

/**
 * ProcessRegistryInterface - Full-featured interface matching FlowProcessService
 * 
 * This interface now exposes ALL capabilities of FlowProcessService.
 * Different implementations can restrict access, but the base capability is complete.
 */
public interface ProcessRegistryInterface {
    
    // ===== REGISTRATION =====
    
    /**
     * Register a process at an explicit path
     * 
     * Path is INDEPENDENT of parent relationship.
     * Parent relationship is for hierarchy tracking only.
     * 
     * @param process Process to register
     * @param path Explicit context path (e.g., "system/services/auth")
     * @param parentPath Parent's path for hierarchy (e.g., "system"), can be null
     * @param interfaceForNewProcess Interface to give the new process
     * @return The registered path
     */
    ContextPath registerProcess(
        FlowProcess process, 
        ContextPath path, 
        ContextPath parentPath, 
        ProcessRegistryInterface interfaceForNewProcess
    );
    
    /**
     * CONVENIENCE: Register child with auto-computed path
     * 
     * Path = parentPath + "/" + child.getName()
     * 
     * This is the "ease of use" feature - not a requirement!
     */
    default ContextPath registerChild(ContextPath parentPath, FlowProcess child) {
        ContextPath childPath = parentPath.append(child.getName());
        return registerProcess(child, childPath, parentPath, this);
    }
    
    /**
     * Unregister a process
     */
    void unregisterProcess(ContextPath path);
    
    // ===== DIRECT LOOKUPS =====
    
    /**
     * Get process by exact path
     */
    FlowProcess getProcess(ContextPath path);
    
    /**
     * Check if process exists at exact path
     */
    boolean exists(ContextPath path);
    
    // ===== PATH-BASED QUERIES =====
    
    /**
     * Find all processes under a path prefix
     * 
     * Searches by path string matching - NO process needs to exist at prefix!
     * 
     * Example:
     *   Processes: ["system/services/auth", "system/services/logging"]
     *   Query: "system/services"
     *   Result: Both processes (even though no process at "system/services")
     */
    List<FlowProcess> findByPathPrefix(ContextPath prefix);
    
    /**
     * Get all registered paths
     */
    Set<ContextPath> getAllPaths();
    
    /**
     * Find processes matching a path pattern
     */
    default List<FlowProcess> findByPattern(String pattern) {
        // Default: delegate to findByPathPrefix
        return findByPathPrefix(ContextPath.parse(pattern));
    }
    
    // ===== HIERARCHY QUERIES =====
    
    /**
     * Get parent path (based on hierarchy, not path string)
     */
    ContextPath getParent(ContextPath childPath);
    
    /**
     * Get children paths (based on hierarchy)
     */
    Set<ContextPath> getChildren(ContextPath parentPath);
    
    /**
     * Get all children paths under caller
     */
    default List<ContextPath> getChildren() {
        throw new UnsupportedOperationException(
            "This method requires a caller context path"
        );
    }
    
    /**
     * Find children by type
     */
    <T extends FlowProcess> List<T> findChildrenByType(ContextPath parentPath, Class<T> type);
    
    /**
     * Find children by name
     */
    List<FlowProcess> findChildrenByName(ContextPath parentPath, String name);
    
    /**
     * CONVENIENCE: Get single child by name
     */
    default FlowProcess getChildProcess(ContextPath parentPath, String childName) {
        List<FlowProcess> children = findChildrenByName(parentPath, childName);
        return children.isEmpty() ? null : children.get(0);
    }
    
    // ===== LIFECYCLE =====
    
    /**
     * Start a process
     */
    CompletableFuture<Void> startProcess(ContextPath path);
    
    /**
     * CONVENIENCE: Start child
     */
    default CompletableFuture<Void> startChild(ContextPath parentPath, ContextPath childPath) {
        return startProcess(childPath);
    }
    
    /**
     * Kill a process
     */
    void killProcess(ContextPath path);
    
    // ===== CONNECTIONS =====
    
    /**
     * Connect processes: downstream subscribes to upstream
     */
    void connect(ContextPath upstreamPath, ContextPath downstreamPath);
    
    /**
     * Disconnect processes
     */
    void disconnect(ContextPath upstreamPath, ContextPath downstreamPath);
    
    /**
     * Get upstream processes
     */
    Set<ContextPath> getUpstreams(ContextPath processPath);
    
    /**
     * Get downstream processes
     */
    Set<ContextPath> getDownstreams(ContextPath processPath);
    
    // ===== STREAM CHANNELS =====
    
    /**
     * Request stream channel to another process
     */
    CompletableFuture<StreamChannel> requestStreamChannel(
        ContextPath callerPath, 
        ContextPath target
    );
    
    /**
     * Check if process supports streaming
     */
    boolean isStreamCapable(ContextPath path);
    
    // ===== REQUEST-REPLY =====
    
    /**
     * Send request to process and await reply
     */
    default CompletableFuture<RoutedPacket> request(
            ContextPath targetPath,
            NoteBytesReadOnly payload,
            Duration timeout) {
        
        throw new UnsupportedOperationException(
            "Request operation not supported by this interface implementation"
        );
    }
    
    // ===== METADATA =====
    
    /**
     * Get registry summary
     */
    default String getSummary() {
        return String.format("Registry with %d processes", getAllPaths().size());
    }
}