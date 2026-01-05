package io.netnotes.engine.core.system;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import io.netnotes.engine.core.system.control.nodes.InstalledPackage;
import io.netnotes.engine.core.system.control.nodes.PackageInfo;
import io.netnotes.engine.core.system.control.nodes.PackageManifest;
import io.netnotes.engine.core.system.control.nodes.ProcessConfig;
import io.netnotes.engine.core.system.control.nodes.security.PolicyManifest;
import io.netnotes.engine.core.system.control.terminal.ClientTerminalRenderManager.RenderState;
import io.netnotes.engine.core.system.control.terminal.Renderable;
import io.netnotes.engine.core.system.control.terminal.TextStyle;
import io.netnotes.engine.core.system.control.terminal.input.TerminalInputReader;
import io.netnotes.engine.core.system.control.terminal.menus.MenuContext;
import io.netnotes.engine.core.system.control.terminal.menus.MenuNavigator;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.utils.TimeHelpers;

/**
 * BrowsePackagesScreen - REFACTORED for pull-based rendering
 * 
 * Browse and install packages from repositories with explicit state management.
 */
class BrowsePackagesScreen extends TerminalScreen {
    
    private enum View {
        LOADING,
        CATEGORY_LIST,
        PACKAGE_LIST,
        PACKAGE_DETAILS,
        CONFIGURE_INSTALL,
        CONFIRM_INSTALL,
        INSTALLING,
        SUCCESS,
        ERROR
    }
    
    private volatile View currentView = View.LOADING;
    private volatile String statusMessage = null;
    private volatile String errorMessage = null;
    
    // Data state
    private volatile List<PackageInfo> availablePackages;
    private volatile List<InstalledPackage> installedPackages;
    private volatile String selectedCategory;
    private volatile PackageInfo selectedPackage;
    private volatile ProcessConfig installConfig;
    private volatile boolean loadImmediately = false;
    
    // UI components
    private MenuNavigator menuNavigator;
    private TerminalInputReader inputReader;
    private final NodeCommands nodeCommands;
    private Runnable onBack;
    
    public BrowsePackagesScreen(
        String name, 
        SystemTerminalContainer terminal, 
        NodeCommands nodeCommands
    ) {
        super(name, terminal);
        this.availablePackages = new ArrayList<>();
        this.installedPackages = new ArrayList<>();
        this.nodeCommands = nodeCommands;

        menuNavigator = new MenuNavigator(terminal).withParent(this);
    }
    
    public void setOnBack(Runnable onBack) {
        this.onBack = onBack;
    }
    
    // ===== RENDERABLE INTERFACE =====
    
    @Override
    public RenderState getRenderState() {
        return switch (currentView) {
            case LOADING -> buildLoadingState();
            case CATEGORY_LIST, PACKAGE_LIST, PACKAGE_DETAILS -> buildMenuState();
            case CONFIGURE_INSTALL -> buildConfigureState();
            case CONFIRM_INSTALL -> buildConfirmState();
            case INSTALLING -> buildInstallingState();
            case SUCCESS -> buildSuccessState();
            case ERROR -> buildErrorState();
        };
    }
    
    /**
     * Build loading state
     */
    private RenderState buildLoadingState() {
        return RenderState.builder()
            .add((term) -> {
                term.printAt(0, 0, "Browse Packages", TextStyle.BOLD);
                term.printAt(7, 10, "Updating package lists from repositories...");
                term.printAt(9, 10, "This may take a moment...");
            })
            .build();
    }
    
    /**
     * MenuNavigator is active, return empty
     */
    private RenderState buildMenuState() {
        return RenderState.builder()
            .add(batch -> batch.clear())
            .add(menuNavigator.asRenderElement())
            .build();
    }
    
    /**
     * Build configure install screen
     */
    private RenderState buildConfigureState() {
        if (selectedPackage == null) {
            return buildErrorState();
        }
        
        PackageManifest manifest = selectedPackage.getManifest();
        NoteBytesReadOnly defaultNamespace = manifest.getNamespace() != null ? 
            manifest.getNamespace() : selectedPackage.getPackageId();
        
        return RenderState.builder()
            .add((term) -> {
                term.printAt(0, 0, "Configure Installation", TextStyle.BOLD);
                term.printAt(5, 10, "Package: " + selectedPackage.getName());
                term.printAt(7, 10, "Process Namespace:");
                term.printAt(8, 12, "Default: " + defaultNamespace);
                term.printAt(9, 12, "Leave blank to use default");
                term.printAt(11, 10, "Custom namespace (or press Enter):");
                // InputReader renders at 11, 46
            })
            .build();
    }
    
    /**
     * Build confirm install screen
     */
    private RenderState buildConfirmState() {
        if (selectedPackage == null || installConfig == null) {
            return buildErrorState();
        }
        
        return RenderState.builder()
            .add((term) -> {
                term.printAt(0, 0, "Confirm Installation", TextStyle.BOLD);
                term.printAt(5, 10, "Package: " + selectedPackage.getName() + 
                    " v" + selectedPackage.getVersion());
                term.printAt(6, 10, "Repository: " + selectedPackage.getRepository());
                term.printAt(7, 10, "Size: " + formatSize(selectedPackage.getSize()));
                term.printAt(9, 10, "Installation Configuration:");
                term.printAt(10, 12, "• Process Namespace: " + 
                    installConfig.getProcessId());
                term.printAt(11, 12, "• Load Immediately: " + 
                    (loadImmediately ? "Yes" : "No"));
                term.printAt(13, 10, "This will download and install the package.");
                term.printAt(15, 10, "Type 'INSTALL' to confirm:");
                // InputReader renders at 17, 38
            })
            .build();
    }
    
    /**
     * Build installing state
     */
    private RenderState buildInstallingState() {
        return RenderState.builder()
            .add((term) -> {
                term.printAt(terminal.getRows() / 2, 10, 
                    "Installing package...", TextStyle.INFO);
                if (statusMessage != null) {
                    term.printAt(terminal.getRows() / 2 + 2, 10, 
                        statusMessage, TextStyle.NORMAL);
                }
            })
            .build();
    }
    
    /**
     * Build success state
     */
    private RenderState buildSuccessState() {
        return RenderState.builder()
            .add((term) -> {
                term.printAt(terminal.getRows() / 2, 10, 
                    "✓ " + (statusMessage != null ? statusMessage : "Success!"), 
                    TextStyle.SUCCESS);
                term.printAt(terminal.getRows() / 2 + 2, 10, 
                    "Returning to package list...", TextStyle.NORMAL);
            })
            .build();
    }
    
    /**
     * Build error state
     */
    private RenderState buildErrorState() {
        String message = errorMessage != null ? errorMessage : "An error occurred";
        
        return RenderState.builder()
            .add((term) -> {
                term.printAt(terminal.getRows() / 2, 10, 
                    message, TextStyle.ERROR);
                term.printAt(terminal.getRows() / 2 + 2, 10, 
                    "Press any key to continue...", TextStyle.NORMAL);
            })
            .build();
    }
    
    // ===== LIFECYCLE =====
    
    @Override
    public CompletableFuture<Void> onShow() {
        // Reset state
        currentView = View.LOADING;
        selectedCategory = null;
        selectedPackage = null;
        installConfig = null;
        loadImmediately = false;
        errorMessage = null;
        statusMessage = null;
        
        // Make this screen active
        super.onShow();
        
        // Start loading
        updatePackageCache();
        
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public void onHide() {
        cleanup();
    }
    
    // ===== LOADING =====
    
    private void updatePackageCache() {
        CompletableFuture<List<PackageInfo>> availableFuture = 
            nodeCommands.browseAvailablePackages();
        CompletableFuture<List<InstalledPackage>> installedFuture = 
            nodeCommands.getInstalledPackages();
        
        CompletableFuture.allOf(availableFuture, installedFuture)
            .thenAccept(v -> {
                availablePackages = availableFuture.join();
                installedPackages = installedFuture.join();
                currentView = View.CATEGORY_LIST;
                showCategoryList();
            })
            .exceptionally(ex -> {
                errorMessage = "Failed to update packages: " + ex.getMessage();
                currentView = View.ERROR;
                invalidate(); // PATCH: Added invalidate
                
                terminal.waitForKeyPress()
                    .thenRun(this::goBack);
                return null;
            });
    }
    
    // ===== CATEGORY LIST =====
    
    private void showCategoryList() {
        if (availablePackages.isEmpty()) {
            errorMessage = "No packages available. Check repository configuration.";
            currentView = View.ERROR;
            invalidate();
            
            terminal.waitForKeyPress()
                .thenRun(this::goBack);
            return;
        }
        
        currentView = View.CATEGORY_LIST;
        
        // Group by category
        Map<String, List<PackageInfo>> byCategory = availablePackages.stream()
            .collect(Collectors.groupingBy(PackageInfo::getCategory));
        
        // Build menu
        ContextPath menuPath = terminal.getSessionPath().append("menu", "categories");
        MenuContext menu = new MenuContext(
            menuPath, 
            "Package Categories",
            "Found " + availablePackages.size() + " packages",
            null
        );
        
        // Add category items
        for (Map.Entry<String, List<PackageInfo>> entry : byCategory.entrySet()) {
            String category = entry.getKey();
            int count = entry.getValue().size();
            String description = category + " (" + count + " package" + 
                (count != 1 ? "s" : "") + ")";
            
            menu.addItem(category, description, "Browse " + category + " packages",
                () -> showCategory(category));
        }
        
        menu.addSeparator("Navigation");
        menu.addItem("all", "All Packages", "View all packages", 
            () -> showCategory(null));
        menu.addItem("refresh", "Refresh Package List", "Update from repositories", 
            () -> {
                currentView = View.LOADING;
                invalidate();
                updatePackageCache();
            });
        menu.addItem("back", "Back to Node Manager", this::goBack);
        
      
        menuNavigator.showMenu(menu);
    }
    
    // ===== PACKAGE LIST =====
    
    private void showCategory(String category) {
        selectedCategory = category;
        currentView = View.PACKAGE_LIST;
        
        List<PackageInfo> packages = selectedCategory == null ? 
            availablePackages : 
            availablePackages.stream()
                .filter(p -> p.getCategory().equals(selectedCategory))
                .collect(Collectors.toList());
        
        String title = selectedCategory != null ? 
            "Category: " + selectedCategory : "All Packages";
        
        ContextPath menuPath = terminal.getSessionPath().append("menu", "packages");
        MenuContext menu = new MenuContext(menuPath, title, null, null);
        
        // Add menu item for each package
        for (PackageInfo pkg : packages) {
            boolean installed = isPackageInstalled(pkg.getPackageId());
            String marker = installed ? "[INSTALLED] " : "";
            String description = marker + pkg.getName() + " v" + pkg.getVersion();
            String help = truncate(pkg.getDescription(), 50);
            
            menu.addItem(pkg.getPackageId().toString(), description, help, 
                () -> showPackageDetails(pkg));
        }
        
        menu.addSeparator("Navigation");
        menu.addItem("back", "Back to Categories", () -> {
            selectedCategory = null;
            currentView = View.CATEGORY_LIST;
            showCategoryList();
        });
        
        menuNavigator.showMenu(menu);
    }
    
    // ===== PACKAGE DETAILS =====
    
    private void showPackageDetails(PackageInfo pkg) {
        selectedPackage = pkg;
        currentView = View.PACKAGE_DETAILS;
        
        boolean isInstalled = isPackageInstalled(pkg.getPackageId());
        
        ContextPath menuPath = terminal.getSessionPath().append("menu", "package-details");
        MenuContext menu = new MenuContext(
            menuPath, 
            pkg.getName() + " v" + pkg.getVersion(),
            buildPackageDescription(pkg),
            null
        );
        
        if (!isInstalled) {
            menu.addItem("install", "Install Package", 
                "Install this package (requires password)",
                this::startInstallFlow);
        } else {
            menu.addInfoItem("installed", "[ALREADY INSTALLED]");
        }
        
        menu.addSeparator("Navigation");
        menu.addItem("back", "Back to Package List", () -> {
            selectedPackage = null;
            currentView = View.PACKAGE_LIST;
            showCategory(selectedCategory);
        });
        
        menuNavigator.showMenu(menu);
    }
    
    private String buildPackageDescription(PackageInfo pkg) {
        StringBuilder desc = new StringBuilder();
        desc.append("Repository: ").append(pkg.getRepository()).append("\n");
        desc.append("Size: ").append(formatSize(pkg.getSize())).append("\n");
        desc.append("Type: ").append(pkg.getManifest().getType()).append("\n\n");
        desc.append(pkg.getDescription());
        return desc.toString();
    }
    
    // ===== INSTALL FLOW =====
    
    private void startInstallFlow() {
        PackageManifest manifest = selectedPackage.getManifest();
        
        // If package requires specific namespace, skip configuration
        if (manifest.requiresSpecificNamespace()) {
            installConfig = ProcessConfig.create(manifest.getNamespace());
            loadImmediately = manifest.isAutoload();
            currentView = View.CONFIRM_INSTALL;
            invalidate();
            
            startConfirmation();
        } else {
            // Allow user to configure
            currentView = View.CONFIGURE_INSTALL;
            invalidate();

            readCustomNamespace();
        }
    }
    
    private void readCustomNamespace() {
        inputReader = new TerminalInputReader(terminal, 11, 46, 20);
        
        inputReader.setOnComplete(input -> {
            if (inputReader != null) {
                inputReader.close();
                inputReader = null;
            }
            
            // Determine namespace
            NoteBytesReadOnly namespace;
            if (input == null || input.trim().isEmpty()) {
                PackageManifest manifest = selectedPackage.getManifest();
                namespace = manifest.getNamespace() != null ? 
                    manifest.getNamespace() : selectedPackage.getPackageId();
            } else {
                namespace = new NoteBytesReadOnly(input.trim());
            }
            
            installConfig = ProcessConfig.create(namespace);
            
            // Ask about autoload
            askAutoload();
        });
        
        inputReader.setOnEscape(v -> {
            if (inputReader != null) {
                inputReader.close();
                inputReader = null;
            }
            selectedPackage = null;
            installConfig = null;
            currentView = View.CATEGORY_LIST;
            showCategoryList();
        });
    }
    
    private void askAutoload() {
        // Update view to show autoload question
        Renderable autoloadRenderable = () -> {
            return RenderState.builder()
                .add((term) -> {
                    term.printAt(0, 0, "Configure Installation", TextStyle.BOLD);
                    term.printAt(5, 10, "Package: " + selectedPackage.getName());
                    term.printAt(7, 10, "Namespace: " + installConfig.getProcessId());
                    term.printAt(9, 10, "Load immediately after installation? (y/N):");
                    // InputReader at 13, 54
                })
                .build();
        };
        
        // Temporarily replace render state
        terminal.setRenderable(autoloadRenderable);
        terminal.invalidate();
        
        readAutoloadChoice();
    }
    
    private void readAutoloadChoice() {
        inputReader = new TerminalInputReader(terminal, 9, 54, 3);
        
        inputReader.setOnComplete(input -> {
            if (inputReader != null) {
                inputReader.close();
                inputReader = null;
            }
            
            loadImmediately = "y".equalsIgnoreCase(input) || 
                             "yes".equalsIgnoreCase(input);
            
            currentView = View.CONFIRM_INSTALL;
            terminal.setRenderable(this);
            invalidate();

            startConfirmation();
        });
        
        inputReader.setOnEscape(v -> {
            if (inputReader != null) {
                inputReader.close();
                inputReader = null;
            }
            selectedPackage = null;
            installConfig = null;
            currentView = View.CATEGORY_LIST;
            showCategoryList();
        });
    }
    
    private void startConfirmation() {
        inputReader = new TerminalInputReader(terminal, 17, 38, 20);
        
        inputReader.setOnComplete(input -> {
            if (inputReader != null) {
                inputReader.close();
                inputReader = null;
            }
            
            if ("INSTALL".equals(input)) {
                performInstallation();
            } else {
                errorMessage = "Installation cancelled";
                currentView = View.ERROR;
                invalidate();
                
                terminal.waitForKeyPress()
                    .thenRun(() -> {
                        currentView = View.CONFIRM_INSTALL;
                        terminal.setRenderable(this);
                        invalidate();
                        startConfirmation();
                    });
            }
        });
        
        inputReader.setOnEscape(v -> {
            if (inputReader != null) {
                inputReader.close();
                inputReader = null;
            }
            selectedPackage = null;
            installConfig = null;
            currentView = View.CATEGORY_LIST;
            showCategoryList();
        });
    }
    
    private void performInstallation() {
        currentView = View.INSTALLING;
        statusMessage = "Downloading and installing...";
        invalidate();
        
        PolicyManifest policy = PolicyManifest.fromNoteBytes(
            selectedPackage.getManifest().getMetadata()
        );
        
        nodeCommands.installPackage(
            selectedPackage,
            installConfig,
            policy,
            loadImmediately
        )
        .thenCompose(installedPkg -> {
            if (loadImmediately) {
                return nodeCommands.loadNode(installedPkg.getPackageId().getId())
                    .thenApply(instance -> {
                        statusMessage = "Package installed and loaded successfully!\n" +
                            "Instance running at: " + instance.getProcessId();
                        return null;
                    });
            } else {
                statusMessage = "Package installed successfully!\n" +
                    "Use 'Running Instances' to load the package";
                return CompletableFuture.completedFuture(null);
            }
        })
        .thenRun(() -> {
            currentView = View.SUCCESS;
            invalidate();
            
            // Wait a bit, then return to package list
            CompletableFuture.runAsync(() -> {
                TimeHelpers.timeDelay(3);
            }).thenRun(() -> {
                selectedPackage = null;
                installConfig = null;
                currentView = View.PACKAGE_LIST;
                showCategory(selectedCategory);
            });
        })
        .exceptionally(ex -> {
            errorMessage = "Installation failed: " + ex.getMessage();
            currentView = View.ERROR;
            invalidate();
            
            terminal.waitForKeyPress()
                .thenRun(() -> {
                    selectedPackage = null;
                    installConfig = null;
                    currentView = View.CATEGORY_LIST;
                    showCategoryList();
                });
            return null;
        });
    }
    
    // ===== UTILITIES =====
    
    private boolean isPackageInstalled(NoteBytesReadOnly packageId) {
        return installedPackages.stream()
            .anyMatch(pkg -> pkg.getPackageId().getId().equals(packageId));
    }
    
    private void goBack() {
        cleanup();
        if (onBack != null) {
            onBack.run();
        }
    }
    
    private void cleanup() {
        if (menuNavigator != null) {
            menuNavigator.cleanup();
            menuNavigator = null;
        }
        
        if (inputReader != null) {
            inputReader.close();
            inputReader = null;
        }
    }
    
    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return (bytes / (1024 * 1024)) + " MB";
    }
    
    private String truncate(String str, int maxLength) {
        if (str == null) return "";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength - 3) + "...";
    }
}