package io.netnotes.engine.core.system.control.nodes.osgi;

import org.osgi.framework.*;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;
import org.osgi.util.tracker.ServiceTracker;

import io.netnotes.engine.core.AppData;
import io.netnotes.engine.core.system.control.nodes.INode;
import io.netnotes.engine.core.system.control.nodes.InstalledPackage;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteFiles.NoteFile;

import java.io.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OSGiBundleLoader - Loads and manages OSGi bundles as nodes
 * 
 * ARCHITECTURE:
 * - Single OSGi Framework instance for all nodes
 * - Each package JAR is installed as an OSGi bundle
 * - Bundles export INode service
 * - Loader discovers and returns INode instances
 * 
 * LIFECYCLE:
 * 1. Initialize framework (once)
 * 2. Install bundle from NoteFile
 * 3. Start bundle
 * 4. Get INode service reference
 * 5. Return INode instance
 * 
 * SECURITY:
 * - Bundles run in same JVM but isolated classloaders
 * - No direct file system access (via AppDataInterface only)
 * - Service registry controls inter-bundle communication
 * 
 * RESOURCE MANAGEMENT:
 * - Bundles share framework instance
 * - Framework shutdown cleans all bundles
 * - Bundle stop releases INode service
 */
public class OSGiBundleLoader {
    
    private final AppData appData;
    private Framework framework;
    private final Map<String, Bundle> installedBundles = new ConcurrentHashMap<>();
    
    // OSGi framework initialization state
    private volatile boolean frameworkInitialized = false;
    private final Object frameworkLock = new Object();
    
    public OSGiBundleLoader(AppData appData) {
        this.appData = appData;
    }
    
    /**
     * Initialize OSGi framework (lazy - called on first bundle load)
     */
    private CompletableFuture<Void> initializeFramework() {
        if (frameworkInitialized) {
            return CompletableFuture.completedFuture(null);
        }
        
        return CompletableFuture.runAsync(() -> {
            synchronized (frameworkLock) {
                if (frameworkInitialized) {
                    return;
                }
                
                try {
                    System.out.println("[OSGiBundleLoader] Initializing OSGi framework");
                    
                    // Get FrameworkFactory via ServiceLoader
                    ServiceLoader<FrameworkFactory> factoryLoader = 
                        ServiceLoader.load(FrameworkFactory.class);
                    
                    FrameworkFactory factory = factoryLoader.iterator().next();
                    
                    // Configure framework
                    Map<String, String> config = new HashMap<>();
                    
                    // Storage location for framework state
                    File frameworkStorage = new File(
                        io.netnotes.engine.core.SettingsData.getDataDir(),
                        "osgi-framework"
                    );
                    frameworkStorage.mkdirs();
                    
                    config.put(Constants.FRAMEWORK_STORAGE, 
                        frameworkStorage.getAbsolutePath());
                    
                    // Clean storage on startup (optional - for testing)
                    config.put(Constants.FRAMEWORK_STORAGE_CLEAN, 
                        Constants.FRAMEWORK_STORAGE_CLEAN_ONFIRSTINIT);
                    
                    // Create and start framework
                    framework = factory.newFramework(config);
                    framework.start();
                    
                    frameworkInitialized = true;
                    
                    System.out.println("[OSGiBundleLoader] OSGi framework started");
                    
                } catch (Exception e) {
                    throw new RuntimeException("Failed to initialize OSGi framework", e);
                }
            }
        });
    }
    
    /**
     * Load INode from OSGi bundle package
     * 
     * Process:
     * 1. Ensure framework initialized
     * 2. Install bundle in framework
     * 5. Wait for INode service registration
     * 6. Return INode service instance
     */
    public CompletableFuture<INode> loadBundle(InstalledPackage pkg) {
        return initializeFramework()
            .thenCompose(v -> installBundleFromNoteFile(pkg.getPackageId(), pkg))
            .thenCompose(bundle -> waitForNodeService(pkg.getPackageId(), bundle))
            .whenComplete((inode, ex) -> {
                if (ex != null) {
                    System.err.println("[OSGiBundleLoader] Failed to load bundle: " + 
                        pkg.getName() + " - " + ex.getMessage());
                }
            });
    }
    
    /**
     * Extract bundle JAR from encrypted NoteFile to temp location
     * 
     * OSGi needs a File path, but NoteFiles are encrypted.
     * Solution: Extract to temporary file, install bundle, then delete temp.
     */
    private CompletableFuture<Bundle> installBundleFromNoteFile(
        NoteBytesReadOnly packageId,
        InstalledPackage pkg) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Get JAR path
                ContextPath jarPath = pkg.getInstallPath().append("package.jar");
                
                // Get NoteFile
                NoteFile jarFile = appData.getNoteFileService()
                    .getNoteFile(jarPath.getSegments())
                    .join();
                
                // Load directly from stream
                BundleContext context = framework.getBundleContext();
                
                String location = "notefile://" + packageId;
                
                // Install from encrypted stream - no temp file!
                try (InputStream stream = jarFile.getInputStream()) {
                    Bundle bundle = context.installBundle(location, stream);
                    bundle.start();
                    return bundle;
                }
                
            } catch (Exception e) {
                throw new RuntimeException("Failed to install bundle", e);
            }
        });
    }
    
    /**
     * Wait for INode service registration
     * 
     * Bundle's BundleActivator should register INode service:
     * 
     * public class Activator implements BundleActivator {
     *     public void start(BundleContext context) {
     *         INode node = new MyNodeImpl();
     *         context.registerService(INode.class, node, null);
     *     }
     * }
     */
    private CompletableFuture<INode> waitForNodeService(
            NoteBytesReadOnly packageId, 
            Bundle bundle) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                System.out.println("[OSGiBundleLoader] Waiting for INode service from: " + 
                    packageId);
                
                BundleContext context = framework.getBundleContext();
                
                // Create service filter for this bundle
                String filter = String.format(
                    "(&(objectClass=%s)(bundle.id=%d))",
                    INode.class.getName(),
                    bundle.getBundleId()
                );
                
                // Wait for service (with timeout)
                ServiceReference<?>[] refs = context.getServiceReferences(
                    (String) null, 
                    filter
                );
                
                if (refs != null && refs.length > 0) {
                    INode node = (INode) context.getService(refs[0]);
                    
                    if (node != null) {
                        System.out.println("[OSGiBundleLoader] Found INode service: " + 
                            node.getNodeId());
                        return node;
                    }
                }
                
                // If not found immediately, use ServiceTracker for async discovery
                return waitForServiceWithTracker(context, bundle);
                
            } catch (Exception e) {
                throw new RuntimeException("Failed to get INode service", e);
            }
        });
    }
    
    /**
     * Wait for service using ServiceTracker (with timeout)
     */
    private INode waitForServiceWithTracker(
            BundleContext context, 
            Bundle bundle) throws Exception {
        
        ServiceTracker<INode, INode> tracker = new ServiceTracker<>(
            context,
            INode.class,
            null
        );
        
        tracker.open();
        
        try {
            // Wait up to 30 seconds for service
            INode node = tracker.waitForService(30000);
            
            if (node == null) {
                throw new RuntimeException(
                    "INode service not registered within timeout: " + 
                    bundle.getSymbolicName()
                );
            }
            
            System.out.println("[OSGiBundleLoader] INode service registered: " + 
                node.getNodeId());
            
            return node;
            
        } finally {
            tracker.close();
        }
    }
    
    /**
     * Unload bundle (stop and uninstall)
     */
    public CompletableFuture<Void> unloadBundle(String packageId) {
        return CompletableFuture.runAsync(() -> {
            try {
                Bundle bundle = installedBundles.remove(packageId);
                
                if (bundle != null) {
                    System.out.println("[OSGiBundleLoader] Unloading bundle: " + 
                        bundle.getSymbolicName());
                    
                    // Stop bundle (deactivates BundleActivator)
                    bundle.stop();
                    
                    // Uninstall bundle
                    bundle.uninstall();
                    
                    System.out.println("[OSGiBundleLoader] Bundle unloaded: " + packageId);
                }
                
            } catch (BundleException e) {
                throw new RuntimeException("Failed to unload bundle", e);
            }
        });
    }
    
    /**
     * Shutdown OSGi framework and all bundles
     */
    public CompletableFuture<Void> shutdown() {
        return CompletableFuture.runAsync(() -> {
            synchronized (frameworkLock) {
                if (framework != null && frameworkInitialized) {
                    try {
                        System.out.println("[OSGiBundleLoader] Shutting down OSGi framework");
                        
                        // Stop framework (stops all bundles)
                        framework.stop();
                        
                        // Wait for framework to stop
                        framework.waitForStop(10000);
                        
                        System.out.println("[OSGiBundleLoader] OSGi framework stopped");
                        
                    } catch (Exception e) {
                        System.err.println("[OSGiBundleLoader] Error shutting down framework: " + 
                            e.getMessage());
                    } finally {
                        framework = null;
                        frameworkInitialized = false;
                        installedBundles.clear();
                    }
                }
            }
        });
    }
    
    /**
     * Get installed bundle
     */
    public Bundle getBundle(String packageId) {
        return installedBundles.get(packageId);
    }
    
    /**
     * Check if framework is running
     */
    public boolean isFrameworkActive() {
        return frameworkInitialized && 
               framework != null && 
               framework.getState() == Bundle.ACTIVE;
    }
    
    /**
     * Get bundle count
     */
    public int getBundleCount() {
        return installedBundles.size();
    }
}