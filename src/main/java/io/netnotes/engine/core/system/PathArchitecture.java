package io.netnotes.engine.core.system;

import io.netnotes.engine.io.ContextPath;

/**
 * PathArchitecture - Unified path structure for entire system
 * 
 * PRINCIPLES:
 * 1. FlowProcess paths ≠ File system paths (separate namespaces)
 * 2. Every entity has ONE canonical identifier
 * 3. Sandboxing enforced at AppDataInterface creation
 * 4. Path structure reflects ownership hierarchy
 * 
 * =============================================================================
 * FLOWPROCESS NETWORK PATHS (for routing/messaging)
 * =============================================================================
 * 
 * /system/
 *   ├─ base                          # BaseSystemProcess
 *   │   ├─ gui-keyboard              # KeyboardInput (always present)
 *   │   ├─ io-daemon                 # IODaemon (if installed)
 *   │   └─ sessions/
 *   │       └─ {session-id}/         # SystemSessionProcess
 *   │           ├─ menu-navigator    # MenuNavigatorProcess
 *   │           ├─ node-manager      # NodeManagerProcess
 *   │           └─ progress-tracker  # ProgressTrackingProcess
 *   │
 *   └─ controller/                   # NodeController
 *       └─ nodes/
 *           └─ {package-id}/         # NodeFlowAdapter (per node instance)
 * 
 * =============================================================================
 * FILE SYSTEM PATHS (encrypted NoteFiles)
 * =============================================================================
 * 
 * /system/
 *   ├─ bootstrap/
 *   │   └─ config                    # Bootstrap configuration (NoteFile)
 *   │
 *   ├─ sessions/
 *   │   └─ {session-id}/             # Session-scoped data (NoteFile + children)
 *   │       └─ temp/                 # Temporary session files (NoteFile + children)
 *   │
 *   └─ nodes/
 *       ├─ registry/                 # Registry data (NoteFile + children)
 *       │   ├─ installed             # InstalledPackage list (NoteFile)
 *       │   └─ repositories          # Repository list (NoteFile)
 *       │
 *       ├─ packages/                 # Package storage (NoteFile + children, READ-ONLY to nodes)
 *       │   └─ {package-id}/         # Package root (NoteFile + children)
 *       │       └─ {version}/        # Version root (NoteFile + children)
 *       │           ├─ manifest      # Manifest data (NoteFile)
 *       │           ├─ bundle        # OSGi bundle JAR (NoteFile)
 *       │           └─ resources/    # Resources directory (NoteFile + children)
 *       │
 *       └─ runtime/                  # Per-instance runtime data (NoteFile + children)
 *           └─ {instance-id}/        # Instance root (NoteFile + children)
 *               ├─ config/           # Config directory (NoteFile + children)
 *               ├─ data/             # Data directory (NoteFile + children)
 *               └─ cache/            # Cache directory (NoteFile + children)
 * 
 * /user/
 *   └─ nodes/
 *       └─ {instance-id}/            # ⬅️ User data per instance
 *           └─ (user-visible files)
 * 
 * =============================================================================
 * KEY DESIGN DECISIONS
 * =============================================================================
 * 
 * 1. PACKAGE ID vs INSTANCE ID
 *    - Package ID: "com.example.chatbot" (identifies CODE)
 *    - Instance ID: "chatbot-personal" (identifies RUNNING INSTANCE)
 *    - Why: User can run MULTIPLE instances of same package
 *    - Example: Run 3 different chatbots with different configs
 * 
 * 2. SANDBOXING ENFORCEMENT
 *    - AppDataInterface created with BASE PATH
 *    - All getNoteFile() calls automatically scoped
 *    - Caller CANNOT escape their base path
 * 
 * 3. SESSION ISOLATION
 *    - Each SystemSessionProcess has unique session-id
 *    - Session data is in /system/sessions/{session-id}/
 *    - Sessions can spawn processes with sub-paths
 * 
 * 4. NODE ISOLATION
 *    - Each node instance has unique instance-id
 *    - Runtime data: /system/nodes/runtime/{instance-id}/
 *    - User data: /user/nodes/{instance-id}/
 *    - Nodes CANNOT access other instances' data
 * 
 * =============================================================================
 * IDENTIFIER STRUCTURE
 * =============================================================================
 * 
 * Package ID: Reverse domain notation
 *   - Format: "com.company.product.component"
 *   - Example: "io.netnotes.nodes.chatbot"
 *   - Used for: Installation, updates, version tracking
 * 
 * Instance ID: User-friendly + unique
 *   - Format: "{user-chosen-name}" or "{package-name}-{uuid}"
 *   - Example: "personal-chatbot" or "chatbot-a7f3c2d1"
 *   - Used for: Runtime identification, file paths
 * 
 * Session ID: UUID-based
 *   - Format: "session-{uuid}"
 *   - Example: "session-f4e2a8b9"
 *   - Used for: Session isolation
 * 
 * FlowProcess Path: Hierarchical context
 *   - Format: "/system/controller/nodes/{instance-id}"
 *   - Used for: Message routing
 */
public class PathArchitecture {
    
    // =========================================================================
    // FLOWPROCESS PATHS
    // =========================================================================
    
    public static class FlowPaths {
        
        // System root
        public static final ContextPath SYSTEM = ContextPath.of("system");
        
        // BaseSystemProcess
        public static final ContextPath BASE = SYSTEM.append("base");
        
        // Sessions
        public static final ContextPath SESSIONS = BASE.append("sessions");
        
        public static ContextPath getSessionPath(String sessionId) {
            return SESSIONS.append(sessionId);
        }
        
        // NodeController
        public static final ContextPath CONTROLLER = SYSTEM.append("controller");
        public static final ContextPath CONTROLLER_NODES = CONTROLLER.append("nodes");
        
        public static ContextPath getNodeFlowPath(String instanceId) {
            return CONTROLLER_NODES.append(instanceId);
        }
    }
    
    // =========================================================================
    // FILE SYSTEM PATHS
    // =========================================================================
    
    public static class FilePaths {
        
        // System root
        public static final ContextPath SYSTEM = ContextPath.of("system");
        public static final ContextPath USER = ContextPath.of("user");
        
        // Bootstrap
        public static final ContextPath BOOTSTRAP = SYSTEM.append("bootstrap");
        public static final ContextPath BOOTSTRAP_CONFIG = BOOTSTRAP.append("config.dat");
        
        // Sessions
        public static final ContextPath SESSIONS = SYSTEM.append("sessions");
        
        public static ContextPath getSessionPath(String sessionId) {
            return SESSIONS.append(sessionId);
        }
        
        public static ContextPath getSessionTempPath(String sessionId) {
            return getSessionPath(sessionId).append("temp");
        }
        
        // Nodes
        public static final ContextPath NODES = SYSTEM.append("nodes");
        
        // Registry
        public static final ContextPath REGISTRY = NODES.append("registry");
        public static final ContextPath INSTALLED_PACKAGES = REGISTRY.append("installed.dat");
        public static final ContextPath REPOSITORIES = REGISTRY.append("repositories.dat");
        
        // Packages (read-only to nodes)
        public static final ContextPath PACKAGES = NODES.append("packages");
        
        public static ContextPath getPackagePath(String packageId, String version) {
            return PACKAGES.append(packageId, version);
        }
        
        public static ContextPath getPackageManifest(String packageId, String version) {
            return getPackagePath(packageId, version).append("manifest.dat");
        }
        
        public static ContextPath getPackageJar(String packageId, String version) {
            return getPackagePath(packageId, version).append("package.jar");
        }
        
        // Runtime (per-instance, read-write to owning node)
        public static final ContextPath RUNTIME = NODES.append("runtime");
        
        public static ContextPath getRuntimePath(String instanceId) {
            return RUNTIME.append(instanceId);
        }
        
        public static ContextPath getRuntimeConfig(String instanceId) {
            return getRuntimePath(instanceId).append("config");
        }
        
        public static ContextPath getRuntimeData(String instanceId) {
            return getRuntimePath(instanceId).append("data");
        }
        
        public static ContextPath getRuntimeCache(String instanceId) {
            return getRuntimePath(instanceId).append("cache");
        }
        
        // User data (per-instance)
        public static final ContextPath USER_NODES = USER.append("nodes");
        
        public static ContextPath getUserNodePath(String instanceId) {
            return USER_NODES.append(instanceId);
        }
    }
    
    // =========================================================================
    // IDENTIFIER GENERATION
    // =========================================================================
    
    public static class Identifiers {
        
        /**
         * Generate session ID
         * Format: session-{uuid}
         */
        public static String generateSessionId() {
            return "session-" + java.util.UUID.randomUUID().toString()
                .replace("-", "").substring(0, 12);
        }
        
        /**
         * Generate instance ID from package ID
         * Format: {package-name}-{uuid}
         * 
         * Example: io.netnotes.nodes.chatbot → chatbot-a7f3c2d1
         */
        public static String generateInstanceId(String packageId) {
            String packageName = extractPackageName(packageId);
            String uuid = java.util.UUID.randomUUID().toString()
                .replace("-", "").substring(0, 8);
            return packageName + "-" + uuid;
        }
        
        /**
         * Create user-friendly instance ID
         * Format: {user-name}
         * 
         * Must be validated (no slashes, dots, special chars)
         */
        public static String createUserInstanceId(String userChoice) {
            // Validate: alphanumeric + hyphens only
            if (!userChoice.matches("^[a-zA-Z0-9-]+$")) {
                throw new IllegalArgumentException(
                    "Instance ID must be alphanumeric with hyphens only");
            }
            return userChoice.toLowerCase();
        }
        
        /**
         * Extract package name from package ID
         * io.netnotes.nodes.chatbot → chatbot
         */
        private static String extractPackageName(String packageId) {
            String[] parts = packageId.split("\\.");
            return parts[parts.length - 1];
        }
        
        /**
         * Validate package ID format
         * Must be reverse domain notation
         */
        public static boolean isValidPackageId(String packageId) {
            return packageId.matches("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$");
        }
        
        /**
         * Validate instance ID format
         */
        public static boolean isValidInstanceId(String instanceId) {
            return instanceId.matches("^[a-zA-Z0-9-]+$");
        }
    }
    
    // =========================================================================
    // ACCESS CONTROL
    // =========================================================================
    
    public static class Access {
        
        /**
         * Check if path is accessible to a session
         */
        public static boolean canSessionAccess(String sessionId, ContextPath path, boolean write) {
            ContextPath sessionPath = FilePaths.getSessionPath(sessionId);
            
            // Sessions can access their own path
            if (path.startsWith(sessionPath)) {
                return true;
            }
            
            // Sessions can read system paths (not write)
            if (!write && path.startsWith(FilePaths.SYSTEM)) {
                return true;
            }
            
            return false;
        }
        
        /**
         * Check if path is accessible to a node instance
         */
        public static boolean canNodeAccess(String instanceId, ContextPath path, boolean write) {
            ContextPath runtimePath = FilePaths.getRuntimePath(instanceId);
            ContextPath userPath = FilePaths.getUserNodePath(instanceId);
            
            // Nodes can access their own runtime data
            if (path.startsWith(runtimePath)) {
                return true;
            }
            
            // Nodes can access their own user data
            if (path.startsWith(userPath)) {
                return true;
            }
            
            // Nodes can READ package storage (not write)
            if (!write && path.startsWith(FilePaths.PACKAGES)) {
                return true;
            }
            
            return false;
        }
        
        /**
         * Check if path is accessible to system code
         */
        public static boolean canSystemAccess(ContextPath path, boolean write) {
            // System code has unrestricted access
            return true;
        }
    }
}