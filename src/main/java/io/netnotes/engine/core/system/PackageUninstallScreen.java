package io.netnotes.engine.core.system;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.core.system.control.nodes.InstalledPackage;
import io.netnotes.engine.core.system.control.nodes.NodeInstance;
import io.netnotes.engine.core.system.control.terminal.RenderManager.RenderState;
import io.netnotes.engine.core.system.control.terminal.TextStyle;
import io.netnotes.engine.core.system.control.terminal.input.TerminalInputReader;
import io.netnotes.engine.io.input.KeyRunTable;
import io.netnotes.engine.io.input.Keyboard.KeyCodeBytes;
import io.netnotes.engine.io.input.ephemeralEvents.EphemeralKeyDownEvent;
import io.netnotes.engine.io.input.ephemeralEvents.EphemeralRoutedEvent;
import io.netnotes.engine.io.input.events.KeyDownEvent;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesRunnablePair;
import io.netnotes.engine.noteBytes.processing.IntCounter;

/**
 * PackageUninstallScreen - REFACTORED for pull-based rendering
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
    private final NodeCommands nodeCommands;
    
    // Mutable state
    private Step currentStep = Step.CHECK_INSTANCES;
    private List<NodeInstance> runningInstances = new ArrayList<>();
    private boolean deleteData = false;
    private NoteBytesReadOnly keyDownHandlerId;
    private TerminalInputReader inputReader;
    private Runnable onCompleteCallback;
    private String errorMessage = null;
    private String statusMessage = null;
    
    public PackageUninstallScreen(
        String name,
        SystemTerminalContainer terminal,
        InstalledPackage pkg,
        NodeCommands nodeCommands
    ) {
        super(name, terminal);
        this.packageToUninstall = pkg;
        this.nodeCommands = nodeCommands;
    }
    
    public void setOnComplete(Runnable callback) {
        this.onCompleteCallback = callback;
    }
    
    @Override
    public CompletableFuture<Void> onShow() {
        currentStep = Step.CHECK_INSTANCES;
        checkRunningInstances();
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public void onHide() {
        cleanup();
    }
    
    // ===== PULL-BASED RENDERING =====
    
    @Override
    public RenderState getRenderState() {
        return switch (currentStep) {
            case CHECK_INSTANCES -> buildCheckInstancesState();
            case SHOW_OPTIONS -> buildShowOptionsState();
            case PASSWORD_CONFIRM -> buildPasswordConfirmState();
            case UNINSTALLING -> buildUninstallingState();
            case COMPLETE -> buildCompleteState();
        };
    }
    
    // ===== STATE BUILDERS =====
    
    private RenderState buildCheckInstancesState() {
        RenderState.Builder builder = RenderState.builder();
        
        if (runningInstances.isEmpty() && statusMessage == null) {
            // Still checking
            builder.add((term, gen) -> 
                term.printAt(0, (term.getCols() - 17) / 2, "Uninstall Package", 
                    TextStyle.BOLD, gen));
            
            builder.add((term, gen) -> 
                term.printAt(5, 10, "Checking for running instances...", gen));
        } else if (runningInstances.isEmpty()) {
            // No instances, ready to proceed
            return buildShowOptionsState();
        } else {
            // Has instances - show warning
            builder.add((term, gen) -> 
                term.printAt(0, (term.getCols() - 17) / 2, "Cannot Uninstall", 
                    TextStyle.BOLD, gen));
            
            builder.add((term, gen) -> 
                term.printAt(5, 10, "⚠️ Package has running instances", 
                    TextStyle.WARNING, gen));
            
            builder.add((term, gen) -> 
                term.printAt(7, 10, "Package: " + packageToUninstall.getName(), gen));
            builder.add((term, gen) -> 
                term.printAt(8, 10, "Running instances: " + runningInstances.size(), gen));
            
            builder.add((term, gen) -> 
                term.printAt(10, 10, "The following instances must be stopped first:", 
                    TextStyle.BOLD, gen));
            
            IntCounter row = new IntCounter(12);
            for (NodeInstance instance : runningInstances) {
                row.increment();
                final int currentRow = row.get();
                builder.add((term, gen) -> 
                    term.printAt(currentRow, 12, "• " + instance.getInstanceId() + 
                        " [" + instance.getProcessId() + "]", gen));
            }
            
            // Menu
            builder.add((term, gen) -> 
                term.printAt(row.get() + 2, 10, "1. Stop All Instances", gen));
            builder.add((term, gen) -> 
                term.printAt(row.get() + 3, 10, "2. Cancel Uninstall", gen));
            builder.add((term, gen) -> 
                term.printAt(row.get() + 5, 10, "Select option (or ESC to cancel):", 
                    TextStyle.INFO, gen));
        }
        
        return builder.build();
    }
    
    private RenderState buildShowOptionsState() {
        RenderState.Builder builder = RenderState.builder();
        
        builder.add((term, gen) -> 
            term.printAt(0, (term.getCols() - 17) / 2, "Uninstall Options", 
                TextStyle.BOLD, gen));
        
        builder.add((term, gen) -> 
            term.printAt(5, 10, "Package: " + packageToUninstall.getName(), gen));
        builder.add((term, gen) -> 
            term.printAt(6, 10, "Version: " + packageToUninstall.getVersion(), gen));
        
        builder.add((term, gen) -> 
            term.printAt(8, 10, "Data path: " + 
                packageToUninstall.getProcessConfig().getDataRootPath(), 
                TextStyle.INFO, gen));
        
        builder.add((term, gen) -> 
            term.printAt(10, 10, "Choose uninstall option:", TextStyle.BOLD, gen));
        
        // Menu
        builder.add((term, gen) -> 
            term.printAt(12, 10, "1. Uninstall but keep data", gen));
        builder.add((term, gen) -> 
            term.printAt(13, 10, "2. Uninstall and delete all data", gen));
        
        builder.add((term, gen) -> 
            term.printAt(15, 10, "─".repeat(40), gen));
        builder.add((term, gen) -> 
            term.printAt(16, 10, "Future Features", TextStyle.INFO, gen));
        builder.add((term, gen) -> 
            term.printAt(17, 12, "(Browse data - Coming Soon)", TextStyle.INFO, gen));
        
        builder.add((term, gen) -> 
            term.printAt(19, 10, "─".repeat(40), gen));
        builder.add((term, gen) -> 
            term.printAt(20, 10, "3. Cancel Uninstall", gen));
        
        builder.add((term, gen) -> 
            term.printAt(22, 10, "Select option (or ESC to cancel):", 
                TextStyle.INFO, gen));
        
        return builder.build();
    }
    
    private RenderState buildPasswordConfirmState() {
        RenderState.Builder builder = RenderState.builder();
        
        builder.add((term, gen) -> 
            term.printAt(0, (term.getCols() - 17) / 2, "Confirm Uninstall", 
                TextStyle.BOLD, gen));
        
        builder.add((term, gen) -> 
            term.printAt(5, 10, "⚠️ WARNING", TextStyle.WARNING, gen));
        
        builder.add((term, gen) -> 
            term.printAt(7, 10, "You are about to uninstall:", gen));
        builder.add((term, gen) -> 
            term.printAt(8, 12, "Package: " + packageToUninstall.getName(), gen));
        builder.add((term, gen) -> 
            term.printAt(9, 12, "Version: " + packageToUninstall.getVersion(), gen));
        
        String dataMsg = deleteData ? "Data will be DELETED" : "Data will be KEPT";
        TextStyle dataStyle = deleteData ? TextStyle.WARNING : TextStyle.SUCCESS;
        builder.add((term, gen) -> 
            term.printAt(11, 10, dataMsg, dataStyle, gen));
        
        builder.add((term, gen) -> 
            term.printAt(13, 10, "Type 'CONFIRM' to proceed:", gen));
        
        return builder.build();
    }
    
    private RenderState buildUninstallingState() {
        RenderState.Builder builder = RenderState.builder();
        
        builder.add((term, gen) -> 
            term.printAt(0, (term.getCols() - 20) / 2, "Uninstalling Package", 
                TextStyle.BOLD, gen));
        
        builder.add((term, gen) -> 
            term.printAt(5, 10, "Uninstalling...", gen));
        builder.add((term, gen) -> 
            term.printAt(7, 10, "Please wait...", TextStyle.INFO, gen));
        
        if (statusMessage != null) {
            builder.add((term, gen) -> 
                term.printAt(9, 10, statusMessage, TextStyle.INFO, gen));
        }
        
        return builder.build();
    }
    
    private RenderState buildCompleteState() {
        RenderState.Builder builder = RenderState.builder();
        
        if (errorMessage != null) {
            builder.add((term, gen) -> 
                term.printAt(0, (term.getCols() - 18) / 2, "Uninstall Failed", 
                    TextStyle.BOLD, gen));
            
            builder.add((term, gen) -> 
                term.printAt(5, 10, "✗ Uninstall failed", TextStyle.ERROR, gen));
            builder.add((term, gen) -> 
                term.printAt(7, 10, "Error: " + errorMessage, TextStyle.ERROR, gen));
        } else {
            builder.add((term, gen) -> 
                term.printAt(0, (term.getCols() - 18) / 2, "Uninstall Complete", 
                    TextStyle.BOLD, gen));
            
            builder.add((term, gen) -> 
                term.printAt(5, 10, "✓ Package uninstalled successfully", 
                    TextStyle.SUCCESS, gen));
            
            builder.add((term, gen) -> 
                term.printAt(7, 10, "Package: " + packageToUninstall.getName(), gen));
            
            String dataMsg = deleteData ? "Data deleted" : "Data preserved";
            builder.add((term, gen) -> 
                term.printAt(8, 10, dataMsg, TextStyle.INFO, gen));
        }
        
        builder.add((term, gen) -> 
            term.printAt(10, 10, "Press any key to return...", gen));
        
        return builder.build();
    }
    
    // ===== STATE TRANSITIONS =====
    
    private void transitionTo(Step newStep) {
        currentStep = newStep;
        invalidate();
        
        switch (newStep) {
            case CHECK_INSTANCES -> checkRunningInstances();
            case SHOW_OPTIONS -> setupOptionsInput();
            case PASSWORD_CONFIRM -> startConfirmationEntry();
            case UNINSTALLING -> performUninstall();
            case COMPLETE -> setupCompleteInput();
        }
    }
    
    // ===== ACTIONS =====
    
    private void checkRunningInstances() {
        statusMessage = "Checking...";
        invalidate();
        
        nodeCommands.getInstancesByPackage(packageToUninstall.getPackageId())
            .thenAccept(instances -> {
                this.runningInstances = instances;
                statusMessage = null;
                
                if (instances.isEmpty()) {
                    transitionTo(Step.SHOW_OPTIONS);
                } else {
                    setupInstanceStopInput();
                    invalidate();
                }
            })
            .exceptionally(ex -> {
                errorMessage = "Failed to check instances: " + ex.getMessage();
                transitionTo(Step.COMPLETE);
                return null;
            });
    }
    
    private void setupInstanceStopInput() {
        KeyRunTable keys = new KeyRunTable(
            new NoteBytesRunnablePair(KeyCodeBytes.getNumeric(1, false), 
                this::stopAllInstances),
            new NoteBytesRunnablePair(KeyCodeBytes.getNumeric(1, true), 
                this::stopAllInstances),
            new NoteBytesRunnablePair(KeyCodeBytes.getNumeric(2, false), 
                this::cancelUninstall),
            new NoteBytesRunnablePair(KeyCodeBytes.getNumeric(2, true), 
                this::cancelUninstall),
            new NoteBytesRunnablePair(KeyCodeBytes.ESCAPE, this::cancelUninstall)
        );
        
        setupKeyHandler(keys);
    }
    
    private void stopAllInstances() {
        statusMessage = "Stopping " + runningInstances.size() + " instances...";
        invalidate();
        
        List<CompletableFuture<Void>> stopFutures = runningInstances.stream()
            .map(inst -> nodeCommands.unloadNode(inst.getInstanceId()))
            .toList();
        
        CompletableFuture.allOf(stopFutures.toArray(new CompletableFuture[0]))
            .thenAccept(v -> {
                statusMessage = "✓ All instances stopped";
                invalidate();
                
                terminal.waitForKeyPress(() -> transitionTo(Step.SHOW_OPTIONS));
            })
            .exceptionally(ex -> {
                errorMessage = "Failed to stop instances: " + ex.getMessage();
                invalidate();
                
                terminal.waitForKeyPress(() -> transitionTo(Step.CHECK_INSTANCES));
                return null;
            });
    }
    
    private void setupOptionsInput() {
        KeyRunTable keys = new KeyRunTable(
            new NoteBytesRunnablePair(KeyCodeBytes.getNumeric(1, false), () -> {
                deleteData = false;
                transitionTo(Step.PASSWORD_CONFIRM);
            }),
            new NoteBytesRunnablePair(KeyCodeBytes.getNumeric(1, true), () -> {
                deleteData = false;
                transitionTo(Step.PASSWORD_CONFIRM);
            }),
            new NoteBytesRunnablePair(KeyCodeBytes.getNumeric(2, false), () -> {
                deleteData = true;
                transitionTo(Step.PASSWORD_CONFIRM);
            }),
            new NoteBytesRunnablePair(KeyCodeBytes.getNumeric(2, true), () -> {
                deleteData = true;
                transitionTo(Step.PASSWORD_CONFIRM);
            }),
            new NoteBytesRunnablePair(KeyCodeBytes.getNumeric(3, false), 
                this::cancelUninstall),
            new NoteBytesRunnablePair(KeyCodeBytes.getNumeric(3, true), 
                this::cancelUninstall),
            new NoteBytesRunnablePair(KeyCodeBytes.ESCAPE, this::cancelUninstall)
        );
        
        setupKeyHandler(keys);
    }
    
    private void startConfirmationEntry() {
        removeKeyDownHandler();
        inputReader = new TerminalInputReader(terminal, 13, 36, 20);
        
        inputReader.setOnComplete(input -> {
            inputReader.close();
            inputReader = null;
            
            if ("CONFIRM".equals(input)) {
                transitionTo(Step.UNINSTALLING);
            } else {
                errorMessage = "Confirmation failed";
                invalidate();
                terminal.waitForKeyPress(() -> {
                    errorMessage = null;
                    transitionTo(Step.PASSWORD_CONFIRM);
                });
            }
        });
        
        inputReader.setOnEscape(text -> {
            inputReader.close();
            inputReader = null;
            cancelUninstall();
        });
    }
    
    private void performUninstall() {
        statusMessage = "Uninstalling...";
        invalidate();
        
        nodeCommands.uninstallPackage(
                packageToUninstall.getPackageId(),
                deleteData
            )
            .thenAccept(v -> {
                errorMessage = null;
                statusMessage = null;
                transitionTo(Step.COMPLETE);
            })
            .exceptionally(ex -> {
                errorMessage = ex.getMessage();
                statusMessage = null;
                transitionTo(Step.COMPLETE);
                return null;
            });
    }
    
    private void setupCompleteInput() {
        removeKeyDownHandler();
        terminal.waitForKeyPress(() -> {
            if (onCompleteCallback != null) {
                onCompleteCallback.run();
            }
        });
    }
    
    private void cancelUninstall() {
        if (onCompleteCallback != null) {
            onCompleteCallback.run();
        }
    }
    
    // ===== INPUT MANAGEMENT =====
    
    private void setupKeyHandler(KeyRunTable keys) {
        removeKeyDownHandler();
        keyDownHandlerId = terminal.addKeyDownHandler(event -> {
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
    
    private void removeKeyDownHandler() {
        if (keyDownHandlerId != null) {
            terminal.removeKeyDownHandler(keyDownHandlerId);
            keyDownHandlerId = null;
        }
    }
    
    // ===== CLEANUP =====
    
    private void cleanup() {
        removeKeyDownHandler();
        
        if (inputReader != null) {
            inputReader.close();
            inputReader = null;
        }
    }
}