package io.netnotes.engine.core.system;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.core.system.control.MenuContext;
import io.netnotes.engine.core.system.control.PasswordReader;
import io.netnotes.engine.core.system.control.nodes.InstallationRequest;
import io.netnotes.engine.core.system.control.nodes.PackageInfo;
import io.netnotes.engine.core.system.control.nodes.PackageManifest;
import io.netnotes.engine.core.system.control.nodes.ProcessConfig;
import io.netnotes.engine.core.system.control.nodes.security.PathCapability;
import io.netnotes.engine.core.system.control.nodes.security.PolicyManifest;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.input.InputDevice;
import io.netnotes.engine.io.input.KeyRunTable;
import io.netnotes.engine.io.input.Keyboard.KeyCodeBytes;
import io.netnotes.engine.io.input.ephemeralEvents.EphemeralKeyDownEvent;
import io.netnotes.engine.io.input.ephemeralEvents.EphemeralRoutedEvent;
import io.netnotes.engine.io.input.events.KeyDownEvent;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesRunnablePair;

/**
 * PackageInstallScreen - Menu-driven package installation flow
 * 
 * REFACTORED: Uses MenuContext for navigation instead of number selection
 * - Clean menu-based interface
 * - KeyRunTable for confirmations
 * - TerminalInputReader for text input
 * - PasswordReader for secure input
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
    private final ContextPath basePath;
    
    private Step currentStep = Step.PACKAGE_OVERVIEW;
    private ProcessConfig chosenProcessConfig;
    private boolean capabilitiesApproved = false;
    private boolean loadImmediately = false;
    
    private MenuContext currentMenu;
    private PasswordReader passwordReader;
    private Runnable onCompleteCallback;
    
    public PackageInstallScreen(
            String name, 
            SystemTerminalContainer terminal, 
            InputDevice keyboard,
            PackageInfo packageInfo) {
        super(name, terminal, keyboard);
        this.packageInfo = packageInfo;
        this.basePath = ContextPath.parse("install/" + packageInfo.getName());
    }
    
    public void setOnComplete(Runnable callback) {
        this.onCompleteCallback = callback;
    }
    
    @Override
    public CompletableFuture<Void> onShow() {
        currentStep = Step.PACKAGE_OVERVIEW;
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
                    case PACKAGE_OVERVIEW:
                        return renderPackageOverview();
                    case REVIEW_CAPABILITIES:
                        return renderCapabilityReview();
                    case CHOOSE_NAMESPACE:
                        return renderNamespaceChoice();
                    case PASSWORD_CONFIRM:
                        return renderPasswordConfirm();
                    case INSTALLING:
                        return renderInstalling();
                    case ASK_LOAD_IMMEDIATELY:
                        return renderAskLoadImmediately();
                    case COMPLETE:
                        return renderComplete();
                    default:
                        return CompletableFuture.completedFuture(null);
                }
            });
    }
    
    // ===== STEP 1: PACKAGE OVERVIEW =====
    
    private CompletableFuture<Void> renderPackageOverview() {
        return terminal.printTitle("Install Package")
            .thenCompose(v -> terminal.printAt(5, 10, "Package: " + packageInfo.getName()))
            .thenCompose(v -> terminal.printAt(6, 10, "Version: " + packageInfo.getVersion()))
            .thenCompose(v -> terminal.printAt(7, 10, "Category: " + packageInfo.getCategory()))
            .thenCompose(v -> terminal.printAt(8, 10, 
                String.format("Size: %.2f MB", packageInfo.getSize() / (1024.0 * 1024.0))))
            .thenCompose(v -> terminal.printAt(9, 10, "Repository: " + packageInfo.getRepository()))
            .thenCompose(v -> terminal.printAt(11, 10, "Description:"))
            .thenCompose(v -> {
                String desc = packageInfo.getDescription();
                String[] lines = wrapText(desc, 60);
                
                CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
                for (int i = 0; i < Math.min(lines.length, 5); i++) {
                    final int row = 12 + i;
                    final String line = lines[i];
                    future = future.thenCompose(v2 -> terminal.printAt(row, 10, line));
                }
                return future;
            })
            .thenCompose(v -> showOverviewMenu());
    }
    
    private CompletableFuture<Void> showOverviewMenu() {
        currentMenu = new MenuContext(basePath.append("overview"), "Continue Installation?")
            .addItem("continue", "Continue with installation", this::proceedToCapabilities)
            .addItem("cancel", "Cancel installation", this::cancelInstallation);
        
        return renderMenu(currentMenu, 19);
    }
    
    private void proceedToCapabilities() {
        keyboard.setEventConsumer(null);
        currentStep = Step.REVIEW_CAPABILITIES;
        render();
    }
    
    private void cancelInstallation() {
        keyboard.setEventConsumer(null);
        if (onCompleteCallback != null) {
            onCompleteCallback.run();
        }
    }
    
    // ===== STEP 2: REVIEW CAPABILITIES =====
    
    private CompletableFuture<Void> renderCapabilityReview() {
        PolicyManifest policy = PolicyManifest.fromNoteBytes(
            packageInfo.getManifest().getMetadata()
        );
        
        List<PathCapability> capabilities = policy.getRequestedCapabilities();
        
        return terminal.printTitle("Security Review")
            .thenCompose(v -> terminal.printAt(5, 10, 
                "This package requests the following capabilities:"))
            .thenCompose(v -> terminal.printAt(6, 10, 
                "Review carefully before approving."))
            .thenCompose(v -> {
                CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
                
                if (capabilities.isEmpty()) {
                    return future.thenCompose(v2 -> terminal.printAt(8, 10, 
                        "No special capabilities requested"));
                }
                
                int row = 8;
                for (int i = 0; i < capabilities.size(); i++) {
                    PathCapability cap = capabilities.get(i);
                    final int currentRow = row + (i * 2);
                    
                    future = future
                        .thenCompose(v2 -> terminal.printAt(currentRow, 10, 
                            String.format("• %s", cap.getDescription())))
                        .thenCompose(v2 -> terminal.printAt(currentRow + 1, 12, 
                            "Reason: " + cap.getReason()));
                }
                
                return future;
            })
            .thenCompose(v -> {
                if (policy.hasSensitivePaths()) {
                    return terminal.printAt(16, 10, 
                        "⚠️  WARNING: Sensitive system paths requested");
                }
                return CompletableFuture.completedFuture(null);
            })
            .thenCompose(v -> showCapabilityMenu(policy.hasSensitivePaths()));
    }
    
    private CompletableFuture<Void> showCapabilityMenu(boolean hasSensitivePaths) {
        String approveDesc = hasSensitivePaths 
            ? "Approve (WARNING: Sensitive access)" 
            : "Approve capabilities";
        
        currentMenu = new MenuContext(basePath.append("capabilities"), "Security Decision")
            .addItem("approve", approveDesc, this::approveCapabilities)
            .addItem("deny", "Deny and cancel installation", this::denyCapabilities);
        
        return renderMenu(currentMenu, 18);
    }
    
    private void approveCapabilities() {
        keyboard.setEventConsumer(null);
        capabilitiesApproved = true;
        currentStep = Step.CHOOSE_NAMESPACE;
        render();
    }
    
    private void denyCapabilities() {
        keyboard.setEventConsumer(null);
        terminal.clear()
            .thenCompose(v -> terminal.printError("Installation cancelled - capabilities denied"))
            .thenCompose(v -> terminal.printAt(10, 10, "Press any key to return..."))
            .thenRun(() -> waitForAnyKey(() -> {
                if (onCompleteCallback != null) {
                    onCompleteCallback.run();
                }
            }));
    }
    
    // ===== STEP 3: CHOOSE NAMESPACE =====
    
    private CompletableFuture<Void> renderNamespaceChoice() {
        PackageManifest manifest = packageInfo.getManifest();
        PackageManifest.NamespaceRequirement nsReq = manifest.getNamespaceRequirement();
        
        // Case 1: REQUIRED namespace - no choice
        if (nsReq.mode() == PackageManifest.NamespaceMode.REQUIRED) {
            chosenProcessConfig = ProcessConfig.create(nsReq.namespace());
            currentStep = Step.PASSWORD_CONFIRM;
            return render();
        }
        
        // Case 2 & 3: Show options
        return terminal.printTitle("Choose Installation Namespace")
            .thenCompose(v -> {
                if (nsReq.mode() == PackageManifest.NamespaceMode.DEFAULT) {
                    return terminal.printAt(5, 10, 
                        "Suggested namespace: " + nsReq.namespace());
                } else {
                    return terminal.printAt(5, 10, 
                        "Choose where this package will be installed:");
                }
            })
            .thenCompose(v -> showNamespaceMenu(nsReq));
    }
    
    private CompletableFuture<Void> showNamespaceMenu(PackageManifest.NamespaceRequirement nsReq) {
        NoteBytesReadOnly defaultNs = getDefaultNamespace(nsReq);
        
        currentMenu = new MenuContext(basePath.append("namespace"), "Installation Location")
            .addItem("default", "Default: " + defaultNs + " (Standard installation)", 
                () -> selectNamespace(defaultNs))
            .addItem("cancel", "Cancel installation", this::cancelInstallation);
        
        return renderMenu(currentMenu, 7);
    }
    
    private void selectNamespace(NoteBytesReadOnly namespace) {
        keyboard.setEventConsumer(null);
        chosenProcessConfig = ProcessConfig.create(namespace);
        currentStep = Step.PASSWORD_CONFIRM;
        render();
    }
    
    private NoteBytesReadOnly getDefaultNamespace(PackageManifest.NamespaceRequirement nsReq) {
        switch (nsReq.mode()) {
            case REQUIRED:
            case DEFAULT:
                return nsReq.namespace();
            case FLEXIBLE:
            default:
                return packageInfo.getPackageId();
        }
    }
    
    // ===== STEP 4: PASSWORD CONFIRM =====
    
    private CompletableFuture<Void> renderPasswordConfirm() {
        return terminal.printTitle("Confirm Installation")
            .thenCompose(v -> terminal.printAt(5, 10, "Ready to install:"))
            .thenCompose(v -> terminal.printAt(7, 10, "Package: " + packageInfo.getName()))
            .thenCompose(v -> terminal.printAt(8, 10, 
                "Namespace: " + chosenProcessConfig.getProcessId()))
            .thenCompose(v -> terminal.printAt(10, 10, "Enter password to confirm:"))
            .thenCompose(v -> terminal.moveCursor(10, 40))
            .thenRun(this::startPasswordEntry);
    }
    
    private void startPasswordEntry() {
        passwordReader = new PasswordReader();
        keyboard.setEventConsumer(passwordReader.getEventConsumer());
        
        passwordReader.setOnPassword(password -> {
            keyboard.setEventConsumer(null);
            passwordReader.close();
            passwordReader = null;
            
            RuntimeAccess access = terminal.getSystemAccess();
            access.verifyPassword(password)
                .thenAccept(valid -> {
                    if (valid) {
                        performInstallation(password);
                    } else {
                        password.close();
                        terminal.clear()
                            .thenCompose(v -> terminal.printError("Invalid password"))
                            .thenCompose(v -> terminal.printAt(10, 10, "Press any key to retry..."))
                            .thenRun(() -> waitForAnyKey(() -> render()));
                    }
                })
                .exceptionally(ex -> {
                    password.close();
                    terminal.printError("Verification failed: " + ex.getMessage())
                        .thenCompose(v -> terminal.printAt(10, 10, "Press any key to return..."))
                        .thenRun(() -> waitForAnyKey(() -> {
                            if (onCompleteCallback != null) {
                                onCompleteCallback.run();
                            }
                        }));
                    return null;
                });
        });
    }
    
    // ===== STEP 5: INSTALLING =====
    
    private CompletableFuture<Void> renderInstalling() {
        return terminal.printTitle("Installing Package")
            .thenCompose(v -> terminal.printAt(5, 10, "Installing..."))
            .thenCompose(v -> terminal.printAt(7, 10, "This may take a moment."));
    }
    
    private void performInstallation(NoteBytesEphemeral password) {
        currentStep = Step.INSTALLING;
        render();
        
        RuntimeAccess access = terminal.getSystemAccess();
        
        PolicyManifest policyManifest = PolicyManifest.fromNoteBytes(
            packageInfo.getManifest().getMetadata()
        );
        
        InstallationRequest request = new InstallationRequest(
            packageInfo,
            chosenProcessConfig,
            policyManifest,
            password,
            false,
            false,
            null
        );
        
        access.installPackage(request)
            .thenAccept(installedPackage -> {
                password.close();
                currentStep = Step.ASK_LOAD_IMMEDIATELY;
                render();
            })
            .exceptionally(ex -> {
                password.close();
                terminal.clear()
                    .thenCompose(v -> terminal.printError(
                        "✗ Installation failed\n\n" +
                        "Error: " + ex.getMessage()))
                    .thenCompose(v -> terminal.printAt(10, 10, "Press any key to return..."))
                    .thenRun(() -> waitForAnyKey(() -> {
                        if (onCompleteCallback != null) {
                            onCompleteCallback.run();
                        }
                    }));
                return null;
            });
    }
    
    // ===== STEP 6: ASK LOAD IMMEDIATELY =====
    
    private CompletableFuture<Void> renderAskLoadImmediately() {
        return terminal.printTitle("Installation Complete")
            .thenCompose(v -> terminal.printSuccess("✓ Package installed successfully!"))
            .thenCompose(v -> terminal.printAt(7, 10, "Package: " + packageInfo.getName()))
            .thenCompose(v -> terminal.printAt(8, 10, 
                "Namespace: " + chosenProcessConfig.getProcessId()))
            .thenCompose(v -> showLoadImmediatelyMenu());
    }
    
    private CompletableFuture<Void> showLoadImmediatelyMenu() {
        currentMenu = new MenuContext(basePath.append("load"), "Load Package Now?")
            .addItem("load", "Yes, load the package now", this::loadPackageNow)
            .addItem("skip", "No, I'll load it later", this::skipLoadPackage);
        
        return renderMenu(currentMenu, 10);
    }
    
    private void loadPackageNow() {
        keyboard.setEventConsumer(null);
        loadImmediately = true;
        // TODO: Load the node
        terminal.printAt(12, 10, "Loading node not yet implemented...")
            .thenRun(() -> {
                currentStep = Step.COMPLETE;
                render();
            });
    }
    
    private void skipLoadPackage() {
        keyboard.setEventConsumer(null);
        currentStep = Step.COMPLETE;
        render();
    }
    
    // ===== STEP 7: COMPLETE =====
    
    private CompletableFuture<Void> renderComplete() {
        return terminal.printTitle("Installation Complete")
            .thenCompose(v -> terminal.printSuccess("✓ Installation successful"))
            .thenCompose(v -> terminal.printAt(7, 10, "Press any key to return..."))
            .thenRun(() -> waitForAnyKey(() -> {
                if (onCompleteCallback != null) {
                    onCompleteCallback.run();
                }
            }));
    }
    
    // ===== MENU RENDERING =====
    
    /**
     * Render a menu at the specified row
     */
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
            .thenCompose(v -> terminal.printAt(selectRow, 10, "Use arrow keys and Enter to select"))
            .thenRun(() -> startMenuNavigation(menu));
    }
    
    /**
     * Start menu navigation with arrow keys
     */
    private void startMenuNavigation(MenuContext menu) {
        List<MenuContext.MenuItem> items = new java.util.ArrayList<>(menu.getItems());
        final int[] selectedIndex = {0};
        
        KeyRunTable navigationKeys = new KeyRunTable(
            new NoteBytesRunnablePair(KeyCodeBytes.UP, () -> {
                selectedIndex[0] = (selectedIndex[0] - 1 + items.size()) % items.size();
                highlightSelection(menu, selectedIndex[0]);
            }),
            new NoteBytesRunnablePair(KeyCodeBytes.DOWN, () -> {
                selectedIndex[0] = (selectedIndex[0] + 1) % items.size();
                highlightSelection(menu, selectedIndex[0]);
            }),
            new NoteBytesRunnablePair(KeyCodeBytes.ENTER, () -> {
                keyboard.setEventConsumer(null);
                MenuContext.MenuItem selected = items.get(selectedIndex[0]);
                menu.navigate(selected.name);
            }),
            new NoteBytesRunnablePair(KeyCodeBytes.ESCAPE, () -> {
                keyboard.setEventConsumer(null);
                cancelInstallation();
            })
        );
        
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
        
        // Initial highlight
        highlightSelection(menu, 0);
    }
    
    private void highlightSelection(MenuContext menu, int selectedIndex) {
        // TODO: Implement visual highlighting
        // For now, just log or update display
    }
    
    // ===== UTILITIES =====
    
    /**
     * Wait for any key press using KeyRunTable pattern
     */
    private void waitForAnyKey(Runnable callback) {
        keyboard.setEventConsumer(event -> {
            keyboard.setEventConsumer(null);
            callback.run();
        });
    }
    
    private String[] wrapText(String text, int width) {
        if (text == null || text.isEmpty()) {
            return new String[0];
        }
        
        String[] words = text.split("\\s+");
        List<String> lines = new java.util.ArrayList<>();
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
    
    private void cleanup() {
        keyboard.setEventConsumer(null);
        
        if (passwordReader != null) {
            passwordReader.close();
            passwordReader = null;
        }
    }
}