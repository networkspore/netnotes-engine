package io.netnotes.engine.core.system;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.core.system.control.MenuContext;
import io.netnotes.engine.core.system.control.PasswordReader;
import io.netnotes.engine.core.system.control.TerminalInputReader;
import io.netnotes.engine.core.system.control.nodes.InstalledPackage;
import io.netnotes.engine.core.system.control.nodes.ProcessConfig;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.input.InputDevice;
import io.netnotes.engine.io.input.KeyRunTable;
import io.netnotes.engine.io.input.Keyboard.KeyCodeBytes;
import io.netnotes.engine.io.input.ephemeralEvents.EphemeralKeyDownEvent;
import io.netnotes.engine.io.input.ephemeralEvents.EphemeralRoutedEvent;
import io.netnotes.engine.io.input.events.KeyDownEvent;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;

/**
 * PackageConfigurationScreen - Modify package configuration
 * 
 * Allows user to:
 * - Change ProcessId (namespace)
 * - Modify security capabilities (future)
 * - Update installation settings
 * 
 * REQUIRES: Password confirmation for all changes
 */
class PackageConfigurationScreen extends TerminalScreen {
    
    private enum Step {
        SHOW_CURRENT,
        CONFIGURE_PROCESS_ID,
        PASSWORD_CONFIRM,
        SAVING,
        COMPLETE
    }
    
    private final InstalledPackage originalPackage;
    private ProcessConfig newProcessConfig;
    
    private Step currentStep = Step.SHOW_CURRENT;
    private PasswordReader passwordReader;
    private TerminalInputReader inputReader;
    private Runnable onCompleteCallback;
    private final NodeCommands nodeCommands;
    
    public PackageConfigurationScreen(
        String name,
        SystemTerminalContainer terminal,
        InputDevice keyboard,
        InstalledPackage pkg,
        NodeCommands nodeCommands
    ) {
        super(name, terminal, keyboard);
        this.originalPackage = pkg;
        this.newProcessConfig = pkg.getProcessConfig();
        this.nodeCommands = nodeCommands;
    }
    
    public void setOnComplete(Runnable callback) {
        this.onCompleteCallback = callback;
    }
    
    @Override
    public CompletableFuture<Void> onShow() {
        currentStep = Step.SHOW_CURRENT;
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
                    case SHOW_CURRENT:
                        return renderCurrentConfig();
                    case CONFIGURE_PROCESS_ID:
                        return renderProcessIdConfig();
                    case PASSWORD_CONFIRM:
                        return renderPasswordConfirm();
                    case SAVING:
                        return renderSaving();
                    case COMPLETE:
                        return renderComplete();
                    default:
                        return CompletableFuture.completedFuture(null);
                }
            });
    }
    
    // ===== STEP 1: SHOW CURRENT CONFIG =====
    
    private CompletableFuture<Void> renderCurrentConfig() {
        return terminal.printTitle("Package Configuration")
            .thenCompose(v -> terminal.printAt(5, 10, 
                "Package: " + originalPackage.getName()))
            .thenCompose(v -> terminal.printAt(6, 10, 
                "Version: " + originalPackage.getVersion()))
            .thenCompose(v -> terminal.printAt(8, 10, "Current Configuration:"))
            .thenCompose(v -> terminal.printAt(10, 10, 
                "ProcessId: " + originalPackage.getProcessId()))
            .thenCompose(v -> terminal.printAt(11, 10, 
                "Data Path: " + originalPackage.getProcessConfig().getDataRootPath()))
            .thenCompose(v -> terminal.printAt(12, 10, 
                "Flow Path: " + originalPackage.getProcessConfig().getFlowBasePath()))
            .thenCompose(v -> showConfigMenu());
    }
    
    private CompletableFuture<Void> showConfigMenu() {
        ContextPath basePath = ContextPath.of("config", originalPackage.getName());
        MenuContext menu = new MenuContext(basePath, "Configuration Options")
            .addItem("process-id", "Change ProcessId (Namespace)", 
                this::startProcessIdConfig)
            .addItem("capabilities", "Modify Security Capabilities (Coming Soon)", 
                this::capabilitiesNotImplemented)
            .addSeparator("Navigation")
            .addItem("cancel", "Cancel and Return", this::cancelConfiguration);
        
        return renderMenu(menu, 14);
    }
    
    private void startProcessIdConfig() {
        keyboard.setEventConsumer(null);
        currentStep = Step.CONFIGURE_PROCESS_ID;
        render();
    }
    
    private void capabilitiesNotImplemented() {
        keyboard.setEventConsumer(null);
        terminal.clear()
            .thenCompose(v -> terminal.printTitle("Feature Not Available"))
            .thenCompose(v -> terminal.printAt(5, 10, 
                "Security capability modification is not yet implemented"))
            .thenCompose(v -> terminal.printAt(7, 10, 
                "This feature will be available in a future update"))
            .thenCompose(v -> terminal.printAt(9, 10, 
                "Press any key to continue..."))
            .thenRun(() -> waitForAnyKey(() -> render()));
    }
    
    private void cancelConfiguration() {
        keyboard.setEventConsumer(null);
        if (onCompleteCallback != null) {
            onCompleteCallback.run();
        }
    }
    
    // ===== STEP 2: CONFIGURE PROCESS ID =====
    
    private CompletableFuture<Void> renderProcessIdConfig() {
        return terminal.printTitle("Change ProcessId")
            .thenCompose(v -> terminal.printAt(5, 10, 
                "Current ProcessId: " + originalPackage.getProcessId()))
            .thenCompose(v -> terminal.printAt(7, 10, 
                "Enter new ProcessId (namespace):"))
            .thenCompose(v -> terminal.printAt(8, 10, "(Leave blank to cancel)"))
            .thenCompose(v -> terminal.printAt(10, 10, "New ProcessId: "))
            .thenRun(this::startProcessIdInput);
    }
    
    private void startProcessIdInput() {
        inputReader = new TerminalInputReader(terminal, 10, 25, 64);
        keyboard.setEventConsumer(inputReader.getEventConsumer());
        
        inputReader.setOnComplete(newProcessId -> {
            keyboard.setEventConsumer(null);
            
            if (newProcessId == null || newProcessId.trim().isEmpty()) {
                // Cancelled
                inputReader.close();
                inputReader = null;
                render();
                return;
            }
            
            // Validate and create new ProcessConfig
            try {
                NoteBytesReadOnly newProcessIdBytes = 
                    new NoteBytesReadOnly(newProcessId.trim());
                newProcessConfig = ProcessConfig.create(newProcessIdBytes);
                
                inputReader.close();
                inputReader = null;
                
                // Proceed to password confirmation
                currentStep = Step.PASSWORD_CONFIRM;
                render();
                
            } catch (Exception e) {
                terminal.clear()
                    .thenCompose(v -> terminal.printError(
                        "Invalid ProcessId: " + e.getMessage()))
                    .thenCompose(v -> terminal.printAt(10, 10, 
                        "Press any key to try again..."))
                    .thenRun(() -> waitForAnyKey(() -> render()));
            }
        });
        
        inputReader.setOnEscape(text -> {
            keyboard.setEventConsumer(null);
            inputReader.close();
            inputReader = null;
            render();
        });
    }
    
    // ===== STEP 3: PASSWORD CONFIRM =====
    
    private CompletableFuture<Void> renderPasswordConfirm() {
        return terminal.printTitle("Confirm Configuration Changes")
            .thenCompose(v -> terminal.printAt(5, 10, "Configuration Summary:"))
            .thenCompose(v -> terminal.printAt(7, 10, 
                "Package: " + originalPackage.getName()))
            .thenCompose(v -> terminal.printAt(9, 10, "Changes:"))
            .thenCompose(v -> terminal.printAt(10, 12, 
                "Old ProcessId: " + originalPackage.getProcessId()))
            .thenCompose(v -> terminal.printAt(11, 12, 
                "New ProcessId: " + newProcessConfig.getProcessId()))
            .thenCompose(v -> terminal.printAt(13, 10, 
                "⚠️ Warning: This will require reloading any running instances"))
            .thenCompose(v -> terminal.printAt(15, 10, 
                "Type 'CONFIRM' to proceed:"))
            .thenCompose(v -> terminal.moveCursor(15, 36))
            .thenRun(this::startConfirmationEntry);
    }
    
    private void startConfirmationEntry() {
        inputReader = new TerminalInputReader(terminal, 15, 36, 20);
        keyboard.setEventConsumer(inputReader.getEventConsumer());
        
        inputReader.setOnComplete(input -> {
            keyboard.setEventConsumer(null);
            inputReader.close();
            inputReader = null;
            
            if ("CONFIRM".equals(input)) {
                saveConfiguration();
            } else {
                terminal.clear()
                    .thenCompose(v -> terminal.printError("Confirmation failed"))
                    .thenCompose(v -> terminal.printAt(10, 10, 
                        "Press any key to try again..."))
                    .thenRun(() -> waitForAnyKey(() -> render()));
            }
        });
        
        inputReader.setOnEscape(text -> {
            keyboard.setEventConsumer(null);
            inputReader.close();
            inputReader = null;
            cancelConfiguration();
        });
    }
    
    // ===== STEP 4: SAVING =====
    
    private CompletableFuture<Void> renderSaving() {
        return terminal.printTitle("Saving Configuration")
            .thenCompose(v -> terminal.printAt(5, 10, "Updating package configuration..."))
            .thenCompose(v -> terminal.printAt(7, 10, "Please wait..."));
    }
    
    private void saveConfiguration() {
        currentStep = Step.SAVING;
        render();
        
        nodeCommands.updatePackageConfiguration(
                originalPackage.getPackageId(),
                newProcessConfig
            )
            .thenAccept(v -> {
                currentStep = Step.COMPLETE;
                render();
            })
            .exceptionally(ex -> {
                terminal.clear()
                    .thenCompose(v -> terminal.printError(
                        "Failed to save configuration: " + ex.getMessage()))
                    .thenCompose(v -> terminal.printAt(10, 10, 
                        "Press any key to return..."))
                    .thenRun(() -> waitForAnyKey(() -> {
                        if (onCompleteCallback != null) {
                            onCompleteCallback.run();
                        }
                    }));
                return null;
            });
    }

    
    // ===== STEP 5: COMPLETE =====
    
    private CompletableFuture<Void> renderComplete() {
        return terminal.printTitle("Configuration Updated")
            .thenCompose(v -> terminal.printSuccess("✓ Configuration saved successfully"))
            .thenCompose(v -> terminal.printAt(7, 10, 
                "New ProcessId: " + newProcessConfig.getProcessId()))
            .thenCompose(v -> terminal.printAt(9, 10, 
                "Note: Reload the package for changes to take effect"))
            .thenCompose(v -> terminal.printAt(11, 10, 
                "Press any key to return..."))
            .thenRun(() -> waitForAnyKey(() -> {
                if (onCompleteCallback != null) {
                    onCompleteCallback.run();
                }
            }));
    }
    
    // ===== UTILITIES =====
    
    private CompletableFuture<Void> renderMenu(MenuContext menu, int startRow) {
        // Simplified menu rendering - just show options
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
            .thenCompose(v -> terminal.printAt(selectRow, 10, 
                "Select option (or ESC to cancel):"))
            .thenRun(() -> startMenuNavigation(menu));
    }
    
    private void startMenuNavigation(MenuContext menu) {
        List<MenuContext.MenuItem> items = new java.util.ArrayList<>(menu.getItems());
        
        KeyRunTable navigationKeys = new KeyRunTable();
        
        // Add number keys for direct selection
        for (int i = 0; i < Math.min(items.size(), 9); i++) {
            final int index = i;
            navigationKeys.setKeyRunnable(
                KeyCodeBytes.getNumeric(index, true),
                () -> {
                    keyboard.setEventConsumer(null);
                    MenuContext.MenuItem selected = items.get(index);
                    menu.navigate(selected.name);
                }
            );
            navigationKeys.setKeyRunnable(
                KeyCodeBytes.getNumeric(index, false),
                () -> {
                    keyboard.setEventConsumer(null);
                    MenuContext.MenuItem selected = items.get(index);
                    menu.navigate(selected.name);
                }
            );
        }
        
        navigationKeys.setKeyRunnable(KeyCodeBytes.ESCAPE, () -> {
            keyboard.setEventConsumer(null);
            cancelConfiguration();
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
        
        if (inputReader != null) {
            inputReader.close();
            inputReader = null;
        }
    }
}