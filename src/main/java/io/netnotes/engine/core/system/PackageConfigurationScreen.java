package io.netnotes.engine.core.system;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.core.system.control.PasswordReader;
import io.netnotes.engine.core.system.control.nodes.InstalledPackage;
import io.netnotes.engine.core.system.control.nodes.ProcessConfig;
import io.netnotes.engine.core.system.control.terminal.TerminalRenderElement;
import io.netnotes.engine.core.system.control.terminal.TerminalRenderState;
import io.netnotes.engine.core.system.control.terminal.TextStyle;
import io.netnotes.engine.core.system.control.terminal.TerminalRenderState.TerminalStateBuilder;
import io.netnotes.engine.core.system.control.terminal.input.TerminalInputReader;
import io.netnotes.engine.io.input.KeyRunTable;
import io.netnotes.engine.io.input.Keyboard.KeyCodeBytes;
import io.netnotes.engine.io.input.ephemeralEvents.EphemeralKeyDownEvent;
import io.netnotes.engine.io.input.ephemeralEvents.EphemeralRoutedEvent;
import io.netnotes.engine.io.input.events.KeyDownEvent;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesRunnablePair;

/**
 * PackageConfigurationScreen - REFACTORED for pull-based rendering
 * 
 * State is stored in fields, rendering is pulled via getRenderState()
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
    private final NodeCommands nodeCommands;
    
    // Mutable state
    private Step currentStep = Step.SHOW_CURRENT;
    private ProcessConfig newProcessConfig;
    private PasswordReader passwordReader;
    private TerminalInputReader inputReader;
    private NoteBytesReadOnly handlerId = null;
    private Runnable onCompleteCallback;
    private String errorMessage = null;
    
    public PackageConfigurationScreen(
        String name,
        SystemApplication systemApplication,
        InstalledPackage pkg,
        NodeCommands nodeCommands
    ) {
        super(name, systemApplication);
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
        invalidate();
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public void onHide() {
        cleanup();
    }
    
    // ===== PULL-BASED RENDERING =====
    
    @Override
    public TerminalRenderState getRenderState() {
        return switch (currentStep) {
            case SHOW_CURRENT -> buildCurrentConfigState();
            case CONFIGURE_PROCESS_ID -> buildProcessIdConfigState();
            case PASSWORD_CONFIRM -> buildPasswordConfirmState();
            case SAVING -> buildSavingState();
            case COMPLETE -> buildCompleteState();
        };
    }
    
    // ===== STATE BUILDERS =====
    
    private TerminalRenderState buildCurrentConfigState() {
        TerminalStateBuilder builder = TerminalRenderState.builder();
        
        // Title
        builder.add((term) -> 
            term.printAt(0, (PackageConfigurationScreen.this.systemApplication.getTerminal().getCols() - 22) / 2, "Package Configuration", 
                TextStyle.BOLD));
        
        // Package info
        builder.add((term) -> 
            term.printAt(5, 10, "Package: " + originalPackage.getName()));
        builder.add((term) -> 
            term.printAt(6, 10, "Version: " + originalPackage.getVersion()));
        
        // Current config
        builder.add((term) -> 
            term.printAt(8, 10, "Current Configuration:", TextStyle.BOLD));
        builder.add((term) -> 
            term.printAt(10, 10, "ProcessId: " + originalPackage.getProcessId()));
        builder.add((term) -> 
            term.printAt(11, 10, "Data Path: " + 
                originalPackage.getProcessConfig().getDataRootPath()));
        builder.add((term) -> 
            term.printAt(12, 10, "Flow Path: " + 
                originalPackage.getProcessConfig().getFlowBasePath()));
        
        // Menu
        builder.addAll(buildMenuElements(14));
        
        return builder.build();
    }
    
    private TerminalRenderState buildProcessIdConfigState() {
        TerminalStateBuilder builder = TerminalRenderState.builder();
        
        builder.add((term) -> 
            term.printAt(0, (PackageConfigurationScreen.this.systemApplication.getTerminal().getCols() - 16) / 2, "Change ProcessId", 
                TextStyle.BOLD));
        
        builder.add((term) -> 
            term.printAt(5, 10, "Current ProcessId: " + originalPackage.getProcessId()));
        
        builder.add((term) -> 
            term.printAt(7, 10, "Enter new ProcessId (namespace):"));
        builder.add((term) -> 
            term.printAt(8, 10, "(Leave blank to cancel)", TextStyle.INFO));
        
        builder.add((term) -> 
            term.printAt(10, 10, "New ProcessId: "));
        
        return builder.build();
    }
    
    private TerminalRenderState buildPasswordConfirmState() {
        TerminalStateBuilder builder = TerminalRenderState.builder();
        
        builder.add((term) -> 
            term.printAt(0, (PackageConfigurationScreen.this.systemApplication.getTerminal().getCols() - 33) / 2, 
                "Confirm Configuration Changes", TextStyle.BOLD));
        
        builder.add((term) -> 
            term.printAt(5, 10, "Configuration Summary:", TextStyle.BOLD));
        
        builder.add((term) -> 
            term.printAt(7, 10, "Package: " + originalPackage.getName()));
        
        builder.add((term) -> 
            term.printAt(9, 10, "Changes:", TextStyle.BOLD));
        builder.add((term) -> 
            term.printAt(10, 12, "Old ProcessId: " + originalPackage.getProcessId()));
        builder.add((term) -> 
            term.printAt(11, 12, "New ProcessId: " + newProcessConfig.getProcessId()));
        
        builder.add((term) -> 
            term.printAt(13, 10, 
                "⚠️ Warning: This will require reloading any running instances", 
                TextStyle.WARNING));
        
        builder.add((term) -> 
            term.printAt(15, 10, "Type 'CONFIRM' to proceed:"));
        
        return builder.build();
    }
    
    private TerminalRenderState buildSavingState() {
        TerminalStateBuilder builder = TerminalRenderState.builder();
        
        builder.add((term) -> 
            term.printAt(0, (PackageConfigurationScreen.this.systemApplication.getTerminal().getCols() - 22) / 2, "Saving Configuration", 
                TextStyle.BOLD));
        
        builder.add((term) -> 
            term.printAt(5, 10, "Updating package configuration..."));
        builder.add((term) -> 
            term.printAt(7, 10, "Please wait...", TextStyle.INFO));
        
        return builder.build();
    }
    
    private TerminalRenderState buildCompleteState() {
        TerminalStateBuilder builder = TerminalRenderState.builder();
        
        if (errorMessage != null) {
            builder.add((term) -> 
                term.printAt(0, (PackageConfigurationScreen.this.systemApplication.getTerminal().getCols() - 20) / 2, "Configuration Failed", 
                    TextStyle.BOLD));
            
            builder.add((term) -> 
                term.printAt(5, 10, errorMessage, TextStyle.ERROR));
        } else {
            builder.add((term) -> 
                term.printAt(0, (PackageConfigurationScreen.this.systemApplication.getTerminal().getCols() - 21) / 2, "Configuration Updated", 
                    TextStyle.BOLD));
            
            builder.add((term) -> 
                term.printAt(5, 10, "✓ Configuration saved successfully", 
                    TextStyle.SUCCESS));
            builder.add((term) -> 
                term.printAt(7, 10, "New ProcessId: " + newProcessConfig.getProcessId()));
            builder.add((term) -> 
                term.printAt(9, 10, 
                    "Note: Reload the package for changes to take effect", 
                    TextStyle.INFO));
        }
        
        builder.add((term) -> 
            term.printAt(11, 10, "Press any key to return..."));
        
        return builder.build();
    }
    
    // ===== MENU ELEMENTS =====
    
    private List<TerminalRenderElement> buildMenuElements(int startRow) {
        List<TerminalRenderElement> elements = new ArrayList<>();
        
        elements.add((term) -> 
            term.printAt(startRow, 10, "1. Change ProcessId (Namespace)"));
        elements.add((term) -> 
            term.printAt(startRow + 1, 10, 
                "2. Modify Security Capabilities (Coming Soon)", TextStyle.INFO));
        elements.add((term) -> 
            term.printAt(startRow + 3, 10, "─".repeat(40)));
        elements.add((term) -> 
            term.printAt(startRow + 4, 10, "3. Cancel and Return"));
        
        elements.add((term) -> 
            term.printAt(startRow + 6, 10, "Select option (or ESC to cancel):", 
                TextStyle.INFO));
        
        return elements;
    }
    
    // ===== STATE TRANSITIONS =====
    
    private void transitionTo(Step newStep) {
        currentStep = newStep;
        invalidate();
        
        // Setup interactions for new step
        switch (newStep) {
            case SHOW_CURRENT -> setupMainMenuInput();
            case CONFIGURE_PROCESS_ID -> startProcessIdInput();
            case PASSWORD_CONFIRM -> startConfirmationEntry();
            case SAVING -> performSave();
            case COMPLETE -> setupCompleteInput();
        }
    }
    
    // ===== INPUT HANDLERS =====
    
    private void setupMainMenuInput() {
        KeyRunTable keys = new KeyRunTable(
            new NoteBytesRunnablePair(KeyCodeBytes.getNumeric(1, false), 
                () -> transitionTo(Step.CONFIGURE_PROCESS_ID)),
            new NoteBytesRunnablePair(KeyCodeBytes.getNumeric(1, true), 
                () -> transitionTo(Step.CONFIGURE_PROCESS_ID)),
            new NoteBytesRunnablePair(KeyCodeBytes.getNumeric(2, false), 
                this::showCapabilitiesNotImplemented),
            new NoteBytesRunnablePair(KeyCodeBytes.getNumeric(2, true), 
                this::showCapabilitiesNotImplemented),
            new NoteBytesRunnablePair(KeyCodeBytes.getNumeric(3, false), 
                this::cancelConfiguration),
            new NoteBytesRunnablePair(KeyCodeBytes.getNumeric(3, true), 
                this::cancelConfiguration),
            new NoteBytesRunnablePair(KeyCodeBytes.ESCAPE, this::cancelConfiguration)
        );
        
        removeKeyDownHandler();
        handlerId = systemApplication.getTerminal().addKeyDownHandler(event -> {
            if (event instanceof EphemeralRoutedEvent ephemeral) {
                try (ephemeral) {
                    if (ephemeral instanceof EphemeralKeyDownEvent ekd) {
                        keys.run(ekd.getKeyCodeBytes());
                    }
                }
            } else if (event instanceof KeyDownEvent keyDown) {
                keys.run(keyDown.getKeyCodeBytes());
            }
        });
    }
    
    private void startProcessIdInput() {
        removeKeyDownHandler();
        inputReader = new TerminalInputReader(systemApplication.getTerminal(), 10, 25, 64);
        
        inputReader.setOnComplete(newProcessId -> {
            inputReader.close();
            inputReader = null;
            
            if (newProcessId == null || newProcessId.trim().isEmpty()) {
                transitionTo(Step.SHOW_CURRENT);
                return;
            }
            
            try {
                NoteBytesReadOnly newProcessIdBytes = 
                    new NoteBytesReadOnly(newProcessId.trim());
                newProcessConfig = ProcessConfig.create(newProcessIdBytes);
                transitionTo(Step.PASSWORD_CONFIRM);
            } catch (Exception e) {
                errorMessage = "Invalid ProcessId: " + e.getMessage();
                // Show error briefly then return to input
                invalidate();
                systemApplication.getTerminal().waitForKeyPress(() -> {
                    errorMessage = null;
                    transitionTo(Step.CONFIGURE_PROCESS_ID);
                });
            }
        });
        
        inputReader.setOnEscape(text -> {
            inputReader.close();
            inputReader = null;
            transitionTo(Step.SHOW_CURRENT);
        });
    }
    
    private void startConfirmationEntry() {
        removeKeyDownHandler();
        inputReader = new TerminalInputReader(systemApplication.getTerminal(), 15, 36, 20);
        
        inputReader.setOnComplete(input -> {
            inputReader.close();
            inputReader = null;
            
            if ("CONFIRM".equals(input)) {
                transitionTo(Step.SAVING);
            } else {
                errorMessage = "Confirmation failed";
                invalidate();
                systemApplication.getTerminal().waitForKeyPress(() -> {
                    errorMessage = null;
                    transitionTo(Step.PASSWORD_CONFIRM);
                });
            }
        });
        
        inputReader.setOnEscape(text -> {
            inputReader.close();
            inputReader = null;
            cancelConfiguration();
        });
    }
    
    private void performSave() {
        nodeCommands.updatePackageConfiguration(
                originalPackage.getPackageId(),
                newProcessConfig
            )
            .thenAccept(v -> {
                errorMessage = null;
                transitionTo(Step.COMPLETE);
            })
            .exceptionally(ex -> {
                errorMessage = "Failed to save: " + ex.getMessage();
                transitionTo(Step.COMPLETE);
                return null;
            });
    }
    
    private void setupCompleteInput() {
        removeKeyDownHandler();
        systemApplication.getTerminal().waitForKeyPress(() -> {
            if (onCompleteCallback != null) {
                onCompleteCallback.run();
            }
        });
    }
    
    private void showCapabilitiesNotImplemented() {
        // TODO: Show temporary message
        // For now, just do nothing
    }
    
    private void cancelConfiguration() {
        if (onCompleteCallback != null) {
            onCompleteCallback.run();
        }
    }
    
    // ===== CLEANUP =====
    
    private void removeKeyDownHandler() {
        if (handlerId != null) {
            systemApplication.getTerminal().removeKeyDownHandler(handlerId);
            handlerId = null;
        }
    }
    
    private void cleanup() {
        removeKeyDownHandler();
        
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