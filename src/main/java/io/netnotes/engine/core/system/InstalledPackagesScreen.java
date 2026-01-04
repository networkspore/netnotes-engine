package io.netnotes.engine.core.system;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.core.system.control.nodes.InstalledPackage;
import io.netnotes.engine.core.system.control.nodes.NodeInstance;
import io.netnotes.engine.core.system.control.terminal.ClientRenderManager.RenderState;
import io.netnotes.engine.core.system.control.terminal.TextStyle;
import io.netnotes.engine.core.system.control.terminal.input.TerminalInputReader;
import io.netnotes.engine.core.system.control.terminal.menus.MenuContext;
import io.netnotes.engine.core.system.control.terminal.menus.MenuNavigator;
import io.netnotes.engine.io.ContextPath;

/**
 * InstalledPackagesScreen - REFACTORED for pull-based rendering
 * 
 * View and manage installed packages with explicit state management.
 */
class InstalledPackagesScreen extends TerminalScreen {
    
    private enum View {
        LOADING,
        PACKAGE_LIST,
        PACKAGE_DETAILS,
        LOADING_INSTANCE,
        DETAILED_VIEW,
        CONFIRM_UNINSTALL,
        UNINSTALLING,
        SUCCESS,
        ERROR
    }
    
    private volatile View currentView = View.LOADING;
    private volatile String statusMessage = null;
    private volatile String errorMessage = null;
    
    // Data state
    private volatile List<InstalledPackage> packages;
    private volatile List<NodeInstance> runningInstances;
    private volatile InstalledPackage selectedPackage;
    
    // UI components
    private final ContextPath menuBasePath;
    private MenuNavigator menuNavigator;
    private TerminalInputReader inputReader;
    private final NodeCommands nodeCommands;
    private Runnable onBackCallback;
    
    public InstalledPackagesScreen(
        String name, 
        SystemTerminalContainer terminal, 
        NodeCommands nodeCommands
    ) {
        super(name, terminal);
        this.menuBasePath = ContextPath.of("installed-packages");
        this.nodeCommands = nodeCommands;
    }
    
    public void setOnBack(Runnable callback) {
        this.onBackCallback = callback;
    }
    
    // ===== RENDERABLE INTERFACE =====
    
    @Override
    public RenderState getRenderState() {
        return switch (currentView) {
            case LOADING -> buildLoadingState();
            case PACKAGE_LIST, PACKAGE_DETAILS -> buildMenuState();
            case LOADING_INSTANCE -> buildLoadingInstanceState();
            case DETAILED_VIEW -> buildDetailedViewState();
            case CONFIRM_UNINSTALL -> buildConfirmUninstallState();
            case UNINSTALLING -> buildUninstallingState();
            case SUCCESS -> buildSuccessState();
            case ERROR -> buildErrorState();
        };
    }
    
    private RenderState buildLoadingState() {
        return RenderState.builder()
            .add((term) -> {
                term.printAt(0, 0, "Installed Packages", TextStyle.BOLD);
                term.printAt(5, 10, "Loading...", TextStyle.INFO);
            })
            .build();
    }
    
    private RenderState buildMenuState() {
        // MenuNavigator is active
        return RenderState.builder().build();
    }
    
    private RenderState buildLoadingInstanceState() {
        return RenderState.builder()
            .add((term) -> {
                term.printAt(0, 0, "Loading Package", TextStyle.BOLD);
                if (selectedPackage != null) {
                    term.printAt(5, 10, "Loading: " + selectedPackage.getName());
                    term.printAt(6, 10, "ProcessId: " + selectedPackage.getProcessId());
                }
                term.printAt(8, 10, "Starting node...", TextStyle.INFO);
            })
            .build();
    }
    
    private RenderState buildDetailedViewState() {
        if (selectedPackage == null) {
            return buildErrorState();
        }
        
        return RenderState.builder()
            .add((term) -> {
                term.printAt(0, 0, "Package Details", TextStyle.BOLD);
                term.printAt(5, 10, "Name: " + selectedPackage.getName());
                term.printAt(6, 10, "Version: " + selectedPackage.getVersion());
                term.printAt(7, 10, "ProcessId: " + selectedPackage.getProcessId());
                term.printAt(8, 10, "Repository: " + selectedPackage.getRepository());
                term.printAt(9, 10, "Installed: " + 
                    formatDate(selectedPackage.getInstalledDate()));
                term.printAt(10, 10, "Type: " + 
                    selectedPackage.getManifest().getType());
                term.printAt(12, 10, "Description:");
                term.printAt(13, 10, selectedPackage.getDescription());
                term.printAt(15, 10, "Paths:");
                term.printAt(16, 12, "Data: " + 
                    selectedPackage.getProcessConfig().getDataRootPath());
                term.printAt(17, 12, "Flow: " + 
                    selectedPackage.getProcessConfig().getFlowBasePath());
                term.printAt(19, 10, "Security:");
                term.printAt(20, 12, 
                    selectedPackage.getSecurityPolicy().getGrantedCapabilities().size() + 
                    " capabilities granted");
                term.printAt(22, 10, "Press any key to return...", 
                    TextStyle.INFO);
            })
            .build();
    }
    
    private RenderState buildConfirmUninstallState() {
        if (selectedPackage == null) {
            return buildErrorState();
        }
        
        return RenderState.builder()
            .add((term) -> {
                term.printAt(0, 0, "Confirm Uninstall", TextStyle.BOLD);
                term.printAt(5, 10, "⚠️ Uninstall package?", TextStyle.WARNING);
                term.printAt(7, 10, "Package: " + selectedPackage.getName());
                term.printAt(8, 10, "Version: " + selectedPackage.getVersion());
                term.printAt(10, 10, "This action cannot be undone.");
                term.printAt(12, 10, "Type 'CONFIRM' to proceed:");
                // InputReader at 12, 40
            })
            .build();
    }
    
    private RenderState buildUninstallingState() {
        return RenderState.builder()
            .add((term) -> {
                term.printAt(0, 0, "Uninstalling", TextStyle.BOLD);
                term.printAt(5, 10, "Uninstalling package...", TextStyle.INFO);
            })
            .build();
    }
    
    private RenderState buildSuccessState() {
        return RenderState.builder()
            .add((term) -> {
                term.printAt(terminal.getRows() / 2, 10, 
                    "✓ " + (statusMessage != null ? statusMessage : "Success!"), 
                    TextStyle.SUCCESS);
                term.printAt(terminal.getRows() / 2 + 2, 10, 
                    "Press any key to continue...", TextStyle.NORMAL);
            })
            .build();
    }
    
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
        currentView = View.LOADING;
        selectedPackage = null;
        errorMessage = null;
        statusMessage = null;
        
        super.onShow();
        
        loadPackages();
        
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public void onHide() {
        cleanup();
    }
    
    // ===== PACKAGE LIST =====
    
    private void loadPackages() {
        CompletableFuture.allOf(
            nodeCommands.getInstalledPackages()
                .thenAccept(pkgs -> this.packages = pkgs),
            nodeCommands.getRunningInstances()
                .thenAccept(inst -> this.runningInstances = inst)
        )
        .thenRun(() -> {
            if (packages == null || packages.isEmpty()) {
                errorMessage = "No packages installed";
                currentView = View.ERROR;
                
                terminal.getRenderManager().setActive(this);
                terminal.waitForKeyPress()
                    .thenRun(this::goBack);
            } else {
                currentView = View.PACKAGE_LIST;
                showPackageList();
            }
        })
        .exceptionally(ex -> {
            errorMessage = "Failed to load packages: " + ex.getMessage();
            currentView = View.ERROR;
            
            terminal.getRenderManager().setActive(this);
            terminal.waitForKeyPress()
                .thenRun(this::goBack);
            
            return null;
        });
    }
    
    private void showPackageList() {
        ContextPath menuPath = menuBasePath.append("list");
        MenuContext menu = new MenuContext(
            menuPath, 
            "Installed Packages",
            packages.size() + " package(s) installed",
            null
        );
        
        // Add menu item for each package
        for (InstalledPackage pkg : packages) {
            boolean running = isPackageRunning(pkg);
            String badge = running ? " 🟢" : "";
            String description = pkg.getName() + " v" + pkg.getVersion() + badge;
            
            menu.addItem(pkg.getPackageId().toString(), description, 
                running ? "Running" : "Stopped",
                () -> showPackageDetails(pkg));
        }
        
        menu.addSeparator("Navigation");
        menu.addItem("refresh", "Refresh List", "Reload package list", 
            this::loadPackages);
        menu.addItem("back", "Back", this::goBack);
        
        if (menuNavigator == null) {
            menuNavigator = new MenuNavigator(terminal);
        }
        menuNavigator.showMenu(menu);
    }
    
    // ===== PACKAGE DETAILS =====
    
    private void showPackageDetails(InstalledPackage pkg) {
        selectedPackage = pkg;
        currentView = View.PACKAGE_DETAILS;
        
        boolean isRunning = isPackageRunning(pkg);
        
        ContextPath menuPath = menuBasePath.append("details");
        MenuContext menu = new MenuContext(
            menuPath, 
            pkg.getName(),
            "Version: " + pkg.getVersion() + "\n" +
            "Status: " + (isRunning ? "Running 🟢" : "Stopped"),
            null
        );
        
        menu.addItem("load", "Load Instance", 
            isRunning ? "Start another instance" : "Start this package",
            this::loadInstance);
        
        menu.addItem("configure", "Configure Package", 
            "Modify namespace and settings (requires password)", 
            this::configurePackage);
        
        menu.addItem("uninstall", "Uninstall Package", 
            "Remove this package (requires password)", 
            this::startUninstall);
        
        menu.addSeparator("Information");
        menu.addItem("details", "View Full Details", 
            "Show all package information",
            this::showDetailedView);
        
        menu.addSeparator("Navigation");
        menu.addItem("back", "Back to List", () -> {
            selectedPackage = null;
            currentView = View.PACKAGE_LIST;
            showPackageList();
        });
        
        menuNavigator.showMenu(menu);
    }
    
    // ===== LOAD INSTANCE =====
    
    private void loadInstance() {
        currentView = View.LOADING_INSTANCE;
        
        // Make this screen active
        terminal.getRenderManager().setActive(this);
        
        nodeCommands.loadNode(selectedPackage.getPackageId().getId())
            .thenAccept(instance -> {
                statusMessage = "Package loaded successfully!\n" +
                    "Instance ID: " + instance.getInstanceId() + "\n" +
                    "Process: " + instance.getProcessId() + "\n" +
                    "State: " + instance.getState();
                currentView = View.SUCCESS;
                invalidate();
                
                terminal.waitForKeyPress()
                    .thenRun(this::loadPackages);
            })
            .exceptionally(ex -> {
                errorMessage = "Failed to load package: " + ex.getMessage();
                currentView = View.ERROR;
                invalidate();
                
                terminal.waitForKeyPress()
                    .thenRun(() -> {
                        currentView = View.PACKAGE_DETAILS;
                        showPackageDetails(selectedPackage);
                    });
                return null;
            });
    }
    
    // ===== CONFIGURE PACKAGE =====
    
    private void configurePackage() {
        // Launch PackageConfigurationScreen
        PackageConfigurationScreen configScreen = new PackageConfigurationScreen(
            "package-config",
            terminal,
            selectedPackage,
            nodeCommands
        );
        
        configScreen.setOnComplete(() -> {
            selectedPackage = null;
            loadPackages();
        });
        
        configScreen.onShow();
    }
    
    // ===== DETAILED VIEW =====
    
    private void showDetailedView() {
        currentView = View.DETAILED_VIEW;
        
        // Make this screen active
        terminal.getRenderManager().setActive(this);
        
        terminal.waitForKeyPress()
            .thenRun(() -> {
                currentView = View.PACKAGE_DETAILS;
                showPackageDetails(selectedPackage);
            });
    }
    
    // ===== UNINSTALL =====
    
    private void startUninstall() {
        // Check if package has running instances
        if (isPackageRunning(selectedPackage)) {
            errorMessage = "Cannot uninstall package with running instances.\n" +
                "Stop all instances first using the 'Running Instances' screen.";
            currentView = View.ERROR;
            
            terminal.getRenderManager().setActive(this);
            terminal.waitForKeyPress()
                .thenRun(() -> {
                    currentView = View.PACKAGE_DETAILS;
                    showPackageDetails(selectedPackage);
                });
            return;
        }
        
        // Launch PackageUninstallScreen
        PackageUninstallScreen uninstallScreen = new PackageUninstallScreen(
            "package-uninstall",
            terminal,
            selectedPackage,
            nodeCommands
        );
        
        uninstallScreen.setOnComplete(() -> {
            selectedPackage = null;
            loadPackages();
        });
        
        uninstallScreen.onShow();
    }
    
    // ===== UTILITIES =====
    
    private boolean isPackageRunning(InstalledPackage pkg) {
        if (runningInstances == null) return false;
        return runningInstances.stream()
            .anyMatch(inst -> inst.getPackageId().equals(pkg.getPackageId()));
    }
    
    private void goBack() {
        cleanup();
        if (onBackCallback != null) {
            onBackCallback.run();
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
    
    private String formatDate(long timestamp) {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
            .format(new java.util.Date(timestamp));
    }
}