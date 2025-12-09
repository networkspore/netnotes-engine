package io.netnotes.engine.core.system;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.core.system.control.MenuContext;
import io.netnotes.engine.core.system.control.PasswordReader;
import io.netnotes.engine.core.system.control.nodes.InstalledPackage;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.input.InputDevice;
import io.netnotes.engine.io.input.KeyRunTable;
import io.netnotes.engine.io.input.Keyboard.KeyCodeBytes;
import io.netnotes.engine.io.input.ephemeralEvents.EphemeralKeyDownEvent;
import io.netnotes.engine.io.input.ephemeralEvents.EphemeralRoutedEvent;
import io.netnotes.engine.io.input.events.KeyDownEvent;
import io.netnotes.engine.noteBytes.collections.NoteBytesRunnablePair;

/**
 * InstalledPackagesScreen - View and manage installed packages
 * 
 * Features:
 * - List all installed packages
 * - View package details
 * - Uninstall packages (with password confirmation)
 * - Load package instances
 */
class InstalledPackagesScreen extends TerminalScreen {
    
    private enum View {
        PACKAGE_LIST,
        PACKAGE_DETAILS,
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
        
        return terminal.clear()
            .thenCompose(v -> terminal.printTitle("Installed Packages"))
            .thenCompose(v -> terminal.printAt(5, 10, "Loading..."))
            .thenCompose(v -> access.getInstalledPackages())
            .thenAccept(pkgs -> {
                this.packages = pkgs;
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
                    String line = String.format("%s%s v%s", 
                        marker, pkg.getName(), pkg.getVersion());
                    
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
        
        currentMenu = new MenuContext(basePath.append("details"), selectedPackage.getName())
            .addItem("load", "Load Instance", "Start a new instance of this package", 
                this::loadInstance)
            .addItem("uninstall", "Uninstall Package", "Remove this package (requires password)", 
                this::startUninstall)
            .addItem("back", "Back to List", this::backToList);
        
        return terminal.clear()
            .thenCompose(v -> terminal.printTitle("Package Details"))
            .thenCompose(v -> terminal.printAt(5, 10, "Package: " + selectedPackage.getName()))
            .thenCompose(v -> terminal.printAt(6, 10, "Version: " + selectedPackage.getVersion()))
            .thenCompose(v -> terminal.printAt(7, 10, "Namespace: " + selectedPackage.getProcessId()))
            .thenCompose(v -> terminal.printAt(8, 10, "Category: " + selectedPackage.getManifest().getName()))
            .thenCompose(v -> renderMenu(currentMenu, 10));
    }
    
    private void loadInstance() {
        keyboard.setEventConsumer(null);
        // TODO: Implement node loading
        terminal.printAt(15, 10, "Node loading not yet implemented")
            .thenRun(() -> waitForAnyKey(() -> {
                currentView = View.PACKAGE_DETAILS;
                render();
            }));
    }
    
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
    
    // ===== UNINSTALL =====
    
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
        access.uninstallPackage(selectedPackage.getPackageId())
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
        java.util.List<MenuContext.MenuItem> items = new java.util.ArrayList<>(menu.getItems());
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
    
    private void cleanup() {
        keyboard.setEventConsumer(null);
        
        if (passwordReader != null) {
            passwordReader.close();
            passwordReader = null;
        }
    }
}