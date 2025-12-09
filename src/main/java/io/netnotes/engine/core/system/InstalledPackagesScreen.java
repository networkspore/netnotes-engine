package io.netnotes.engine.core.system;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.core.system.control.MenuContext;
import io.netnotes.engine.core.system.control.PasswordReader;
import io.netnotes.engine.core.system.control.nodes.InstalledPackage;
import io.netnotes.engine.core.system.control.nodes.NodeInstance;
import io.netnotes.engine.core.system.control.nodes.NodeLoadRequest;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.input.InputDevice;
import io.netnotes.engine.io.input.KeyRunTable;
import io.netnotes.engine.io.input.Keyboard.KeyCodeBytes;
import io.netnotes.engine.io.input.ephemeralEvents.EphemeralKeyDownEvent;
import io.netnotes.engine.io.input.ephemeralEvents.EphemeralRoutedEvent;
import io.netnotes.engine.io.input.events.KeyDownEvent;
import io.netnotes.engine.noteBytes.collections.NoteBytesRunnablePair;

/**
 * ENHANCED InstalledPackagesScreen
 * 
 * Key additions:
 * - Load package with stored configuration
 * - Configure package (launches PackageConfigurationScreen)
 * - Enhanced uninstall (launches PackageUninstallScreen)
 * - Show which packages have running instances
 */
class InstalledPackagesScreen extends TerminalScreen {
    
    private enum View {
        PACKAGE_LIST,
        PACKAGE_DETAILS,
        LOADING_INSTANCE,
        CONFIRM_UNINSTALL,
        UNINSTALLING
    }
    
    private final ContextPath basePath;
    private View currentView = View.PACKAGE_LIST;
    
    private List<InstalledPackage> packages;
    private InstalledPackage selectedPackage;
    private int selectedIndex = 0;
    
    private MenuContext currentMenu;
    private PasswordReader passwordReader;
    private Runnable onBackCallback;
    
    // For tracking running instances
    private List<NodeInstance> runningInstances;
    
    public InstalledPackagesScreen(String name, SystemTerminalContainer terminal, InputDevice keyboard) {
        super(name, terminal, keyboard);
        this.basePath = ContextPath.parse("installed-packages");
    }
    
    public void setOnBack(Runnable callback) {
        this.onBackCallback = callback;
    }
    
    @Override
    public CompletableFuture<Void> onShow() {
        currentView = View.PACKAGE_LIST;
        return loadPackages();
    }
    
    @Override
    public void onHide() {
        cleanup();
    }
    
    @Override
    public CompletableFuture<Void> render() {
        switch (currentView) {
            case PACKAGE_LIST:
                return renderPackageList();
            case PACKAGE_DETAILS:
                return renderPackageDetails();
            case LOADING_INSTANCE:
                return renderLoadingInstance();
            case CONFIRM_UNINSTALL:
                return renderConfirmUninstall();
            case UNINSTALLING:
                return renderUninstalling();
            default:
                return CompletableFuture.completedFuture(null);
        }
    }
    
    // ===== PACKAGE LIST =====
    
    private CompletableFuture<Void> loadPackages() {
        RuntimeAccess access = terminal.getSystemAccess();
        if (access == null) {
            return showError("System access not available");
        }
        
        // Load both packages and running instances
        return terminal.clear()
            .thenCompose(v -> terminal.printTitle("Installed Packages"))
            .thenCompose(v -> terminal.printAt(5, 10, "Loading..."))
            .thenCompose(v -> CompletableFuture.allOf(
                access.getInstalledPackages().thenAccept(pkgs -> this.packages = pkgs),
                access.getRunningInstances().thenAccept(inst -> this.runningInstances = inst)
            ))
            .thenRun(() -> {
                this.selectedIndex = 0;
                render();
            })
            .exceptionally(ex -> {
                showError("Failed to load packages: " + ex.getMessage());
                return null;
            });
    }
    
    private CompletableFuture<Void> renderPackageList() {
        return terminal.clear()
            .thenCompose(v -> terminal.printTitle("Installed Packages"))
            .thenCompose(v -> {
                if (packages == null || packages.isEmpty()) {
                    return terminal.printAt(5, 10, "No packages installed")
                        .thenCompose(v2 -> terminal.printAt(7, 10, "Press any key to go back..."))
                        .thenRun(() -> waitForAnyKey(() -> goBack()));
                }
                
                CompletableFuture<Void> future = terminal.printAt(5, 10, 
                    String.format("%d package(s) installed:", packages.size()));
                
                for (int i = 0; i < Math.min(packages.size(), 15); i++) {
                    InstalledPackage pkg = packages.get(i);
                    String marker = (i == selectedIndex) ? "▶ " : "  ";
                    
                    // Check if package has running instances
                    String badge = isPackageRunning(pkg) ? " 🟢" : "";
                    
                    String line = String.format("%s%s v%s%s", 
                        marker, pkg.getName(), pkg.getVersion(), badge);
                    
                    final int row = 7 + i;
                    future = future.thenCompose(v2 -> terminal.printAt(row, 10, line));
                }
                
                int instructionsRow = 7 + Math.min(packages.size(), 15) + 2;
                return future
                    .thenCompose(v2 -> terminal.printAt(instructionsRow, 10, 
                        "↑/↓: Navigate | Enter: Details | ESC: Back"))
                    .thenRun(this::startListNavigation);
            });
    }
    
    private boolean isPackageRunning(InstalledPackage pkg) {
        if (runningInstances == null) return false;
        return runningInstances.stream()
            .anyMatch(inst -> inst.getPackageId().equals(pkg.getPackageId()));
    }
    
    private void startListNavigation() {
        KeyRunTable navKeys = new KeyRunTable(
            new NoteBytesRunnablePair(KeyCodeBytes.UP, () -> {
                selectedIndex = Math.max(0, selectedIndex - 1);
                render();
            }),
            new NoteBytesRunnablePair(KeyCodeBytes.DOWN, () -> {
                selectedIndex = Math.min(packages.size() - 1, selectedIndex + 1);
                render();
            }),
            new NoteBytesRunnablePair(KeyCodeBytes.ENTER, () -> {
                keyboard.setEventConsumer(null);
                selectedPackage = packages.get(selectedIndex);
                currentView = View.PACKAGE_DETAILS;
                render();
            }),
            new NoteBytesRunnablePair(KeyCodeBytes.ESCAPE, () -> {
                keyboard.setEventConsumer(null);
                goBack();
            })
        );
        
        keyboard.setEventConsumer(event -> handleKeyEvent(event, navKeys));
    }
    
    // ===== PACKAGE DETAILS =====
    
    private CompletableFuture<Void> renderPackageDetails() {
        if (selectedPackage == null) {
            currentView = View.PACKAGE_LIST;
            return render();
        }
        
        boolean isRunning = isPackageRunning(selectedPackage);
        
        currentMenu = new MenuContext(basePath.append("details"), selectedPackage.getName())
            .addItem("load", "Load Instance", 
                isRunning ? "Start another instance of this package" : "Start this package",
                this::loadInstance)
            .addItem("configure", "Configure Package", 
                "Modify namespace and settings (requires password)", 
                this::configurePackage)
            .addItem("uninstall", "Uninstall Package", 
                "Remove this package (requires password)", 
                this::launchUninstallScreen)
            .addSeparator("Information")
            .addItem("details", "View Full Details", "Show all package information",
                this::showDetailedView)
            .addSeparator("Navigation")
            .addItem("back", "Back to List", this::backToList);
        
        return terminal.clear()
            .thenCompose(v -> terminal.printTitle("Package Details"))
            .thenCompose(v -> terminal.printAt(5, 10, "Package: " + selectedPackage.getName()))
            .thenCompose(v -> terminal.printAt(6, 10, "Version: " + selectedPackage.getVersion()))
            .thenCompose(v -> terminal.printAt(7, 10, "Namespace: " + selectedPackage.getProcessId()))
            .thenCompose(v -> terminal.printAt(8, 10, 
                "Status: " + (isRunning ? "Running 🟢" : "Stopped")))
            .thenCompose(v -> renderMenu(currentMenu, 10));
    }
    
    // ===== LOAD INSTANCE =====
    
    private void loadInstance() {
        keyboard.setEventConsumer(null);
        currentView = View.LOADING_INSTANCE;
        render();
        
        RuntimeAccess access = terminal.getSystemAccess();
        NodeLoadRequest request = new NodeLoadRequest(selectedPackage);
        
        access.loadNode(request)
            .thenAccept(instance -> {
                terminal.clear()
                    .thenCompose(v -> terminal.printSuccess("✓ Package loaded successfully"))
                    .thenCompose(v -> terminal.printAt(7, 10, 
                        "Instance ID: " + instance.getInstanceId()))
                    .thenCompose(v -> terminal.printAt(8, 10, 
                        "Process: " + instance.getProcessId()))
                    .thenCompose(v -> terminal.printAt(9, 10, 
                        "State: " + instance.getState()))
                    .thenCompose(v -> terminal.printAt(11, 10, 
                        "Press any key to continue..."))
                    .thenRun(() -> waitForAnyKey(() -> {
                        // Reload to show updated status
                        loadPackages();
                    }));
            })
            .exceptionally(ex -> {
                terminal.clear()
                    .thenCompose(v -> terminal.printError("Failed to load package"))
                    .thenCompose(v -> terminal.printAt(7, 10, "Error: " + ex.getMessage()))
                    .thenCompose(v -> terminal.printAt(10, 10, 
                        "Press any key to continue..."))
                    .thenRun(() -> waitForAnyKey(() -> {
                        currentView = View.PACKAGE_DETAILS;
                        render();
                    }));
                return null;
            });
    }
    
    private CompletableFuture<Void> renderLoadingInstance() {
        return terminal.clear()
            .thenCompose(v -> terminal.printTitle("Loading Package"))
            .thenCompose(v -> terminal.printAt(5, 10, "Loading: " + selectedPackage.getName()))
            .thenCompose(v -> terminal.printAt(6, 10, "ProcessId: " + selectedPackage.getProcessId()))
            .thenCompose(v -> terminal.printAt(8, 10, "Starting node..."));
    }
    
    // ===== CONFIGURE PACKAGE =====
    
    private void configurePackage() {
        keyboard.setEventConsumer(null);
        
        // Launch PackageConfigurationScreen
        PackageConfigurationScreen configScreen = new PackageConfigurationScreen(
            "package-config",
            terminal,
            keyboard,
            selectedPackage
        );
        
        configScreen.setOnComplete(() -> {
            // Return to package list and reload
            selectedPackage = null;
            loadPackages();
        });
        
        configScreen.onShow();
    }
    
    // ===== DETAILED VIEW =====
    
    private void showDetailedView() {
        keyboard.setEventConsumer(null);
        
        terminal.clear()
            .thenCompose(v -> terminal.printTitle("Package Details"))
            .thenCompose(v -> terminal.printAt(5, 10, "Name: " + selectedPackage.getName()))
            .thenCompose(v -> terminal.printAt(6, 10, "Version: " + selectedPackage.getVersion()))
            .thenCompose(v -> terminal.printAt(7, 10, "ProcessId: " + selectedPackage.getProcessId()))
            .thenCompose(v -> terminal.printAt(8, 10, "Repository: " + selectedPackage.getRepository()))
            .thenCompose(v -> terminal.printAt(9, 10, 
                "Installed: " + formatDate(selectedPackage.getInstalledDate())))
            .thenCompose(v -> terminal.printAt(10, 10, "Type: " + 
                selectedPackage.getManifest().getType()))
            .thenCompose(v -> terminal.printAt(12, 10, "Description:"))
            .thenCompose(v -> terminal.printAt(13, 10, selectedPackage.getDescription()))
            .thenCompose(v -> terminal.printAt(15, 10, "Paths:"))
            .thenCompose(v -> terminal.printAt(16, 12, "Data: " + 
                selectedPackage.getProcessConfig().getDataRootPath()))
            .thenCompose(v -> terminal.printAt(17, 12, "Flow: " + 
                selectedPackage.getProcessConfig().getFlowBasePath()))
            .thenCompose(v -> terminal.printAt(19, 10, "Security:"))
            .thenCompose(v -> terminal.printAt(20, 12, 
                selectedPackage.getSecurityPolicy().getGrantedCapabilities().size() + 
                " capabilities granted"))
            .thenCompose(v -> terminal.printAt(22, 10, 
                "Press any key to return..."))
            .thenRun(() -> waitForAnyKey(() -> {
                currentView = View.PACKAGE_DETAILS;
                render();
            }));
    }
    
    // ===== UNINSTALL =====
    
    private void launchUninstallScreen() {
        keyboard.setEventConsumer(null);
        
        // Check if package has running instances
        if (isPackageRunning(selectedPackage)) {
            terminal.clear()
                .thenCompose(v -> terminal.printError("Cannot Uninstall"))
                .thenCompose(v -> terminal.printAt(7, 10, 
                    "Package has running instances"))
                .thenCompose(v -> terminal.printAt(9, 10, 
                    "Stop all instances before uninstalling"))
                .thenCompose(v -> terminal.printAt(10, 10, 
                    "Use 'Running Instances' screen to stop them"))
                .thenCompose(v -> terminal.printAt(12, 10, 
                    "Press any key to return..."))
                .thenRun(() -> waitForAnyKey(() -> {
                    currentView = View.PACKAGE_DETAILS;
                    render();
                }));
            return;
        }
        
        // Launch PackageUninstallScreen
        PackageUninstallScreen uninstallScreen = new PackageUninstallScreen(
            "package-uninstall",
            terminal,
            keyboard,
            selectedPackage
        );
        
        uninstallScreen.setOnComplete(() -> {
            // Return to package list and reload
            selectedPackage = null;
            loadPackages();
        });
        
        uninstallScreen.onShow();
    }
    
    // Old simple uninstall (kept as fallback)
    private void startUninstall() {
        keyboard.setEventConsumer(null);
        currentView = View.CONFIRM_UNINSTALL;
        render();
    }
    
    private void backToList() {
        keyboard.setEventConsumer(null);
        currentView = View.PACKAGE_LIST;
        render();
    }
    
    private CompletableFuture<Void> renderConfirmUninstall() {
        return terminal.clear()
            .thenCompose(v -> terminal.printTitle("Confirm Uninstall"))
            .thenCompose(v -> terminal.printAt(5, 10, "⚠️  Uninstall package?"))
            .thenCompose(v -> terminal.printAt(7, 10, "Package: " + selectedPackage.getName()))
            .thenCompose(v -> terminal.printAt(8, 10, "Version: " + selectedPackage.getVersion()))
            .thenCompose(v -> terminal.printAt(10, 10, "This action cannot be undone."))
            .thenCompose(v -> terminal.printAt(12, 10, "Enter password to confirm:"))
            .thenCompose(v -> terminal.moveCursor(12, 40))
            .thenRun(this::startPasswordConfirmation);
    }
    
    private void startPasswordConfirmation() {
        passwordReader = new PasswordReader();
        keyboard.setEventConsumer(passwordReader.getEventConsumer());
        
        passwordReader.setOnPassword(password -> {
            keyboard.setEventConsumer(null);
            passwordReader.close();
            passwordReader = null;
            
            RuntimeAccess access = terminal.getSystemAccess();
            access.verifyPassword(password)
                .thenAccept(valid -> {
                    password.close();
                    if (valid) {
                        performUninstall();
                    } else {
                        terminal.clear()
                            .thenCompose(v -> terminal.printError("Invalid password"))
                            .thenCompose(v -> terminal.printAt(10, 10, "Press any key..."))
                            .thenRun(() -> waitForAnyKey(() -> {
                                currentView = View.PACKAGE_DETAILS;
                                render();
                            }));
                    }
                })
                .exceptionally(ex -> {
                    password.close();
                    showError("Password verification failed: " + ex.getMessage());
                    return null;
                });
        });
    }
    
    private void performUninstall() {
        currentView = View.UNINSTALLING;
        render();
        
        RuntimeAccess access = terminal.getSystemAccess();
        //TODO: implement progress tracking
        access.uninstallPackage(selectedPackage.getPackageId(), null)
            .thenAccept(v -> {
                terminal.clear()
                    .thenCompose(v2 -> terminal.printSuccess("✓ Package uninstalled"))
                    .thenCompose(v2 -> terminal.printAt(7, 10, "Press any key..."))
                    .thenRun(() -> waitForAnyKey(() -> {
                        selectedPackage = null;
                        currentView = View.PACKAGE_LIST;
                        loadPackages();
                    }));
            })
            .exceptionally(ex -> {
                showError("Uninstall failed: " + ex.getMessage());
                return null;
            });
    }
    
    private CompletableFuture<Void> renderUninstalling() {
        return terminal.clear()
            .thenCompose(v -> terminal.printTitle("Uninstalling"))
            .thenCompose(v -> terminal.printAt(5, 10, "Uninstalling package..."));
    }
    
    // ===== UTILITIES =====
    
    private CompletableFuture<Void> renderMenu(MenuContext menu, int startRow) {
        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        
        int row = startRow;
        int index = 1;
        
        for (MenuContext.MenuItem item : menu.getItems()) {
            if (item.type == MenuContext.MenuItemType.SEPARATOR) {
                final int currentRow = row;
                future = future.thenCompose(v -> terminal.printAt(currentRow, 10, 
                    "─".repeat(40)));
                row++;
                continue;
            }
            
            final int currentRow = row;
            final String itemText = String.format("%d. %s", index, item.description);
            
            future = future.thenCompose(v -> terminal.printAt(currentRow, 10, itemText));
            row++;
            index++;
        }
        
        final int selectRow = row + 1;
        return future
            .thenCompose(v -> terminal.printAt(selectRow, 10, "Use arrow keys and Enter"))
            .thenRun(() -> startMenuNavigation(menu));
    }
    
    private void startMenuNavigation(MenuContext menu) {
        java.util.List<MenuContext.MenuItem> items = new java.util.ArrayList<>();
        for (MenuContext.MenuItem item : menu.getItems()) {
            if (item.type != MenuContext.MenuItemType.SEPARATOR) {
                items.add(item);
            }
        }
        
        final int[] selectedIndex = {0};
        
        KeyRunTable navKeys = new KeyRunTable(
            new NoteBytesRunnablePair(KeyCodeBytes.UP, () -> 
                selectedIndex[0] = (selectedIndex[0] - 1 + items.size()) % items.size()),
            new NoteBytesRunnablePair(KeyCodeBytes.DOWN, () -> 
                selectedIndex[0] = (selectedIndex[0] + 1) % items.size()),
            new NoteBytesRunnablePair(KeyCodeBytes.ENTER, () -> {
                keyboard.setEventConsumer(null);
                menu.navigate(items.get(selectedIndex[0]).name);
            }),
            new NoteBytesRunnablePair(KeyCodeBytes.ESCAPE, () -> {
                keyboard.setEventConsumer(null);
                backToList();
            })
        );
        
        keyboard.setEventConsumer(event -> handleKeyEvent(event, navKeys));
    }
    
    private void handleKeyEvent(Object event, KeyRunTable keys) {
        if (event instanceof EphemeralRoutedEvent ephemeral) {
            try (ephemeral) {
                if (ephemeral instanceof EphemeralKeyDownEvent ekd) {
                    keys.run(ekd.getKeyCodeBytes());
                }
            }
        } else if (event instanceof KeyDownEvent keyDown) {
            keys.run(keyDown.getKeyCodeBytes());
        }
    }
    
    private void waitForAnyKey(Runnable callback) {
        keyboard.setEventConsumer(event -> {
            keyboard.setEventConsumer(null);
            callback.run();
        });
    }
    
    private CompletableFuture<Void> showError(String message) {
        return terminal.clear()
            .thenCompose(v -> terminal.printError(message))
            .thenCompose(v -> terminal.printAt(10, 10, "Press any key..."))
            .thenRun(() -> waitForAnyKey(() -> goBack()));
    }
    
    private void goBack() {
        if (onBackCallback != null) {
            onBackCallback.run();
        }
    }
    
    private String formatDate(long timestamp) {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
            .format(new java.util.Date(timestamp));
    }
    
    private void cleanup() {
        keyboard.setEventConsumer(null);
        
        if (passwordReader != null) {
            passwordReader.close();
            passwordReader = null;
        }
    }
}