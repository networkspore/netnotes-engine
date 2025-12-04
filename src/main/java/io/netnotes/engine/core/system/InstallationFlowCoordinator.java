package io.netnotes.engine.core.system;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.netnotes.engine.core.system.control.MenuContext;
import io.netnotes.engine.core.system.control.nodes.InstallationRegistry;
import io.netnotes.engine.core.system.control.nodes.InstallationRequest;
import io.netnotes.engine.core.system.control.nodes.InstalledPackage;
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
            "Auto-load: %s",
            manifest.getType(),
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
        
    /**
     * Choose processId for installation
     */
    private CompletableFuture<ProcessConfig> chooseProcessConfiguration() {
        PackageManifest manifest = selectedPackage.getManifest();
        PackageManifest.NamespaceRequirement nsReq = manifest.getNamespaceRequirement();
        
        // Case 1: REQUIRED namespace - no user choice
        if (nsReq.mode() == PackageManifest.NamespaceMode.REQUIRED) {
            NoteBytesReadOnly requiredNs = nsReq.namespace();
            
            System.out.println("[InstallationFlow] Package requires namespace: " + requiredNs);
            
            uiRenderer.render(UIProtocol.showMessage(
                String.format(
                    "This package must be installed in namespace: %s\n\n" +
                    "This is a system requirement and cannot be changed.",
                    requiredNs
                )
            ));
            
            ProcessConfig config = ProcessConfig.create(requiredNs);
            return CompletableFuture.completedFuture(config);
        }
        
        // Case 2 & 3: DEFAULT or FLEXIBLE - show menu
        return showNamespaceChoiceMenu(nsReq);
    }


    /**
     * Show namespace choice menu
     */
    private CompletableFuture<ProcessConfig> showNamespaceChoiceMenu(
        PackageManifest.NamespaceRequirement nsReq
    ) {
        CompletableFuture<ProcessConfig> resultFuture = new CompletableFuture<>();
        
        InstallationRegistry registry = systemAccess.getInstallationRegistry();
        
        MenuContext menu = new MenuContext(
            basePath.append("process-config"),
            "Choose Installation Namespace",
            uiRenderer
        );
        
        // Different info based on namespace mode
        if (nsReq.mode() == PackageManifest.NamespaceMode.DEFAULT) {
            menu.addInfoItem("info", 
                String.format(
                    "This package suggests namespace: %s\n\n" +
                    "You can accept the suggestion or choose a different namespace.\n" +
                    "Packages sharing a namespace share data and flow paths.",
                    nsReq.namespace()
                )
            );
        } else {
            menu.addInfoItem("info", 
                "Choose where this package will be installed.\n\n" +
                "Packages sharing a processId share data and flow paths.\n" +
                "Most packages use their own unique processId.");
        }
        
        menu.addSeparator("Options");
        
        // Option 1: Use suggested/default namespace
        NoteBytesReadOnly defaultNs = getDefaultNamespace(nsReq);
        String defaultLabel = nsReq.mode() == PackageManifest.NamespaceMode.DEFAULT ?
            "✓ Use Suggested: " + defaultNs :
            "✓ Default: " + defaultNs;
        String defaultDesc = nsReq.mode() == PackageManifest.NamespaceMode.DEFAULT ?
            "Use package's recommended namespace" :
            "Standard installation with unique namespace";
        
        menu.addItem("default", 
            defaultLabel,
            defaultDesc,
            () -> {
                ProcessConfig config = ProcessConfig.create(defaultNs);
                resultFuture.complete(config);
            });
        
        // Option 2: Join existing namespace
        List<NoteBytesReadOnly> existingProcessIds = getExistingProcessIds(registry);
        if (!existingProcessIds.isEmpty()) {
            menu.addSeparator("Join Existing Namespace");
            
            for (NoteBytesReadOnly existingId : existingProcessIds) {
                List<InstalledPackage> members = getPackagesInNamespace(existingId, registry);
                String memberNames = members.stream()
                    .map(InstalledPackage::getName)
                    .limit(3)
                    .collect(Collectors.joining(", "));
                
                if (members.size() > 3) {
                    memberNames += String.format(" (+%d more)", members.size() - 3);
                }
                
                String description = String.format(
                    "Share with: %s", 
                    memberNames
                );
                
                menu.addItem("join-" + existingId,
                    "Join: " + existingId,
                    description,
                    () -> {
                        // Show warning if joining different namespace than suggested
                        if (nsReq.mode() == PackageManifest.NamespaceMode.DEFAULT &&
                            !existingId.equals(nsReq.namespace())) {
                            
                            confirmNamespaceOverride(existingId, nsReq.namespace())
                                .thenAccept(confirmed -> {
                                    if (confirmed) {
                                        ProcessConfig config = ProcessConfig.forExistingNamespace(existingId);
                                        resultFuture.complete(config);
                                    } else {
                                        // Return to menu
                                        showNamespaceChoiceMenu(nsReq)
                                            .thenAccept(resultFuture::complete);
                                    }
                                });
                        } else {
                            ProcessConfig config = ProcessConfig.forExistingNamespace(existingId);
                            resultFuture.complete(config);
                        }
                    });
            }
        }
        
        // Option 3: Custom namespace
        menu.addItem("custom",
            "Custom Namespace",
            "Create a new named namespace for grouping",
            () -> promptForCustomProcessId()
                .thenCompose(customId -> {
                    if (customId == null) {
                        resultFuture.complete(null);
                        return CompletableFuture.completedFuture(null);
                    }
                    
                    // Show warning if using custom namespace instead of suggested
                    if (nsReq.mode() == PackageManifest.NamespaceMode.DEFAULT &&
                        !customId.equals(nsReq.namespace())) {
                        
                        return confirmNamespaceOverride(customId, nsReq.namespace())
                            .thenApply(confirmed -> {
                                if (confirmed) {
                                    ProcessConfig config = ProcessConfig.withCustomId(customId);
                                    resultFuture.complete(config);
                                } else {
                                    // Return to menu
                                    showNamespaceChoiceMenu(nsReq)
                                        .thenAccept(resultFuture::complete);
                                }
                                return null;
                            });
                    } else {
                        ProcessConfig config = ProcessConfig.withCustomId(customId);
                        resultFuture.complete(config);
                        return CompletableFuture.completedFuture(null);
                    }
                }));
        
        menu.addSeparator("Actions");
        menu.addItem("cancel", "Cancel Installation", 
            () -> resultFuture.complete(null));
        
        menu.display();
        return resultFuture;
    }


    /**
     * Get default namespace based on manifest requirements
     */
    private NoteBytesReadOnly getDefaultNamespace(PackageManifest.NamespaceRequirement nsReq) {
        switch (nsReq.mode()) {
            case REQUIRED:
            case DEFAULT:
                return nsReq.namespace();
            case FLEXIBLE:
            default:
                return selectedPackage.getPackageId();
        }
    }

   

  
    /**
     * Confirm namespace override when user chooses different from suggested
     */
    private CompletableFuture<Boolean> confirmNamespaceOverride(
        NoteBytesReadOnly chosenNs,
        NoteBytesReadOnly suggestedNs
    ) {
        CompletableFuture<Boolean> resultFuture = new CompletableFuture<>();
        
        MenuContext menu = new MenuContext(
            basePath.append("confirm-override"),
            "Confirm Namespace Override",
            uiRenderer
        );
        
        menu.addInfoItem("warning",
            String.format(
                "⚠️  This package recommends namespace: %s\n\n" +
                "You've chosen: %s\n\n" +
                "Using a different namespace may affect functionality " +
                "if this package expects to coordinate with others.",
                suggestedNs,
                chosenNs
            )
        );
        
        menu.addSeparator("Decision");
        
        menu.addItem("proceed",
            "Continue with " + chosenNs,
            "I understand the risks",
            () -> resultFuture.complete(true));
        
        menu.addItem("back",
            "Use Suggested: " + suggestedNs,
            "Follow package recommendation",
            () -> resultFuture.complete(false));
        
        menu.display();
        return resultFuture;
    }


   /**
     * Get all existing processIds from installed packages
     */
    private List<NoteBytesReadOnly> getExistingProcessIds(InstallationRegistry registry) {
        return registry.getInstalledPackages().stream()
            .map(pkg -> pkg.getProcessId())
            .distinct()
            .sorted((a, b) -> a.toString().compareTo(b.toString()))
            .collect(Collectors.toList());
    }

    /**
     * Get all packages sharing a namespace
     */
    private List<InstalledPackage> getPackagesInNamespace(
        NoteBytesReadOnly processId, 
        InstallationRegistry registry
    ) {
        return registry.getInstalledPackages().stream()
            .filter(pkg -> pkg.getProcessId().equals(processId))
            .collect(Collectors.toList());
    }


   /**
     * Prompt for custom processId
     */
    private CompletableFuture<NoteBytesReadOnly> promptForCustomProcessId() {
        CompletableFuture<NoteBytesReadOnly> resultFuture = new CompletableFuture<>();
        
        MenuContext menu = new MenuContext(
            basePath.append("custom-namespace"),
            "Custom Namespace",
            uiRenderer
        );
        
        menu.addInfoItem("info",
            "Enter a custom namespace identifier.\n\n" +
            "This will be used for the installation path and must be unique.");
        
        // Temporary: Generate unique ID
        // In real implementation, would use UIProtocol text input
        String customId = "custom-" + NoteUUID.createSafeUUID64();
        
        menu.addItem("generate",
            "Generate: " + customId,
            "Use auto-generated unique namespace",
            () -> resultFuture.complete(new NoteBytesReadOnly(customId)));
        
        menu.addItem("cancel",
            "Cancel",
            "Return to namespace selection",
            () -> resultFuture.complete(null));
        
        menu.display();
        return resultFuture;
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