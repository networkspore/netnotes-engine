package io.netnotes.engine.core;

import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.noteFiles.NoteFile;
import io.netnotes.engine.noteFiles.notePath.NoteFileService;
import io.netnotes.engine.utils.VirtualExecutors;

/**
 * ScopedAppDataInterface - Path-based access control
 * 
 * - Interface lives at specific paths
 * - Access control via hop validation
 * - Paths determine what's reachable
 * 
 * Example:
 *   Interface at: /system/nodes/runtime/database-node
 *   Alt path: /user/nodes/database-node
 *   
 *   Request: config/settings.json (relative)
 *   → Resolves to: /system/nodes/runtime/database-node/config/settings.json
 *   → canReach? Yes (within basePath)
 *   → ALLOWED
 *   
 *   Request: /user/nodes/database-node/exports/data.csv (absolute)
 *   → canReach from basePath? Check hops...
 *   → canReach from altPath? Check hops...
 *   → ALLOWED (reachable from altPath)
 *   
 *   Request: /system/nodes/runtime/other-node/data.json (absolute)
 *   → canReach from basePath? Check hops...
 *     → Ownership check fails (database-node ≠ other-node)
 *   → DENIED
 */
public class ScopedAppDataInterface implements AppDataInterface {
    
    private final NoteFileService noteFileService;
    private final ContextPath basePath;     // Primary location (e.g., runtime)
    private final ContextPath altPath;      // Alternative location (e.g., user)
    
    /**
     * Constructor - paths determine access
     * 
     * @param noteFileService File service for actual file operations
     * @param basePath Primary path where this interface lives
     * @param altPath Alternative path (optional, can be null)
     */
    public ScopedAppDataInterface(
            NoteFileService noteFileService,
            ContextPath basePath,
            ContextPath altPath) {
        
        if (noteFileService == null) {
            throw new IllegalArgumentException("NoteFileService cannot be null");
        }
        
        if (basePath == null) {
            throw new IllegalArgumentException("basePath cannot be null");
        }
        
        if (!basePath.isAbsolute()) {
            throw new IllegalArgumentException("basePath must be absolute: " + basePath);
        }
        
        if (altPath != null && !altPath.isAbsolute()) {
            throw new IllegalArgumentException("altPath must be absolute: " + altPath);
        }
        
        this.noteFileService = noteFileService;
        this.basePath = basePath;
        this.altPath = altPath;
    }
    
    // =========================================================================
    // PUBLIC API
    // =========================================================================
    
    @Override
    public CompletableFuture<NoteFile> getNoteFile(ContextPath requestedPath) {
        if (requestedPath == null) {
            return CompletableFuture.failedFuture(
                new NullPointerException("Path is null"));
        }
        
        return CompletableFuture.supplyAsync(() -> {
            
            // Resolve and validate path via hop validation
            PathResolution resolution = resolvePath(requestedPath);
            
            if (resolution == null || !resolution.allowed) {
                throw new SecurityException(String.format(
                    "Cannot access '%s' from base '%s'%s",
                    requestedPath,
                    basePath,
                    altPath != null ? " or alt '" + altPath + "'" : ""
                ));
            }
            
            System.out.println(String.format(
                "[ScopedInterface] %s → %s (via %s)",
                requestedPath,
                resolution.resolvedPath,
                resolution.resolvedVia
            ));
            
            return resolution.resolvedPath.getSegments();
            
        }, VirtualExecutors.getVirtualExecutor())
        .thenCompose(segments -> noteFileService.getNoteFile(segments));
    }
    
    @Override
    public void shutdown() {
        // Scoped interfaces don't own resources
        // Nothing to shutdown
    }
    
    // =========================================================================
    // PATH RESOLUTION & HOP VALIDATION
    // =========================================================================
    
    /**
     * Resolve requested path to absolute path
     * 
     * Strategy:
     * 1. If RELATIVE: scope to basePath (or altPath)
     * 2. If ABSOLUTE: verify reachable via hop validation
     * 
     * @param requestedPath Path requested by caller
     * @return PathResolution with resolved path, or null if denied
     */
    private PathResolution resolvePath(ContextPath requestedPath) {
        
        // === RELATIVE PATH ===
        if (requestedPath.isRelative()) {
            // Scope to basePath first
            ContextPath scopedToBase = basePath.append(requestedPath);
            
            // Verify no path traversal attacks
            if (basePath.canReach(scopedToBase)) {
                return new PathResolution(scopedToBase, true, "basePath");
            }
            
            // Try altPath if available
            if (altPath != null) {
                ContextPath scopedToAlt = altPath.append(requestedPath);
                
                if (altPath.canReach(scopedToAlt)) {
                    return new PathResolution(scopedToAlt, true, "altPath");
                }
            }
            
            // Cannot scope safely
            return new PathResolution(requestedPath, false, "relative-unsafe");
        }
        
        // === ABSOLUTE PATH ===
        // Check if reachable from basePath
        if (basePath.canReach(requestedPath)) {
            return new PathResolution(requestedPath, true, "basePath-hop-valid");
        }
        
        // Check if reachable from altPath
        if (altPath != null && altPath.canReach(requestedPath)) {
            return new PathResolution(requestedPath, true, "altPath-hop-valid");
        }
        
        // Not reachable
        System.err.println(String.format(
            "[Security] Hop validation failed: '%s' not reachable from '%s'%s",
            requestedPath,
            basePath,
            altPath != null ? " or '" + altPath + "'" : ""
        ));
        
        return new PathResolution(requestedPath, false, "hop-validation-failed");
    }
    
    // =========================================================================
    // HELPER CLASSES
    // =========================================================================
    
    /**
     * Result of path resolution
     */
    private static class PathResolution {
        final ContextPath resolvedPath;
        final boolean allowed;
        final String resolvedVia;  // For debugging
        
        PathResolution(ContextPath resolvedPath, boolean allowed, String resolvedVia) {
            this.resolvedPath = resolvedPath;
            this.allowed = allowed;
            this.resolvedVia = resolvedVia;
        }
    }
    
    // =========================================================================
    // GETTERS (for debugging)
    // =========================================================================
    
    public ContextPath getBasePath() {
        return basePath;
    }
    
    public ContextPath getAltPath() {
        return altPath;
    }
    
    @Override
    public String toString() {
        return String.format(
            "ScopedInterface[base=%s, alt=%s]",
            basePath,
            altPath
        );
    }
}