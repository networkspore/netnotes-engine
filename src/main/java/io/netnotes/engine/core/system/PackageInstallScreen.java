package io.netnotes.engine.core.system;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.core.system.control.nodes.PackageInfo;
import io.netnotes.engine.core.system.control.nodes.PackageManifest;
import io.netnotes.engine.core.system.control.nodes.ProcessConfig;
import io.netnotes.engine.core.system.control.nodes.security.PathCapability;
import io.netnotes.engine.core.system.control.nodes.security.PolicyManifest;
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

/**
 * PackageInstallScreen - REFACTORED for pull-based rendering
 */
class PackageInstallScreen extends TerminalScreen {
    
    private enum Step {
        PACKAGE_OVERVIEW,
        REVIEW_CAPABILITIES,
        CHOOSE_NAMESPACE,
        PASSWORD_CONFIRM,
        INSTALLING,
        ASK_LOAD_IMMEDIATELY,
        COMPLETE
    }
    
    private final PackageInfo packageInfo;
    private final NodeCommands nodeCommands;
    
    // Mutable state
    private Step currentStep = Step.PACKAGE_OVERVIEW;
    private ProcessConfig chosenProcessConfig;
    private boolean capabilitiesApproved = false;
    private boolean loadImmediately = false;
    private NoteBytesReadOnly keyPressHandlerId = null;
    private TerminalInputReader inputReader;
    private Runnable onCompleteCallback;
    private String errorMessage = null;
    private String statusMessage = null;
    
    public PackageInstallScreen(
        String name, 
        SystemTerminalContainer terminal, 
        PackageInfo packageInfo,
        NodeCommands nodeCommands
    ) {
        super(name, terminal);
        this.packageInfo = packageInfo;
        this.nodeCommands = nodeCommands;
    }
    
    public void setOnComplete(Runnable callback) {
        this.onCompleteCallback = callback;
    }
    
    @Override
    public CompletableFuture<Void> onShow() {
        currentStep = Step.PACKAGE_OVERVIEW;
        invalidate();
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
            case PACKAGE_OVERVIEW -> buildPackageOverviewState();
            case REVIEW_CAPABILITIES -> buildCapabilityReviewState();
            case CHOOSE_NAMESPACE -> buildNamespaceChoiceState();
            case PASSWORD_CONFIRM -> buildPasswordConfirmState();
            case INSTALLING -> buildInstallingState();
            case ASK_LOAD_IMMEDIATELY -> buildAskLoadState();
            case COMPLETE -> buildCompleteState();
        };
    }
    
    // ===== STATE BUILDERS =====
    
    private RenderState buildPackageOverviewState() {
        RenderState.Builder builder = RenderState.builder();
        
        builder.add((term) -> 
            term.printAt(0, (PackageInstallScreen.this.terminal.getCols() - 15) / 2, "Install Package", 
                TextStyle.BOLD));
        
        builder.add((term) -> 
            term.printAt(5, 10, "Package: " + packageInfo.getName()));
        builder.add((term) -> 
            term.printAt(6, 10, "Version: " + packageInfo.getVersion()));
        builder.add((term) -> 
            term.printAt(7, 10, "Category: " + packageInfo.getCategory()));
        builder.add((term) -> 
            term.printAt(8, 10, String.format("Size: %.2f MB", 
                packageInfo.getSize() / (1024.0 * 1024.0))));
        builder.add((term) -> 
            term.printAt(9, 10, "Repository: " + packageInfo.getRepository()));
        
        builder.add((term) -> 
            term.printAt(11, 10, "Description:", TextStyle.BOLD));
        
        // Add description lines
        String desc = packageInfo.getDescription();
        String[] lines = wrapText(desc, 60);
        for (int i = 0; i < Math.min(lines.length, 5); i++) {
            final int row = 12 + i;
            final String line = lines[i];
            builder.add((term) -> term.printAt(row, 10, line));
        }
        
        // Menu
        builder.add((term) -> 
            term.printAt(19, 10, "1. Continue with installation"));
        builder.add((term) -> 
            term.printAt(20, 10, "2. Cancel installation"));
        builder.add((term) -> 
            term.printAt(22, 10, "Select option (or ESC to cancel):", 
                TextStyle.INFO));
        
        return builder.build();
    }
    
    private RenderState buildCapabilityReviewState() {
        RenderState.Builder builder = RenderState.builder();
        
        PolicyManifest policy = PolicyManifest.fromNoteBytes(
            packageInfo.getManifest().getMetadata());
        List<PathCapability> capabilities = policy.getRequestedCapabilities();
        
        builder.add((term) -> 
            term.printAt(0, (PackageInstallScreen.this.terminal.getCols() - 15) / 2, "Security Review", 
                TextStyle.BOLD));
        
        builder.add((term) -> 
            term.printAt(5, 10, "This package requests the following capabilities:"));
        builder.add((term) -> 
            term.printAt(6, 10, "Review carefully before approving.", 
                TextStyle.WARNING));
        
        if (capabilities.isEmpty()) {
            builder.add((term) -> 
                term.printAt(8, 10, "No special capabilities requested", 
                    TextStyle.INFO));
        } else {
            int row = 8;
            for (PathCapability cap : capabilities) {
                final int currentRow = row;
                builder.add((term) -> 
                    term.printAt(currentRow, 10, "• " + cap.getDescription()));
                builder.add((term) -> 
                    term.printAt(currentRow + 1, 12, "Reason: " + cap.getReason(), 
                        TextStyle.INFO));
                row += 2;
            }
        }
        
        if (policy.hasSensitivePaths()) {
            builder.add((term) -> 
                term.printAt(16, 10, "⚠️ WARNING: Sensitive system paths requested", 
                    TextStyle.WARNING));
        }
        
        // Menu
        String approveDesc = policy.hasSensitivePaths() 
            ? "1. Approve (WARNING: Sensitive access)" 
            : "1. Approve capabilities";
        
        builder.add((term) -> term.printAt(18, 10, approveDesc));
        builder.add((term) -> 
            term.printAt(19, 10, "2. Deny and cancel installation"));
        builder.add((term) -> 
            term.printAt(21, 10, "Select option (or ESC to cancel):", 
                TextStyle.INFO));
        
        return builder.build();
    }
    
    private RenderState buildNamespaceChoiceState() {
        RenderState.Builder builder = RenderState.builder();
        
        PackageManifest manifest = packageInfo.getManifest();
        PackageManifest.NamespaceRequirement nsReq = manifest.getNamespaceRequirement();
        NoteBytesReadOnly defaultNs = getDefaultNamespace(nsReq);
        
        builder.add((term) -> 
            term.printAt(0, (PackageInstallScreen.this.terminal.getCols() - 31) / 2, 
                "Choose Installation Namespace", TextStyle.BOLD));
        
        if (nsReq.mode() == PackageManifest.NamespaceMode.DEFAULT) {
            builder.add((term) -> 
                term.printAt(5, 10, "Suggested namespace: " + nsReq.namespace()));
        } else {
            builder.add((term) -> 
                term.printAt(5, 10, "Choose where this package will be installed:"));
        }
        
        builder.add((term) -> 
            term.printAt(7, 10, "1. Default: " + defaultNs + " (Standard installation)"));
        builder.add((term) -> 
            term.printAt(8, 10, "2. Cancel installation"));
        builder.add((term) -> 
            term.printAt(10, 10, "Select option (or ESC to cancel):", 
                TextStyle.INFO));
        
        return builder.build();
    }
    
    private RenderState buildPasswordConfirmState() {
        RenderState.Builder builder = RenderState.builder();
        
        builder.add((term) -> 
            term.printAt(0, (PackageInstallScreen.this.terminal.getCols() - 20) / 2, "Confirm Installation", 
                TextStyle.BOLD));
        
        builder.add((term) -> 
            term.printAt(5, 10, "Ready to install:", TextStyle.BOLD));
        builder.add((term) -> 
            term.printAt(7, 10, "Package: " + packageInfo.getName()));
        builder.add((term) -> 
            term.printAt(8, 10, "Namespace: " + chosenProcessConfig.getProcessId()));
        
        builder.add((term) -> 
            term.printAt(10, 10, "Type 'INSTALL' to proceed:"));
        
        return builder.build();
    }
    
    private RenderState buildInstallingState() {
        RenderState.Builder builder = RenderState.builder();
        
        builder.add((term) -> 
            term.printAt(0, (PackageInstallScreen.this.terminal.getCols() - 18) / 2, "Installing Package", 
                TextStyle.BOLD));
        
        builder.add((term) -> 
            term.printAt(5, 10, "Installing..."));
        builder.add((term) -> 
            term.printAt(7, 10, "This may take a moment.", TextStyle.INFO));
        
        if (statusMessage != null) {
            builder.add((term) -> 
                term.printAt(9, 10, statusMessage, TextStyle.INFO));
        }
        
        return builder.build();
    }
    
    private RenderState buildAskLoadState() {
        RenderState.Builder builder = RenderState.builder();
        
        builder.add((term) -> 
            term.printAt(0, (PackageInstallScreen.this.terminal.getCols() - 21) / 2, "Installation Complete", 
                TextStyle.BOLD));
        
        builder.add((term) -> 
            term.printAt(5, 10, "✓ Package installed successfully!", 
                TextStyle.SUCCESS));
        builder.add((term) -> 
            term.printAt(7, 10, "Package: " + packageInfo.getName()));
        builder.add((term) -> 
            term.printAt(8, 10, "Namespace: " + chosenProcessConfig.getProcessId()));
        
        builder.add((term) -> 
            term.printAt(10, 10, "1. Yes, load the package now"));
        builder.add((term) -> 
            term.printAt(11, 10, "2. No, I'll load it later"));
        builder.add((term) -> 
            term.printAt(13, 10, "Select option:", TextStyle.INFO));
        
        return builder.build();
    }
    
    private RenderState buildCompleteState() {
        RenderState.Builder builder = RenderState.builder();
        
        if (errorMessage != null) {
            builder.add((term) -> 
                term.printAt(0, (PackageInstallScreen.this.terminal.getCols() - 18) / 2, "Installation Failed", 
                    TextStyle.BOLD));
            
            builder.add((term) -> 
                term.printAt(5, 10, "✗ Installation failed", TextStyle.ERROR));
            builder.add((term) -> 
                term.printAt(7, 10, "Error: " + errorMessage, TextStyle.ERROR));
        } else {
            builder.add((term) -> 
                term.printAt(0, (PackageInstallScreen.this.terminal.getCols() - 21) / 2, "Installation Complete", 
                    TextStyle.BOLD));
            
            builder.add((term) -> 
                term.printAt(5, 10, "✓ Installation successful", TextStyle.SUCCESS));
            
            if (loadImmediately && statusMessage != null) {
                builder.add((term) -> 
                    term.printAt(7, 10, statusMessage, TextStyle.SUCCESS));
            }
        }
        
        builder.add((term) -> 
            term.printAt(10, 10, "Press any key to return..."));
        
        return builder.build();
    }
    
    // ===== STATE TRANSITIONS =====
    
    private void transitionTo(Step newStep) {
        currentStep = newStep;
        invalidate();
        
        switch (newStep) {
            case PACKAGE_OVERVIEW -> setupOverviewInput();
            case REVIEW_CAPABILITIES -> setupCapabilitiesInput();
            case CHOOSE_NAMESPACE -> setupNamespaceInput();
            case PASSWORD_CONFIRM -> startConfirmationEntry();
            case INSTALLING -> performInstallation();
            case ASK_LOAD_IMMEDIATELY -> setupLoadInput();
            case COMPLETE -> setupCompleteInput();
        }
    }
    
    // ===== INPUT HANDLERS =====
    
    private void setupOverviewInput() {
        KeyRunTable keys = new KeyRunTable(
            new NoteBytesRunnablePair(KeyCodeBytes.getNumeric(1, false), 
                () -> transitionTo(Step.REVIEW_CAPABILITIES)),
            new NoteBytesRunnablePair(KeyCodeBytes.getNumeric(1, true), 
                () -> transitionTo(Step.REVIEW_CAPABILITIES)),
            new NoteBytesRunnablePair(KeyCodeBytes.getNumeric(2, false), 
                this::cancelInstallation),
            new NoteBytesRunnablePair(KeyCodeBytes.getNumeric(2, true), 
                this::cancelInstallation),
            new NoteBytesRunnablePair(KeyCodeBytes.ESCAPE, this::cancelInstallation)
        );
        
        setupKeyHandler(keys);
    }
    
    private void setupCapabilitiesInput() {
        KeyRunTable keys = new KeyRunTable(
            new NoteBytesRunnablePair(KeyCodeBytes.getNumeric(1, false), 
                this::approveCapabilities),
            new NoteBytesRunnablePair(KeyCodeBytes.getNumeric(1, true), 
                this::approveCapabilities),
            new NoteBytesRunnablePair(KeyCodeBytes.getNumeric(2, false), 
                this::denyCapabilities),
            new NoteBytesRunnablePair(KeyCodeBytes.getNumeric(2, true), 
                this::denyCapabilities),
            new NoteBytesRunnablePair(KeyCodeBytes.ESCAPE, this::denyCapabilities)
        );
        
        setupKeyHandler(keys);
    }
    
    private void setupNamespaceInput() {
        PackageManifest manifest = packageInfo.getManifest();
        PackageManifest.NamespaceRequirement nsReq = manifest.getNamespaceRequirement();
        
        // Auto-advance if REQUIRED
        if (nsReq.mode() == PackageManifest.NamespaceMode.REQUIRED) {
            selectNamespace(nsReq.namespace());
            return;
        }
        
        NoteBytesReadOnly defaultNs = getDefaultNamespace(nsReq);
        
        KeyRunTable keys = new KeyRunTable(
            new NoteBytesRunnablePair(KeyCodeBytes.getNumeric(1, false), 
                () -> selectNamespace(defaultNs)),
            new NoteBytesRunnablePair(KeyCodeBytes.getNumeric(1, true), 
                () -> selectNamespace(defaultNs)),
            new NoteBytesRunnablePair(KeyCodeBytes.getNumeric(2, false), 
                this::cancelInstallation),
            new NoteBytesRunnablePair(KeyCodeBytes.getNumeric(2, true), 
                this::cancelInstallation),
            new NoteBytesRunnablePair(KeyCodeBytes.ESCAPE, this::cancelInstallation)
        );
        
        setupKeyHandler(keys);
    }
    
    private void startConfirmationEntry() {
        removeKeyPressHandler();
        inputReader = new TerminalInputReader(terminal, 10, 36, 20);
        
        inputReader.setOnComplete(input -> {
            inputReader.close();
            inputReader = null;
            
            if ("INSTALL".equals(input)) {
                transitionTo(Step.INSTALLING);
            } else {
                errorMessage = "Installation cancelled";
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
            cancelInstallation();
        });
    }
    
    private void performInstallation() {
        PolicyManifest policyManifest = PolicyManifest.fromNoteBytes(
            packageInfo.getManifest().getMetadata());
        
        nodeCommands.installPackage(
                packageInfo,
                chosenProcessConfig,
                policyManifest,
                false
            )
            .thenAccept(installedPackage -> {
                errorMessage = null;
                transitionTo(Step.ASK_LOAD_IMMEDIATELY);
            })
            .exceptionally(ex -> {
                errorMessage = ex.getMessage();
                transitionTo(Step.COMPLETE);
                return null;
            });
    }
    
    private void setupLoadInput() {
        KeyRunTable keys = new KeyRunTable(
            new NoteBytesRunnablePair(KeyCodeBytes.getNumeric(1, false), 
                this::loadPackageNow),
            new NoteBytesRunnablePair(KeyCodeBytes.getNumeric(1, true), 
                this::loadPackageNow),
            new NoteBytesRunnablePair(KeyCodeBytes.getNumeric(2, false), 
                this::skipLoadPackage),
            new NoteBytesRunnablePair(KeyCodeBytes.getNumeric(2, true), 
                this::skipLoadPackage),
            new NoteBytesRunnablePair(KeyCodeBytes.ESCAPE, this::skipLoadPackage)
        );
        
        setupKeyHandler(keys);
    }
    
    private void setupCompleteInput() {
        removeKeyPressHandler();
        terminal.waitForKeyPress(() -> {
            if (onCompleteCallback != null) {
                onCompleteCallback.run();
            }
        });
    }
    
    private void setupKeyHandler(KeyRunTable keys) {
        removeKeyPressHandler();
        keyPressHandlerId = terminal.addKeyDownHandler(event -> {
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
    
    // ===== ACTIONS =====
    
    private void approveCapabilities() {
        capabilitiesApproved = true;
        transitionTo(Step.CHOOSE_NAMESPACE);
    }
    
    private void denyCapabilities() {
        errorMessage = "Installation cancelled - capabilities denied";
        transitionTo(Step.COMPLETE);
    }
    
    private void selectNamespace(NoteBytesReadOnly namespace) {
        chosenProcessConfig = ProcessConfig.create(namespace);
        transitionTo(Step.PASSWORD_CONFIRM);
    }
    
    private void loadPackageNow() {
        loadImmediately = true;
        statusMessage = "Loading package...";
        invalidate();
        
        nodeCommands.loadNode(packageInfo.getPackageId())
            .thenAccept(instance -> {
                statusMessage = "✓ Node loaded: " + instance.getInstanceId();
                transitionTo(Step.COMPLETE);
            })
            .exceptionally(ex -> {
                statusMessage = "Failed to load: " + ex.getMessage();
                transitionTo(Step.COMPLETE);
                return null;
            });
    }
    
    private void skipLoadPackage() {
        transitionTo(Step.COMPLETE);
    }
    
    private void cancelInstallation() {
        if (onCompleteCallback != null) {
            onCompleteCallback.run();
        }
    }
    
    // ===== UTILITIES =====
    
    private NoteBytesReadOnly getDefaultNamespace(
            PackageManifest.NamespaceRequirement nsReq) {
        return switch (nsReq.mode()) {
            case REQUIRED, DEFAULT -> nsReq.namespace();
            case FLEXIBLE -> packageInfo.getPackageId();
        };
    }
    
    private String[] wrapText(String text, int width) {
        if (text == null || text.isEmpty()) {
            return new String[0];
        }
        
        String[] words = text.split("\\s+");
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        
        for (String word : words) {
            if (current.length() + word.length() + 1 > width) {
                if (current.length() > 0) {
                    lines.add(current.toString());
                    current = new StringBuilder();
                }
            }
            
            if (current.length() > 0) {
                current.append(" ");
            }
            current.append(word);
        }
        
        if (current.length() > 0) {
            lines.add(current.toString());
        }
        
        return lines.toArray(new String[0]);
    }
    
    // ===== CLEANUP =====
    
    private void removeKeyPressHandler() {
        if (keyPressHandlerId != null) {
            terminal.removeKeyDownHandler(keyPressHandlerId);
            keyPressHandlerId = null;
        }
    }
    
    private void cleanup() {
        removeKeyPressHandler();
        
        if (inputReader != null) {
            inputReader.close();
            inputReader = null;
        }
    }
}