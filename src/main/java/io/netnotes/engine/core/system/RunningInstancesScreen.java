package io.netnotes.engine.core.system;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.core.system.control.MenuContext;
import io.netnotes.engine.core.system.control.MenuNavigatorProcess;
import io.netnotes.engine.core.system.control.PasswordReader;
import io.netnotes.engine.core.system.control.nodes.InstalledPackage;
import io.netnotes.engine.core.system.control.nodes.NodeInstance;
import io.netnotes.engine.core.system.control.nodes.NodeState;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.input.InputDevice;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.engine.utils.TimeHelpers;

/**
 * RunningInstancesScreen - View and manage running node instances
 * 
 * Features:
 * - List all running instances with status
 * - View instance details (uptime, crash count, etc.)
 * - Stop running instances (with password confirmation)
 * 
 * Uses RuntimeAccess to interact with system services
 */
class RunningInstancesScreen extends TerminalScreen {
    
    private enum View {
        INSTANCE_LIST,
        INSTANCE_DETAILS,
        CONFIRM_STOP
    }
    
    private View currentView = View.INSTANCE_LIST;
    private NodeInstance selectedInstance;
    private Runnable onBack;
    
    private MenuNavigatorProcess menuNavigator;
    private PasswordReader passwordReader;
    
    public RunningInstancesScreen(
        String name, 
        SystemTerminalContainer terminal, 
        InputDevice keyboard
    ) {
        super(name, terminal, keyboard);
    }
    
    public void setOnBack(Runnable onBack) {
        this.onBack = onBack;
    }
    
    @Override
    public CompletableFuture<Void> onShow() {
        currentView = View.INSTANCE_LIST;
        selectedInstance = null;
        return render();
    }
    
    @Override
    public void onHide() {
        cleanup();
    }
    
    @Override
    public CompletableFuture<Void> render() {
        switch (currentView) {
            case INSTANCE_LIST:
                return renderInstanceList();
            case INSTANCE_DETAILS:
                return renderInstanceDetails();
            case CONFIRM_STOP:
                return renderConfirmStop();
            default:
                return CompletableFuture.completedFuture(null);
        }
    }
    
    // ===== INSTANCE LIST =====
    
    private CompletableFuture<Void> renderInstanceList() {
        return terminal.getSystemAccess().getRunningInstances()
            .thenCompose(instances -> {
                return terminal.clear()
                    .thenCompose(v -> terminal.printTitle("Running Node Instances"))
                    .thenCompose(v -> {
                        if (instances.isEmpty()) {
                            return terminal.printAt(5, 10, "No instances currently running")
                                .thenCompose(x -> terminal.printAt(7, 10, "Press ESC to go back"))
                                .thenRun(() -> waitForKeyPress(keyboard, this::goBack));
                        } else {
                            return renderInstanceTable(instances, 5)
                                .thenCompose(x -> showInstanceListMenu(instances));
                        }
                    });
            })
            .exceptionally(ex -> {
                terminal.printError("Failed to load instances: " + ex.getMessage())
                    .thenCompose(v -> terminal.printAt(7, 10, "Press any key to go back..."))
                    .thenRun(() -> waitForKeyPress(keyboard, this::goBack));
                return null;
            });
    }
    
    private CompletableFuture<Void> renderInstanceTable(List<NodeInstance> instances, int startRow) {
        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        
        // Header
        future = future
            .thenCompose(v -> terminal.printAt(startRow, 10, "Package"))
            .thenCompose(v -> terminal.printAt(startRow, 35, "Process ID"))
            .thenCompose(v -> terminal.printAt(startRow, 55, "State"))
            .thenCompose(v -> terminal.printAt(startRow, 70, "Uptime"))
            .thenCompose(v -> terminal.printAt(startRow + 1, 10, "─".repeat(70)));
        
        int row = startRow + 2;
        int index = 1;
        
        for (NodeInstance instance : instances) {
            InstalledPackage pkg = instance.getPackage();
            String packageName = pkg.getName() + " v" + pkg.getVersion();
            String processId = instance.getProcessId().toString();
            String state = instance.getState().toString();
            String uptime = formatUptime(instance.getUptime());
            
            final int currentRow = row;
            final int currentIndex = index;
            
            future = future
                .thenCompose(v -> terminal.printAt(currentRow, 8, currentIndex + "."))
                .thenCompose(v -> terminal.printAt(currentRow, 10, truncate(packageName, 23)))
                .thenCompose(v -> terminal.printAt(currentRow, 35, truncate(processId, 18)))
                .thenCompose(v -> terminal.printAt(currentRow, 55, state))
                .thenCompose(v -> terminal.printAt(currentRow, 70, uptime));
            
            row++;
            index++;
        }
        
        return future;
    }
    
    private CompletableFuture<Void> showInstanceListMenu(List<NodeInstance> instances) {
        ContextPath menuPath = terminal.getSessionPath().append("menu", "instances");
        MenuContext menu = new MenuContext(menuPath, "Instance Actions");
        
        // Add menu item for each instance
        int index = 1;
        for (NodeInstance instance : instances) {
            final NodeInstance inst = instance;
            String itemId = "instance_" + index;
            String description = instance.getPackage().getName();
            String help = "View details and manage this instance";
            
            menu.addItem(itemId, description, help, () -> showInstanceDetails(inst));
            index++;
        }
        
        menu.addSeparator("Instance Navigation");
        menu.addItem("refresh", "Refresh List", "Reload instance list", this::onShow);
        menu.addItem("back", "Back to Node Manager", this::goBack);
        
        menuNavigator = new MenuNavigatorProcess("instance-list-menu", terminal, keyboard);
        
        return terminal.spawnPasswordProcess(menuNavigator)
            .thenRun(() -> menuNavigator.showMenu(menu));
    }
    
    // ===== INSTANCE DETAILS =====
    
    private void showInstanceDetails(NodeInstance instance) {
        cleanupMenuNavigator();
        selectedInstance = instance;
        currentView = View.INSTANCE_DETAILS;
        render();
    }
    
    private CompletableFuture<Void> renderInstanceDetails() {
        if (selectedInstance == null) {
            currentView = View.INSTANCE_LIST;
            return render();
        }
        
        InstalledPackage pkg = selectedInstance.getPackage();
        
        return terminal.clear()
            .thenCompose(v -> terminal.printTitle("Instance Details"))
            .thenCompose(v -> terminal.printAt(5, 10, "Package: " + pkg.getName()))
            .thenCompose(v -> terminal.printAt(6, 10, "Version: " + pkg.getVersion()))
            .thenCompose(v -> terminal.printAt(7, 10, "Description: " + pkg.getDescription()))
            .thenCompose(v -> terminal.printAt(9, 10, "Instance ID: " + selectedInstance.getInstanceId()))
            .thenCompose(v -> terminal.printAt(10, 10, "Process ID: " + selectedInstance.getProcessId()))
            .thenCompose(v -> terminal.printAt(11, 10, "State: " + selectedInstance.getState()))
            .thenCompose(v -> terminal.printAt(12, 10, "Uptime: " + formatUptime(selectedInstance.getUptime())))
            .thenCompose(v -> terminal.printAt(13, 10, "Crash Count: " + selectedInstance.getCrashCount()))
            .thenCompose(v -> terminal.printAt(15, 10, "Data Path: " + selectedInstance.getDataRootPath()))
            .thenCompose(v -> terminal.printAt(16, 10, "Flow Path: " + selectedInstance.getFlowBasePath()))
            .thenCompose(v -> terminal.printAt(18, 10, "Choose an action:"))
            .thenRun(this::showInstanceDetailsMenu);
    }
    
    private void showInstanceDetailsMenu() {
        ContextPath menuPath = terminal.getSessionPath().append("menu", "instance-details");
        MenuContext menu = new MenuContext(menuPath, "Instance Actions");
        
        // Only allow stopping if instance is running
        if (selectedInstance.getState() == NodeState.RUNNING) {
            menu.addItem("stop", "Stop Instance", 
                "Stop this running instance (requires password)",
                this::confirmStopInstance);
        }
        
        menu.addItem("refresh", "Refresh Details", 
            "Reload instance information",
            () -> render());
        
        menu.addSeparator("Navigation");
        menu.addItem("back", "Back to Instance List", () -> {
            cleanupMenuNavigator();
            selectedInstance = null;
            currentView = View.INSTANCE_LIST;
            render();
        });
        
        menuNavigator = new MenuNavigatorProcess("instance-details-menu", terminal, keyboard);
        
        terminal.spawnPasswordProcess(menuNavigator)
            .thenRun(() -> menuNavigator.showMenu(menu));
    }
    
    // ===== CONFIRM STOP =====
    
    private void confirmStopInstance() {
        cleanupMenuNavigator();
        currentView = View.CONFIRM_STOP;
        render();
    }
    
    private CompletableFuture<Void> renderConfirmStop() {
        if (selectedInstance == null) {
            currentView = View.INSTANCE_LIST;
            return render();
        }
        
        return terminal.clear()
            .thenCompose(v -> terminal.printTitle("Confirm Stop Instance"))
            .thenCompose(v -> terminal.printAt(5, 10, "⚠ WARNING ⚠"))
            .thenCompose(v -> terminal.printAt(7, 10, "Stop instance: " + selectedInstance.getPackage().getName()))
            .thenCompose(v -> terminal.printAt(8, 10, "Process ID: " + selectedInstance.getProcessId()))
            .thenCompose(v -> terminal.printAt(10, 10, "This will:"))
            .thenCompose(v -> terminal.printAt(11, 12, "• Gracefully shutdown the node"))
            .thenCompose(v -> terminal.printAt(12, 12, "• Close all connections"))
            .thenCompose(v -> terminal.printAt(13, 12, "• Unload from runtime"))
            .thenCompose(v -> terminal.printAt(15, 10, "Enter password to confirm:"))
            .thenCompose(v -> terminal.moveCursor(15, 36))
            .thenRun(this::startPasswordConfirmation);
    }
    
    private void startPasswordConfirmation() {
        passwordReader = new PasswordReader();
        keyboard.setEventConsumer(passwordReader.getEventConsumer());
        
        passwordReader.setOnPassword(password -> {
            keyboard.setEventConsumer(null);
            passwordReader.close();
            passwordReader = null;
            
            verifyPasswordAndStop(password);
        });
    }
    
    private void verifyPasswordAndStop(NoteBytesEphemeral password) {
        terminal.printAt(17, 10, "Verifying password...")
            .thenCompose(v -> terminal.getSystemAccess().verifyPassword(password))
            .thenCompose(valid -> {
                password.close();
                
                if (!valid) {
                    return terminal.printError("Invalid password")
                        .thenCompose(x -> terminal.printAt(19, 10, "Press any key to try again..."))
                        .thenRun(() -> waitForKeyPress(keyboard, () -> {
                            currentView = View.CONFIRM_STOP;
                            render();
                        }));
                } else {
                    return performStop();
                }
            })
            .exceptionally(ex -> {
                password.close();
                terminal.printError("Error: " + ex.getMessage())
                    .thenCompose(x -> terminal.printAt(19, 10, "Press any key..."))
                    .thenRun(() -> waitForKeyPress(keyboard, () -> {
                        selectedInstance = null;
                        currentView = View.INSTANCE_LIST;
                        render();
                    }));
                return null;
            });
    }
    
    private CompletableFuture<Void> performStop() {
        return terminal.printAt(17, 10, "Stopping instance...                    ")
            .thenCompose(v -> terminal.getSystemAccess().unloadNode(selectedInstance.getInstanceId()))
            .thenCompose(v -> terminal.printSuccess("Instance stopped successfully"))
            .thenCompose(v -> terminal.printAt(19, 10, "Returning to instance list..."))
            .thenRunAsync(() -> TimeHelpers.timeDelay(2))
            .thenRun(() -> {
                selectedInstance = null;
                currentView = View.INSTANCE_LIST;
                render();
            })
            .exceptionally(ex -> {
                terminal.printError("Failed to stop: " + ex.getMessage())
                    .thenCompose(x -> terminal.printAt(21, 10, "Press any key..."))
                    .thenRun(() -> waitForKeyPress(keyboard, () -> {
                        selectedInstance = null;
                        currentView = View.INSTANCE_LIST;
                        render();
                    }));
                return null;
            });
    }
    
    // ===== UTILITY =====
    
    private void goBack() {
        cleanup();
        if (onBack != null) {
            onBack.run();
        }
    }
    
    private void cleanupMenuNavigator() {
        if (menuNavigator != null) {
            terminal.getRegistry().unregisterProcess(menuNavigator.getContextPath());
            menuNavigator = null;
        }
    }
    
    private void cleanup() {
        cleanupMenuNavigator();
        
        if (passwordReader != null) {
            keyboard.setEventConsumer(null);
            passwordReader.close();
            passwordReader = null;
        }
        
        keyboard.setEventConsumer(null);
    }
    
    private String formatUptime(long uptimeMs) {
        long seconds = uptimeMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        
        if (days > 0) {
            return String.format("%dd %dh", days, hours % 24);
        } else if (hours > 0) {
            return String.format("%dh %dm", hours, minutes % 60);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds % 60);
        } else {
            return String.format("%ds", seconds);
        }
    }
    
    private String truncate(String str, int maxLength) {
        if (str == null) return "";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength - 3) + "...";
    }
}