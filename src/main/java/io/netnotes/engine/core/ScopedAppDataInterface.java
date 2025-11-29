package io.netnotes.engine.core;

import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.core.system.PathArchitecture;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.noteFiles.NoteFile;
import io.netnotes.engine.noteFiles.notePath.NoteFileService;
import io.netnotes.engine.utils.VirtualExecutors;

/**
 * ScopedAppDataInterface - Automatically sandboxed file access
 * 
 * DESIGN PRINCIPLES:
 * 1. Created with a BASE PATH (caller's root directory)
 * 2. ENTITY ID (who owns this interface)
 * 3. All getNoteFile() calls automatically scoped
 * 4. Caller CANNOT escape their base path
 * 5. Validation happens at interface layer (not in NoteFileService)
 * 
 * USAGE PATTERNS:
 * 
 * // System code (unrestricted)
 * AppDataInterface systemInterface = appData.getSystemInterface("installation-registry");
 * systemInterface.getNoteFile(ContextPath.of("system", "nodes", "registry", "installed.dat"));
 * → Access granted (system has full access)
 * 
 * // Session (scoped to session path)
 * AppDataInterface sessionInterface = appData.getSessionInterface(sessionId);
 * sessionInterface.getNoteFile(ContextPath.of("temp", "upload.dat"));
 * → Resolved to: /system/sessions/{sessionId}/temp/upload.dat
 * 
 * // Node instance (scoped to runtime + user paths)
 * AppDataInterface nodeInterface = appData.getNodeInterface(instanceId);
 * nodeInterface.getNoteFile(ContextPath.of("config", "settings.json"));
 * → Resolved to: /system/nodes/runtime/{instanceId}/config/settings.json
 * 
 * // Node trying to access another node's data
 * nodeInterface.getNoteFile(ContextPath.of("..", "other-node", "data.dat"));
 * → Access DENIED (path traversal blocked)
 */
public class ScopedAppDataInterface implements AppDataInterface {
    
    private final NoteFileService noteFileService;
    private final String entityId;          // Who owns this interface
    private final EntityType entityType;    // What type of entity
    private final ContextPath basePath;     // Root path for this entity
    private final ContextPath altPath;      // Alternative path (e.g., user data for nodes)
    private final boolean allowSystemRead;  // Can read system paths?
    
    /**
     * Entity types with different access patterns
     */
    public enum EntityType {
        SYSTEM,     // Unrestricted access
        SESSION,    // Scoped to session path, can read system
        NODE        // Scoped to runtime + user paths, can read packages
    }
    
    /**
     * Create system interface (unrestricted)
     */
    public static ScopedAppDataInterface createSystemInterface(
            String systemArea,
            NoteFileService noteFileService) {
        
        return new ScopedAppDataInterface(
            noteFileService,
            systemArea,
            EntityType.SYSTEM,
            null,  // No base path restriction
            null,
            true
        );
    }
    
    /**
     * Create session interface (scoped to session path)
     */
    public static ScopedAppDataInterface createSessionInterface(
            String sessionId,
            NoteFileService noteFileService) {
        
        ContextPath sessionPath = PathArchitecture.FilePaths.getSessionPath(sessionId);
        
        return new ScopedAppDataInterface(
            noteFileService,
            sessionId,
            EntityType.SESSION,
            sessionPath,
            null,
            true  // Sessions can read system paths
        );
    }
    
    /**
     * Create node interface (scoped to runtime + user paths)
     */
    public static ScopedAppDataInterface createNodeInterface(
            String instanceId,
            NoteFileService noteFileService) {
        
        ContextPath runtimePath = PathArchitecture.FilePaths.getRuntimePath(instanceId);
        ContextPath userPath = PathArchitecture.FilePaths.getUserNodePath(instanceId);
        
        return new ScopedAppDataInterface(
            noteFileService,
            instanceId,
            EntityType.NODE,
            runtimePath,     // Primary: runtime data
            userPath,        // Alternative: user data
            true             // Nodes can read packages
        );
    }
    
    /**
     * Private constructor
     */
    private ScopedAppDataInterface(
            NoteFileService noteFileService,
            String entityId,
            EntityType entityType,
            ContextPath basePath,
            ContextPath altPath,
            boolean allowSystemRead) {
        
        this.noteFileService = noteFileService;
        this.entityId = entityId;
        this.entityType = entityType;
        this.basePath = basePath;
        this.altPath = altPath;
        this.allowSystemRead = allowSystemRead;
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
            
            // Validate and resolve path
            PathResolution resolution = resolvePath(requestedPath, false);
            
            if (resolution == null || !resolution.allowed) {
                throw new SecurityException(String.format(
                    "Entity '%s' (type: %s) cannot access path: %s",
                    entityId, entityType, requestedPath
                ));
            }
            
            System.out.println(String.format(
                "[ScopedInterface:%s] %s → %s",
                entityId,
                requestedPath,
                resolution.resolvedPath
            ));
            
            return resolution.resolvedPath.getSegments();
            
        }, VirtualExecutors.getVirtualExecutor())
        .thenCompose(segments -> noteFileService.getNoteFile(segments));
    }
    
    @Override
    public void shutdown() {
        // Entities cannot shutdown the system
        throw new UnsupportedOperationException(
            "Entity '" + entityId + "' cannot shutdown AppData");
    }
    
    // =========================================================================
    // PATH RESOLUTION & VALIDATION
    // =========================================================================
    
    /**
     * Resolve and validate a requested path
     * 
     * Resolution strategy:
     * 
     * ABSOLUTE PATH (starts with root segment: system, user):
     *   1. SYSTEM entities: allowed if access rules permit
     *   2. SESSION entities: allowed if within session path or read-only system
     *   3. NODE entities: allowed if within runtime/user paths or read-only packages
     *   4. If access denied → deny
     * 
     * RELATIVE PATH (doesn't start with root segment):
     *   1. Try scoping to base path (primary scope)
     *   2. If base denied, try alt path (for nodes: user data)
     *   3. If all fail → deny access
     * 
     * @param requestedPath Path requested by entity
     * @param write Whether this is a write operation
     * @return PathResolution with resolved path and access decision
     */
    private PathResolution resolvePath(ContextPath requestedPath, boolean write) {
        
        // SYSTEM entities: unrestricted
        if (entityType == EntityType.SYSTEM) {
            // System can request any path (absolute or relative)
            // If relative, scope to base path (if exists)
            if (requestedPath.isRelative() && basePath != null) {
                ContextPath scoped = basePath.append(requestedPath);
                return new PathResolution(scoped, true, "system-scoped");
            }
            return new PathResolution(requestedPath, true, "system-absolute");
        }
        
        // === ABSOLUTE PATH HANDLING ===
        if (requestedPath.isAbsolute()) {
            // Entity is requesting a specific absolute path
            // Check if they have permission to access it
            
            if (canAccessAbsolute(requestedPath, write)) {
                return new PathResolution(requestedPath, true, "absolute-allowed");
            } else {
                // Absolute path requested but access denied
                System.err.println(String.format(
                    "[Security] Entity '%s' (%s) denied absolute access to: %s (write: %s)",
                    entityId, entityType, requestedPath, write
                ));
                return new PathResolution(requestedPath, false, "absolute-denied");
            }
        }
        
        // === RELATIVE PATH HANDLING ===
        // Entity is requesting a path relative to their scope
        // We automatically scope it to their permitted areas
        
        // Try primary base path first
        if (basePath != null) {
            ContextPath scoped = basePath.append(requestedPath);
            
            if (canAccess(scoped, write)) {
                return new PathResolution(scoped, true, "relative-to-base");
            }
        }
        
        // Try alternative path (for nodes: user data area)
        if (altPath != null) {
            ContextPath scoped = altPath.append(requestedPath);
            
            if (canAccess(scoped, write)) {
                return new PathResolution(scoped, true, "relative-to-alt");
            }
        }
        
        // No valid scope found
        System.err.println(String.format(
            "[Security] Entity '%s' (%s) has no valid scope for relative path: %s",
            entityId, entityType, requestedPath
        ));
        return new PathResolution(requestedPath, false, "no-valid-scope");
    }
    
    /**
     * Check if entity can access an absolute path
     */
    private boolean canAccessAbsolute(ContextPath path, boolean write) {
        switch (entityType) {
            case SYSTEM:
                return true;
                
            case SESSION:
                return PathArchitecture.Access.canSessionAccess(
                    entityId, path, write);
                
            case NODE:
                return PathArchitecture.Access.canNodeAccess(
                    entityId, path, write);
                
            default:
                return false;
        }
    }
    
    /**
     * Check if entity can access a resolved path
     */
    private boolean canAccess(ContextPath path, boolean write) {
        // Same logic as absolute, but for already-resolved paths
        return canAccessAbsolute(path, write);
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
        final String reason;
        
        PathResolution(ContextPath resolvedPath, boolean allowed, String reason) {
            this.resolvedPath = resolvedPath;
            this.allowed = allowed;
            this.reason = reason;
        }
    }
    
    // =========================================================================
    // GETTERS (for debugging)
    // =========================================================================
    
    public String getEntityId() {
        return entityId;
    }
    
    public EntityType getEntityType() {
        return entityType;
    }
    
    public ContextPath getBasePath() {
        return basePath;
    }
    
    public ContextPath getAltPath() {
        return altPath;
    }
    
    @Override
    public String toString() {
        return String.format(
            "ScopedInterface[entity=%s, type=%s, base=%s, alt=%s]",
            entityId, entityType, basePath, altPath
        );
    }
}