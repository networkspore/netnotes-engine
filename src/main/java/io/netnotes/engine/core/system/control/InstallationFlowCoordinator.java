package io.netnotes.engine.core.system.control;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import io.netnotes.engine.core.RuntimeAccess;
import io.netnotes.engine.core.system.control.nodes.InstallationRegistry;
import io.netnotes.engine.core.system.control.nodes.InstallationRequest;
import io.netnotes.engine.core.system.control.nodes.PackageId;
import io.netnotes.engine.core.system.control.nodes.PackageInfo;
import io.netnotes.engine.core.system.control.nodes.PackageManifest;
import io.netnotes.engine.core.system.control.nodes.ProcessConfig;
import io.netnotes.engine.core.system.control.nodes.security.PathCapability;
import io.netnotes.engine.core.system.control.nodes.security.PolicyManifest;
import io.netnotes.engine.core.system.control.ui.UIProtocol;
import io.netnotes.engine.core.system.control.ui.UIRenderer;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.NoteUUID;

/**
 * InstallationFlowCoordinator - REFACTORED
 * 
 * Guides user through installation WITHOUT password or InputDevice access.
 * All system operations go through RuntimeAccess.
 * 
 * KEY CHANGES:
 * 1. Uses RuntimeAccess instead of InstallationRegistry directly
 * 2. Password obtained via callback (SystemSessionProcess handles it)
 * 3. No InputDevice dependency
 */
public class InstallationFlowCoordinator {
    public static final NoteBytesReadOnly DASH = new NoteBytesReadOnly("-");
    private final UIRenderer uiRenderer;
    private final ContextPath basePath;
    private final RuntimeAccess systemAccess;
    
    // Callback to get password from SystemSessionProcess
    private final Supplier<CompletableFuture<NoteBytesEphemeral>> passwordSupplier;
    
    // Current state
    private PackageInfo selectedPackage;
    private ProcessConfig chosenProcessConfig;
    private PolicyManifest packagePolicy;
    private boolean sourceReviewed = false;
    
    public InstallationFlowCoordinator(
        UIRenderer uiRenderer,
        ContextPath basePath,
        RuntimeAccess systemAccess,
        Supplier<CompletableFuture<NoteBytesEphemeral>> passwordSupplier
    ) {
        this.uiRenderer = uiRenderer;
        this.basePath = basePath;
        this.systemAccess = systemAccess;
        this.passwordSupplier = passwordSupplier;
    }
    
    /**
     * Start installation flow for a package
     * 
     * Returns InstallationRequest with password obtained from SystemSessionProcess
     */
    public CompletableFuture<InstallationRequest> startInstallation(PackageInfo pkg) {
        this.selectedPackage = pkg;
        
        // Step 1: Show package overview
        return showPackageOverview()
            .thenCompose(proceed -> {
                if (!proceed) {
                    return CompletableFuture.completedFuture(null);
                }
                
                // Step 2: Review capabilities
                return reviewCapabilities();
            })
            .thenCompose(approved -> {
                if (!approved) {
                    return CompletableFuture.completedFuture(null);
                }
                
                // Step 3: Choose process configuration
                return chooseProcessConfiguration();
            })
            .thenCompose(processConfig -> {
                if (processConfig == null) {
                    return CompletableFuture.completedFuture(null);
                }
                
                this.chosenProcessConfig = processConfig;
                
                // Step 4: Browse source (optional, skip for now)
                // return offerSourceBrowsing();
                return CompletableFuture.completedFuture(true);
            })
            .thenCompose(continueInstall -> {
                if (!continueInstall) {
                    return CompletableFuture.completedFuture(null);
                }
                
                // Step 5: Get password via callback
                return requestPasswordConfirmation();
            })
            .thenCompose(password -> {
                if (password == null) {
                    return CompletableFuture.completedFuture(null);
                }
                
                // Step 6: Ask if should load immediately
                return askLoadImmediately()
                    .thenApply(loadNow -> {
                        // Build final request
                        return new InstallationRequest(
                            selectedPackage,
                            chosenProcessConfig,
                            packagePolicy,
                            password,
                            loadNow,
                            sourceReviewed,
                            null // GitHubInfo - optional
                        );
                    });
            });
    }
    
    // ===== STEP 1: PACKAGE OVERVIEW =====
    
    private CompletableFuture<Boolean> showPackageOverview() {
        CompletableFuture<Boolean> resultFuture = new CompletableFuture<>();
        
        MenuContext menu = new MenuContext(
            basePath.append("overview"),
            "Install: " + selectedPackage.getName(),
            uiRenderer
        );
        
        String info = String.format(
            "Package: %s\n" +
            "Version: %s\n" +
            "Category: %s\n" +
            "Size: %d KB\n" +
            "Repository: %s\n\n" +
            "%s",
            selectedPackage.getName(),
            selectedPackage.getVersion(),
            selectedPackage.getCategory(),
            selectedPackage.getSize() / 1024,
            selectedPackage.getRepository(),
            selectedPackage.getDescription()
        );
        
        menu.addInfoItem("package_info", info);
        
        PackageManifest manifest = selectedPackage.getManifest();
        String manifestInfo = String.format(
            "Type: %s\n" +
            "Multiple Instances: %s\n" +
            "Auto-load: %s",
            manifest.getType(),
            manifest.allowsMultipleInstances() ? "Yes" : "No",
            manifest.isAutoload() ? "Yes" : "No"
        );
        
        menu.addInfoItem("manifest_info", manifestInfo);
        menu.addSeparator("Actions");
        
        menu.addItem("continue", "Continue Installation", 
            "Proceed to capability review", 
            () -> resultFuture.complete(true));
        
        menu.addItem("cancel", "Cancel", 
            "Do not install this package", 
            () -> resultFuture.complete(false));
        
        menu.display();
        
        return resultFuture;
    }
    
    // ===== STEP 2: REVIEW CAPABILITIES =====
    
    private CompletableFuture<Boolean> reviewCapabilities() {
        CompletableFuture<Boolean> resultFuture = new CompletableFuture<>();
        
        // Extract policy from manifest metadata
        this.packagePolicy = PolicyManifest.fromNoteBytes(
            selectedPackage.getManifest().getMetadata()
        );
        
        MenuContext menu = new MenuContext(
            basePath.append("capabilities"),
            "Security Review",
            uiRenderer
        );
        
        menu.addInfoItem("info", 
            "This package requests the following capabilities.\n" +
            "Review carefully before approving.");
        
        menu.addSeparator("Requested Capabilities");
        
        List<PathCapability> capabilities = packagePolicy.getRequestedCapabilities();
        
        if (capabilities.isEmpty()) {
            menu.addInfoItem("no_caps", "No special capabilities requested");
        } else {
            int capNum = 1;
            for (PathCapability cap : capabilities) {
                String capInfo = String.format(
                    "%d. %s\n   Reason: %s",
                    capNum++,
                    cap.getDescription(),
                    cap.getReason()
                );
                menu.addInfoItem("cap_" + capNum, capInfo);
            }
        }
        
        if (packagePolicy.hasSensitivePaths()) {
            menu.addInfoItem("warning", 
                "\n⚠️ WARNING: This package requests access to sensitive system paths.");
        }
        
        menu.addSeparator("Decision");
        
        menu.addItem("approve", "✓ Approve Capabilities", 
            "Continue with installation", 
            () -> resultFuture.complete(true));
        
        menu.addItem("deny", "✗ Deny Installation", 
            "Do not install - capabilities too broad", 
            () -> resultFuture.complete(false));
        
        menu.display();
        
        return resultFuture;
    }
    
    // ===== STEP 3: CHOOSE PROCESS CONFIGURATION =====
    
    private CompletableFuture<ProcessConfig> chooseProcessConfiguration() {
        PackageManifest manifest = selectedPackage.getManifest();
        
        // Get installation registry from systemAccess
        InstallationRegistry registry = systemAccess.getInstallationRegistry();
        
        // Single instance only? Auto-assign
        if (!manifest.allowsMultipleInstances()) {
            NoteBytesReadOnly processId = selectedPackage.getPackageId();
            
            if (registry.isInstalled(new PackageId(selectedPackage.getPackageId(), selectedPackage.getVersion()))) {
                uiRenderer.render(UIProtocol.showError(
                    "Package already installed and does not support multiple instances"));
                return CompletableFuture.completedFuture(null);
            }
            
            return CompletableFuture.completedFuture(
                ProcessConfig.standalone(processId)
            );
        }
        
        return chooseProcessConfigurationMenu(registry);
    }
    
    private CompletableFuture<ProcessConfig> chooseProcessConfigurationMenu(
        InstallationRegistry registry
    ) {
        CompletableFuture<ProcessConfig> resultFuture = new CompletableFuture<>();
        
        PackageManifest manifest = selectedPackage.getManifest();
        
        MenuContext menu = new MenuContext(
            basePath.append("process-config"),
            "Process Configuration",
            uiRenderer
        );
        
        menu.addInfoItem("info", 
            "This package supports multiple instances.\n" +
            "Choose how this instance should run:");
        
        menu.addSeparator("Configuration Options");
        
        menu.addItem("standalone", 
            "Standalone Instance",
            "Run in its own namespace (recommended)",
            () -> {
                NoteBytesReadOnly processId = generateUniqueProcessId(
                    selectedPackage.getPackageId(),
                    registry
                );
                resultFuture.complete(ProcessConfig.standalone(processId));
            });
        
        if (manifest.supportsProcessMode(ProcessConfig.InheritanceMode.SHARED)) {
            menu.addItem("shared",
                "Shared Workspace",
                "Share namespace with other packages",
                () -> {
                    // TODO: Choose shared workspace
                    resultFuture.complete(null);
                });
        }
        
        if (manifest.supportsClustering()) {
            menu.addItem("cluster",
                "Cluster Member",
                "Join an existing cluster",
                () -> {
                    // TODO: Choose cluster
                    resultFuture.complete(null);
                });
        }
        
        menu.addItem("cancel", "Cancel Installation", 
            () -> resultFuture.complete(null));
        
        menu.display();
        
        return resultFuture;
    }
    
    private NoteBytesReadOnly generateUniqueProcessId(NoteBytesReadOnly baseId, InstallationRegistry registry) {
        NoteBytesReadOnly processId = baseId;
        
        if (isProcessIdInUse(processId, registry)) {
            processId = baseId.concat(DASH, NoteUUID.createLocalUUID64());
        }
        
        return processId;
    }
    
    private boolean isProcessIdInUse(NoteBytesReadOnly processId, InstallationRegistry registry) {
        return registry.getInstalledPackages().stream()
            .anyMatch(pkg -> pkg.getProcessId().equals(processId));
    }
    
    // ===== STEP 5: PASSWORD CONFIRMATION =====
    
    /**
     * Request password via callback
     * 
     * KEY CHANGE: Uses passwordSupplier instead of creating PasswordSession
     * SystemSessionProcess handles the actual password entry
     */
    private CompletableFuture<NoteBytesEphemeral> requestPasswordConfirmation() {
        uiRenderer.render(UIProtocol.showMessage(
            "Installation requires system password confirmation..."));
        
        // Delegate to SystemSessionProcess via callback
        return passwordSupplier.get()
            .thenApply(password -> {
                if (password != null) {
                    uiRenderer.render(UIProtocol.showMessage(
                        "✓ Password confirmed"));
                } else {
                    uiRenderer.render(UIProtocol.showMessage(
                        "Installation cancelled"));
                }
                return password;
            })
            .exceptionally(ex -> {
                uiRenderer.render(UIProtocol.showError(
                    "Password confirmation failed: " + ex.getMessage()));
                return null;
            });
    }
    
    // ===== STEP 6: LOAD IMMEDIATELY =====
    
    private CompletableFuture<Boolean> askLoadImmediately() {
        CompletableFuture<Boolean> resultFuture = new CompletableFuture<>();
        
        MenuContext menu = new MenuContext(
            basePath.append("load-now"),
            "Installation Complete",
            uiRenderer
        );
        
        menu.addInfoItem("success",
            "✓ Package will be installed.\n\n" +
            "Would you like to load it immediately?");
        
        menu.addSeparator("Options");
        
        menu.addItem("load",
            "▶️ Load Now",
            "Start the node immediately",
            () -> resultFuture.complete(true));
        
        menu.addItem("later",
            "⏸️ Load Later",
            "Install but don't start yet",
            () -> resultFuture.complete(false));
        
        menu.display();
        
        return resultFuture;
    }
}