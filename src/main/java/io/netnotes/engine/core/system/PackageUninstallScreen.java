package io.netnotes.engine.core.system;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.core.system.control.PasswordReader;
import io.netnotes.engine.core.system.control.nodes.InstalledPackage;
import io.netnotes.engine.core.system.control.nodes.NodeInstance;
import io.netnotes.engine.core.system.control.terminal.input.TerminalInputReader;
import io.netnotes.engine.core.system.control.terminal.menus.MenuContext;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.input.InputDevice;
import io.netnotes.engine.io.input.KeyRunTable;
import io.netnotes.engine.io.input.Keyboard.KeyCodeBytes;
import io.netnotes.engine.io.input.ephemeralEvents.EphemeralKeyDownEvent;
import io.netnotes.engine.io.input.ephemeralEvents.EphemeralRoutedEvent;
import io.netnotes.engine.io.input.events.KeyDownEvent;

/**
 * PackageUninstallScreen - Comprehensive package uninstall
 * 
 * Features:
 * - Check and stop running instances
 * - Option to keep or delete data
 * - Password confirmation
 * - Progress indication
 * 
 * Note: Data browsing is a future feature
 */
class PackageUninstallScreen extends TerminalScreen {
    
    private enum Step {
        CHECK_INSTANCES,
        SHOW_OPTIONS,
        PASSWORD_CONFIRM,
        UNINSTALLING,
        COMPLETE
    }
    
    private final InstalledPackage packageToUninstall;
    
    private Step currentStep = Step.CHECK_INSTANCES;
    private List<NodeInstance> runningInstances;
    private boolean deleteData = false;
    private PasswordReader passwordReader;
    private Runnable onCompleteCallback;
    private final NodeCommands nodeCommands;
    
    public PackageUninstallScreen(
        String name,
        SystemTerminalContainer terminal,
        InputDevice keyboard,
        InstalledPackage pkg,
        NodeCommands nodeCommands
    ) {
        super(name, terminal, keyboard);
        this.packageToUninstall = pkg;
        this.nodeCommands = nodeCommands;
    }
    
    public void setOnComplete(Runnable callback) {
        this.onCompleteCallback = callback;
    }
    
    @Override
    public CompletableFuture<Void> onShow() {
        currentStep = Step.CHECK_INSTANCES;
        return render();
    }
    
    @Override
    public void onHide() {
        cleanup();
    }
    
    @Override
    public CompletableFuture<Void> render() {
        return terminal.clear()
            .thenCompose(v -> {
                switch (currentStep) {
                    case CHECK_INSTANCES:
                        return checkRunningInstances();
                    case SHOW_OPTIONS:
                        return renderUninstallOptions();
                    case PASSWORD_CONFIRM:
                        return renderConfirm();
                    case UNINSTALLING:
                        return renderUninstalling();
                    case COMPLETE:
                        return renderComplete();
                    default:
                        return CompletableFuture.completedFuture(null);
                }
            });
    }
    
    // ===== STEP 1: CHECK RUNNING INSTANCES =====
    
    private CompletableFuture<Void> checkRunningInstances() {
        return terminal.printTitle("Uninstall Package")
            .thenCompose(v -> terminal.printAt(5, 10, 
                "Checking for running instances..."))
            .thenCompose(v -> nodeCommands.getInstancesByPackage(
                packageToUninstall.getPackageId()))
            .thenCompose(instances -> {
                this.runningInstances = instances;
                
                if (instances.isEmpty()) {
                    currentStep = Step.SHOW_OPTIONS;
                    return render();
                } else {
                    return showRunningInstancesWarning();
                }
            });
    }
    
    private CompletableFuture<Void> showRunningInstancesWarning() {
        return terminal.clear()
            .thenCompose(v -> terminal.printTitle("Cannot Uninstall"))
            .thenCompose(v -> terminal.printError(
                "⚠️  Package has running instances"))
            .thenCompose(v -> terminal.printAt(7, 10, 
                "Package: " + packageToUninstall.getName()))
            .thenCompose(v -> terminal.printAt(8, 10, 
                "Running instances: " + runningInstances.size()))
            .thenCompose(v -> terminal.printAt(10, 10, 
                "The following instances must be stopped first:"))
            .thenCompose(v -> {
                CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
                int row = 12;
                for (NodeInstance instance : runningInstances) {
                    final int currentRow = row++;
                    future = future.thenCompose(v2 -> terminal.printAt(currentRow, 12, 
                        "• " + instance.getInstanceId() + " [" + 
                        instance.getProcessId() + "]"));
                }
                return future;
            })
            .thenCompose(v -> showInstanceStopMenu());
    }
    
    private CompletableFuture<Void> showInstanceStopMenu() {
        ContextPath basePath = ContextPath.of("uninstall", "stop-instances");
        MenuContext menu = new MenuContext(basePath, "Options")
            .addItem("stop-all", "Stop All Instances", this::stopAllInstances)
            .addItem("cancel", "Cancel Uninstall", this::cancelUninstall);
        
        return renderMenu(menu, 18);
    }
    
    private void stopAllInstances() {
        keyboard.setEventConsumer(null);
        
        terminal.clear()
            .thenCompose(v -> terminal.printTitle("Stopping Instances"))
            .thenCompose(v -> terminal.printAt(5, 10, 
                "Stopping " + runningInstances.size() + " instances..."))
            .thenCompose(v -> {
                List<CompletableFuture<Void>> stopFutures = runningInstances.stream()
                    .map(inst -> nodeCommands.unloadNode(inst.getInstanceId()))
                    .toList();
                
                return CompletableFuture.allOf(
                    stopFutures.toArray(new CompletableFuture[0]));
            })
            .thenAccept(v -> {
                terminal.clear()
                    .thenCompose(v2 -> terminal.printSuccess("✓ All instances stopped"))
                    .thenCompose(v2 -> terminal.printAt(7, 10, 
                        "Press any key to continue..."))
                    .thenRun(() -> waitForAnyKey(() -> {
                        currentStep = Step.SHOW_OPTIONS;
                        render();
                    }));
            })
            .exceptionally(ex -> {
                terminal.clear()
                    .thenCompose(v -> terminal.printError(
                        "Failed to stop instances: " + ex.getMessage()))
                    .thenCompose(v -> terminal.printAt(10, 10, 
                        "Press any key to retry..."))
                    .thenRun(() -> waitForAnyKey(this::checkRunningInstances));
                return null;
            });
    }
    
    private void cancelUninstall() {
        keyboard.setEventConsumer(null);
        if (onCompleteCallback != null) {
            onCompleteCallback.run();
        }
    }
    
    // ===== STEP 2: SHOW UNINSTALL OPTIONS =====
    
    private CompletableFuture<Void> renderUninstallOptions() {
        return terminal.printTitle("Uninstall Options")
            .thenCompose(v -> terminal.printAt(5, 10, 
                "Package: " + packageToUninstall.getName()))
            .thenCompose(v -> terminal.printAt(6, 10, 
                "Version: " + packageToUninstall.getVersion()))
            .thenCompose(v -> terminal.printAt(8, 10, 
                "Data path: " + packageToUninstall.getProcessConfig().getDataRootPath()))
            .thenCompose(v -> terminal.printAt(10, 10, 
                "Choose uninstall option:"))
            .thenCompose(v -> showDataOptionsMenu());
    }
    
    private CompletableFuture<Void> showDataOptionsMenu() {
        ContextPath basePath = ContextPath.of("uninstall", "data-options");
        MenuContext menu = new MenuContext(basePath, "Data Handling")
            .addItem("keep-data", "Uninstall but keep data", () -> {
                deleteData = false;
                proceedToPasswordConfirm();
            })
            .addItem("delete-data", "Uninstall and delete all data", () -> {
                deleteData = true;
                proceedToPasswordConfirm();
            })
            .addSeparator("Future Features")
            .addInfoItem("browse-placeholder", 
                "Browse data (Coming Soon)")
            .addSeparator("Navigation")
            .addItem("cancel", "Cancel Uninstall", this::cancelUninstall);
        
        return renderMenu(menu, 12);
    }
    
    private void proceedToPasswordConfirm() {
        keyboard.setEventConsumer(null);
        currentStep = Step.PASSWORD_CONFIRM;
        render();
    }
    
    // ===== STEP 3: PASSWORD CONFIRM =====
    
    private CompletableFuture<Void> renderConfirm() {
        return terminal.printTitle("Confirm Uninstall")
            .thenCompose(v -> terminal.printAt(5, 10, "⚠️ WARNING"))
            .thenCompose(v -> terminal.printAt(7, 10, 
                "You are about to uninstall:"))
            .thenCompose(v -> terminal.printAt(8, 12, 
                "Package: " + packageToUninstall.getName()))
            .thenCompose(v -> terminal.printAt(9, 12, 
                "Version: " + packageToUninstall.getVersion()))
            .thenCompose(v -> terminal.printAt(11, 10, 
                deleteData ? "Data will be DELETED" : "Data will be KEPT"))
            .thenCompose(v -> terminal.printAt(13, 10, 
                "Type 'CONFIRM' to proceed:"))
            .thenCompose(v -> terminal.moveCursor(13, 36))
            .thenRun(this::startConfirmationEntry);
    }
    
    private void startConfirmationEntry() {
        TerminalInputReader inputReader = new TerminalInputReader(terminal, 13, 36, 20);
        keyboard.setEventConsumer(inputReader.getEventConsumer());
        
        inputReader.setOnComplete(input -> {
            keyboard.setEventConsumer(null);
            inputReader.close();
            
            if ("CONFIRM".equals(input)) {
                performUninstall();
            } else {
                terminal.clear()
                    .thenCompose(v -> terminal.printError("Confirmation failed"))
                    .thenCompose(v -> terminal.printAt(10, 10, 
                        "Press any key to return..."))
                    .thenRun(() -> waitForAnyKey(() -> render()));
            }
        });
        
        inputReader.setOnEscape(text -> {
            keyboard.setEventConsumer(null);
            inputReader.close();
            cancelUninstall();
        });
    }
    
    // ===== STEP 4: UNINSTALLING =====
    
    private CompletableFuture<Void> renderUninstalling() {
        return terminal.printTitle("Uninstalling Package")
            .thenCompose(v -> terminal.printAt(5, 10, "Uninstalling..."))
            .thenCompose(v -> terminal.printAt(7, 10, "Please wait..."));
            // TODO: Add progress bar here when progress API is ready
        }
        
        private void performUninstall() {
        currentStep = Step.UNINSTALLING;
        render();
        
        nodeCommands.uninstallPackage(
                packageToUninstall.getPackageId(),
                deleteData
            )
            .thenAccept(v -> {
                currentStep = Step.COMPLETE;
                render();
            })
            .exceptionally(ex -> {
                terminal.clear()
                    .thenCompose(v -> terminal.printError(
                        "Uninstall failed: " + ex.getMessage()))
                    .thenCompose(v -> terminal.printAt(10, 10, 
                        "Press any key to return..."))
                    .thenRun(() -> waitForAnyKey(this::cancelUninstall));
                return null;
            });
    }
    
    // ===== STEP 5: COMPLETE =====
    
    private CompletableFuture<Void> renderComplete() {
        return terminal.printTitle("Uninstall Complete")
            .thenCompose(v -> terminal.printSuccess("✓ Package uninstalled successfully"))
            .thenCompose(v -> terminal.printAt(7, 10, 
                "Package: " + packageToUninstall.getName()))
            .thenCompose(v -> terminal.printAt(8, 10, 
                deleteData ? "Data deleted" : "Data preserved"))
            .thenCompose(v -> terminal.printAt(10, 10, 
                "Press any key to return..."))
            .thenRun(() -> waitForAnyKey(() -> {
                if (onCompleteCallback != null) {
                    onCompleteCallback.run();
                }
            }));
    }
    
    // ===== UTILITIES =====
    
    private CompletableFuture<Void> renderMenu(MenuContext menu, int startRow) {
        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        
        int row = startRow;
        int index = 1;
        
        for (MenuContext.MenuItem item : menu.getItems()) {
            if (item.type == MenuContext.MenuItemType.SEPARATOR) {
                final int currentRow = row;
                final String label = item.description != null ? 
                    " " + item.description + " " : "";
                future = future.thenCompose(v -> terminal.printAt(currentRow, 10, 
                    "─".repeat(20) + label + "─".repeat(20)));
                row++;
                continue;
            }
            
            if (item.type == MenuContext.MenuItemType.INFO) {
                final int currentRow = row;
                future = future.thenCompose(v -> terminal.printAt(currentRow, 12, 
                    "(" + item.description + ")"));
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
            .thenCompose(v -> terminal.printAt(selectRow, 10, 
                "Select option (or ESC to cancel):"))
            .thenRun(() -> startMenuNavigation(menu));
    }
    
    private void startMenuNavigation(MenuContext menu) {
        List<MenuContext.MenuItem> items = new java.util.ArrayList<>();
        for (MenuContext.MenuItem item : menu.getItems()) {
            if (item.type != MenuContext.MenuItemType.SEPARATOR &&
                item.type != MenuContext.MenuItemType.INFO) {
                items.add(item);
            }
        }
        
        KeyRunTable navigationKeys = new KeyRunTable();
        
        // Add number keys for direct selection
        for (int i = 0; i < Math.min(items.size(), 9); i++) {
            final int index = i;
            navigationKeys.setKeyRunnable(
                KeyCodeBytes.getNumeric(i, false),
                () -> {
                    keyboard.setEventConsumer(null);
                    MenuContext.MenuItem selected = items.get(index);
                    menu.navigate(selected.name);
                }
            );
            navigationKeys.setKeyRunnable(
                KeyCodeBytes.getNumeric(i, true),
                () -> {
                    keyboard.setEventConsumer(null);
                    MenuContext.MenuItem selected = items.get(index);
                    menu.navigate(selected.name);
                }
            );
        }
        
        navigationKeys.setKeyRunnable(KeyCodeBytes.ESCAPE, () -> {
            keyboard.setEventConsumer(null);
            cancelUninstall();
        });
        
        keyboard.setEventConsumer(event -> {
            if (event instanceof EphemeralRoutedEvent ephemeral) {
                try (ephemeral) {
                    if (ephemeral instanceof EphemeralKeyDownEvent ekd) {
                        navigationKeys.run(ekd.getKeyCodeBytes());
                    }
                }
            } else if (event instanceof KeyDownEvent keyDown) {
                navigationKeys.run(keyDown.getKeyCodeBytes());
            }
        });
    }
    
    private void waitForAnyKey(Runnable callback) {
        keyboard.setEventConsumer(event -> {
            keyboard.setEventConsumer(null);
            callback.run();
        });
    }
    
    private void cleanup() {
        keyboard.setEventConsumer(null);
        
        if (passwordReader != null) {
            passwordReader.close();
            passwordReader = null;
        }
    }
}
